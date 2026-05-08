package com.guima.esa.cluster

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.guima.esa.data.UserRepository
import java.util.concurrent.TimeUnit

object ClusterWorkScheduler {
    private const val UNIQUE_WORK_NAME = "cluster-worker-loop"

    fun sync(context: Context, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled || !UserRepository.hasAcceptedPrivacy()) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }
        enqueueNext(context, delaySeconds = 5)
    }

    fun enqueueNext(context: Context, delaySeconds: Long) {
        if (!UserRepository.isClusterEnabled() || !UserRepository.hasAcceptedPrivacy()) {
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = OneTimeWorkRequestBuilder<ClusterWorker>()
            .setConstraints(constraints)
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
