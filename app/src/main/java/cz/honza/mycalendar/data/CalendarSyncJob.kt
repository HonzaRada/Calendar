package cz.honza.mycalendar.data

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal class CalendarSyncJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = mutableMapOf<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        jobs[params.jobId] = scope.launch(Dispatchers.Main) {
            val success = CalendarRepository.get(applicationContext).sync()
            jobs.remove(params.jobId)
            jobFinished(params, !success && params.jobId == CHANGES)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        jobs.remove(params.jobId)?.cancel()
        return params.jobId == CHANGES
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val PERIODIC = 1001
        private const val CHANGES = 1002
        private const val INTERVAL = 15 * 60 * 1000L

        fun configure(context: Context, connected: Boolean) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (!connected) {
                scheduler.cancel(PERIODIC)
                scheduler.cancel(CHANGES)
                return
            }
            if (scheduler.getPendingJob(PERIODIC) == null) {
                scheduler.schedule(builder(context, PERIODIC).setPeriodic(INTERVAL).build())
            }
            enqueueChangeIfConnected(context, true)
        }

        fun enqueueChangeIfConnected(context: Context, connected: Boolean) {
            if (!connected) return
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (scheduler.getPendingJob(CHANGES) == null) {
                scheduler.schedule(builder(context, CHANGES).setMinimumLatency(4_000).build())
            }
        }

        private fun builder(context: Context, id: Int): JobInfo.Builder =
            JobInfo.Builder(id, ComponentName(context, CalendarSyncJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
    }
}
