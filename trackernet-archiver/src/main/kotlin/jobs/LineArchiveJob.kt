@file:OptIn(ExperimentalTime::class)

package systems.choochoo.transit_data_archivers.trackernet.jobs

import com.clickhouse.client.api.ClickHouseException
import com.clickhouse.client.api.Client
import com.clickhouse.client.api.insert.InsertSettings
import com.clickhouse.data.ClickHouseFormat.JSONEachRow
import com.fasterxml.jackson.datatype.guava.GuavaModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.google.common.base.Stopwatch
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.inject.Inject
import org.quartz.DisallowConcurrentExecution
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import systems.choochoo.transit_data_archivers.common.utils.FallbackWriter
import systems.choochoo.transit_data_archivers.trackernet.model.PredictionDetailFetchResult
import systems.choochoo.transit_data_archivers.trackernet.model.PredictionSummaryFetchResult
import systems.choochoo.transit_data_archivers.trackernet.services.TrackernetService
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant


private val log = KotlinLogging.logger {}

private val om = jsonMapper {
    addModules(
        kotlinModule(),
        JakartaXmlBindAnnotationModule(),
        JavaTimeModule(),
        GuavaModule()
    )
}

private val settings = InsertSettings().serverSetting("materialized_views_ignore_errors", "1")

@DisallowConcurrentExecution
internal class LineArchiveJob : Job {
    @Inject
    lateinit var service: TrackernetService

    @Inject
    lateinit var client: Client

    @Inject
    lateinit var fallbackWriter: FallbackWriter

    lateinit var lineCode: String
    lateinit var excludedStations: List<String>

    private val w = om.writer()

    override fun execute(context: JobExecutionContext) {
        val fetchTime = context.fireTime.toInstant().toKotlinInstant()

        log.trace { "Beginning fetch for $lineCode" }
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
            fetchTime,
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
                            log.error {
                                "Trackernet PredictionDetailed API returned line and station code" +
                                        " (${pd.line}, ${pd.station.stationCode})" +
                                        " when ($lineCode, $stationCode) was requested"
                            }
                            null
                        }
                    } else {
                        log.error { "HTTP error ${detailResponse.code()} ${detailResponse.message()} while fetching details for station $stationCode on line $lineCode" }
                        null
                    }
                } catch (ex: IOException) {
                    log.error(ex) { "Other IO error while fetching details for station $stationCode on line $lineCode" }
                    null
                }
            }
            .map { (stationCode, predictionDetail) ->
                PredictionDetailFetchResult(
                    fetchTime,
                    lineCode,
                    stationCode,
                    predictionDetail
                )
            }

        st.stop()
        log.trace { "Fetch complete for $lineCode; took $st" }

        val summaryBytes = w.writeValueAsBytes(psfr)

        try {
            val response = ByteArrayInputStream(summaryBytes).use {
                client.insert(
                    "prediction_summary",
                    it,
                    JSONEachRow,
                    settings
                ).get()
            }

            log.trace { "Inserted ${response.writtenRows} summary rows" }
        } catch (e: ClickHouseException) {
            log.warn(e) { "Exception while persisting to database; will attempt fallback write" }

            fallbackWriter.write(
                linkedMapOf(
                    "observation_type" to "summary",
                    "line_code" to lineCode
                ),
                fetchTime,
                summaryBytes
            )
        }

        val detailsBytes = ByteArrayOutputStream()
            .apply {
                this.use { os ->
                    w.withRootValueSeparator("\n")
                        .writeValues(os).use {
                            predictionDetails.forEach { predictionDetail -> it.write(predictionDetail) }
                        }
                }
            }
            .toByteArray()

        try {
            val response = ByteArrayInputStream(detailsBytes).use {
                client.insert(
                    "prediction_details",
                    it,
                    JSONEachRow,
                    settings
                )
            }.get()

            log.trace { "Inserted ${response.writtenRows} detail rows" }
        } catch (e: ClickHouseException) {
            log.warn(e) { "Exception while persisting to database; will attempt fallback write" }

            fallbackWriter.write(
                linkedMapOf(
                    "observation_type" to "details",
                    "line_code" to lineCode
                ),
                fetchTime,
                detailsBytes
            )
        }
    }
}