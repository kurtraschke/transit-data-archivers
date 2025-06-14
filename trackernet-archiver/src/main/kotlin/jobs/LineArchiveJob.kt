package systems.choochoo.transit_data_archivers.trackernet.jobs

import com.clickhouse.client.api.Client
import com.clickhouse.data.ClickHouseFormat.JSONEachRow
import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.datatype.guava.GuavaModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.google.common.base.Stopwatch
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.inject.Inject
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import org.quartz.DisallowConcurrentExecution
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import systems.choochoo.transit_data_archivers.trackernet.model.PredictionDetailFetchResult
import systems.choochoo.transit_data_archivers.trackernet.model.PredictionSummaryFetchResult
import systems.choochoo.transit_data_archivers.trackernet.services.TrackernetService
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.time.temporal.ChronoUnit.SECONDS
import java.util.concurrent.CompletableFuture


private val logger = KotlinLogging.logger {}

private val om = jsonMapper {
    addModules(
        kotlinModule(),
        JakartaXmlBindAnnotationModule(),
        JavaTimeModule(),
        GuavaModule()
    )
}

@DisallowConcurrentExecution
internal class LineArchiveJob : Job {
    @Inject
    lateinit var service: TrackernetService

    @Inject
    lateinit var client: Client

    lateinit var lineCode: String
    lateinit var excludedStations: List<String>

    val w: ObjectWriter = om.writer()

    override fun execute(context: JobExecutionContext) {
        val fetchTime = context.fireTime.toInstant().truncatedTo(SECONDS).toKotlinInstant()

        logger.trace { "Beginning fetch for $lineCode" }
        val st = Stopwatch.createStarted()

        val summaryCall = service.getPredictionSummary(lineCode)

        val predictionSummary = try {
            val summaryResponse = summaryCall.execute()

            if (summaryResponse.isSuccessful) {
                summaryResponse.body()!!
            } else {
                throw JobExecutionException("HTTP error ${summaryResponse.code()} ${summaryResponse.message()} while fetching summary for line $lineCode")
            }
        } catch (ex: IOException) {
            throw JobExecutionException("Other IO error while fetching summary for line $lineCode", ex)
        }

        val psfr = PredictionSummaryFetchResult(
            fetchTime.toJavaInstant(),
            lineCode,
            predictionSummary
        )

        val stationsToFetch = predictionSummary
            .stations
            .filter { station -> station.platforms.any { platform -> platform.trains.isNotEmpty() } }
            .map { station -> station.code }
            .filter { stationCode -> !excludedStations.contains(stationCode) }

        val predictionDetails = stationsToFetch
            .mapNotNull { stationCode ->
                val detailCall = service.getPredictionDetail(lineCode, stationCode)

                try {
                    val detailResponse = detailCall.execute()

                    if (detailResponse.isSuccessful) {
                        val pd = detailResponse.body()!!

                        if (lineCode == pd.line && stationCode == pd.station.stationCode) {
                            Pair(stationCode, pd)
                        } else {
                            logger.error {
                                "Trackernet PredictionDetailed API returned line and station code" +
                                        " (${pd.line}, ${pd.station.stationCode})" +
                                        " when ($lineCode, $stationCode) was requested"
                            }
                            null
                        }
                    } else {
                        logger.error { "HTTP error ${detailResponse.code()} ${detailResponse.message()} while fetching details for station $stationCode on line $lineCode" }
                        null
                    }
                } catch (ex: IOException) {
                    logger.error(ex) { "Other IO error while fetching details for station $stationCode on line $lineCode" }
                    null
                }
            }
            .map { (stationCode, predictionDetail) ->
                PredictionDetailFetchResult(
                    fetchTime.toJavaInstant(),
                    lineCode,
                    stationCode,
                    predictionDetail
                )
            }

        st.stop()
        logger.trace { "Fetch complete for $lineCode; took $st" }

        run {
            val os = ByteArrayOutputStream()

            os.use {
                w.writeValue(it, psfr)
            }

            val ins = ByteArrayInputStream(os.toByteArray())

            val response =
                ins.use {
                    client.insert(
                        "prediction_summary",
                        it,
                        JSONEachRow
                    ).get()
                }

            logger.trace { "Inserted ${response.writtenRows} summary rows" }
        }

        run {
            val ins = PipedInputStream()
            val os = PipedOutputStream(ins)

            val p = CompletableFuture.runAsync {
                os.use {
                    val sw = w.withRootValueSeparator("\n").writeValues(it)
                    sw.use {
                        predictionDetails.forEach { predictionDetail -> it.write(predictionDetail) }
                    }
                }
            }

            val f = ins.use {
                client.insert(
                    "prediction_details",
                    it,
                    JSONEachRow
                )
            }

            CompletableFuture.allOf(p, f).join()

            logger.trace { "Inserted ${f.get().writtenRows} detail rows" }
        }

    }
}