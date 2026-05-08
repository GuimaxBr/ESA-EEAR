package com.guima.esa.cluster

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ClusterWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val cycleResult = ClusterWorkerRepository.runSingleCycle(applicationContext)
            ClusterWorkerRepository.persistCycleSnapshot(cycleResult)
            ClusterWorkScheduler.enqueueNext(applicationContext, cycleResult.suggestedDelaySeconds)
            Result.success()
        } catch (error: Exception) {
            ClusterWorkerRepository.persistCycleSnapshot(
                ClusterCycleResult(
                    status = "Erro de conexao",
                    suggestedDelaySeconds = 120
                )
            )
            ClusterWorkScheduler.enqueueNext(applicationContext, 120)
            Result.retry()
        }
    }
}
