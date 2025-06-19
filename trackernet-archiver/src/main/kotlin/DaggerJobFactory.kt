package systems.choochoo.transit_data_archivers.trackernet

import dagger.MembersInjector
import jakarta.inject.Inject
import org.quartz.Job
import org.quartz.Scheduler
import org.quartz.simpl.PropertySettingJobFactory
import org.quartz.spi.TriggerFiredBundle
import systems.choochoo.transit_data_archivers.trackernet.jobs.LineArchiveJob

internal class DaggerJobFactory @Inject constructor(var jobInjector: MembersInjector<LineArchiveJob>) :
    PropertySettingJobFactory() {

    init {
        isThrowIfPropertyNotFound = true
    }

    override fun newJob(bundle: TriggerFiredBundle, scheduler: Scheduler): Job {
        val job = super.newJob(bundle, scheduler)

        if (job is LineArchiveJob) {
            jobInjector.injectMembers(job)
        }

        return job
    }
}