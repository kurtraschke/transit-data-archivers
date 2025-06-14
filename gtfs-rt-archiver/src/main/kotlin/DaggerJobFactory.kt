package systems.choochoo.transit_data_archivers.gtfsrt

import dagger.MembersInjector
import jakarta.inject.Inject
import org.quartz.Job
import org.quartz.Scheduler
import org.quartz.simpl.PropertySettingJobFactory
import org.quartz.spi.TriggerFiredBundle
import systems.choochoo.transit_data_archivers.gtfsrt.jobs.FeedArchiveJob


internal class DaggerJobFactory @Inject constructor(private val jobInjector: MembersInjector<FeedArchiveJob>) :
    PropertySettingJobFactory() {

    init {
        isThrowIfPropertyNotFound = true
    }


    override fun newJob(bundle: TriggerFiredBundle, scheduler: Scheduler): Job {
        val job = super.newJob(bundle, scheduler)

        if (job is FeedArchiveJob) {
            jobInjector.injectMembers(job)
        }

        return job
    }
}
