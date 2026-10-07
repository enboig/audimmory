package org.audimmory.mobile.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import org.audimmory.mobile.data.local.SessionStore
import org.audimmory.mobile.data.repository.AuthRepository
import org.audimmory.mobile.data.repository.BookmarkRepository
import org.audimmory.mobile.data.repository.PlaybackHistoryRepository
import org.audimmory.mobile.data.repository.ProgressRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class SyncWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val sessionStore: SessionStore,
        private val progressRepository: ProgressRepository,
        private val bookmarkRepository: BookmarkRepository,
        private val playbackHistoryRepository: PlaybackHistoryRepository,
        private val authRepository: AuthRepository,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            if (sessionStore.currentToken().isNullOrBlank()) return Result.success()

            val progress = progressRepository.sync()
            val bookmarks = bookmarkRepository.sync()
            val history = playbackHistoryRepository.sync()
            val account = authRepository.refreshCurrentUser()

            return if (progress.isSuccess && bookmarks.isSuccess && history.isSuccess && account.isSuccess) {
                Result.success()
            } else {
                Result.retry()
            }
        }

        companion object {
            const val PERIODIC_WORK_NAME = "audimmory_periodic_sync"
            const val ONE_TIME_WORK_NAME = "audimmory_sync_now"

            val constraints: Constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
        }
    }

@Singleton
class SyncScheduler
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val workManager = WorkManager.getInstance(context)

        fun schedulePeriodic() {
            val request =
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(SyncWorker.constraints)
                    .build()

            workManager.enqueueUniquePeriodicWork(
                SyncWorker.PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun enqueueNow() {
            val request =
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(SyncWorker.constraints)
                    .build()

            workManager.enqueueUniqueWork(
                SyncWorker.ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun cancelAll() {
            workManager.cancelUniqueWork(SyncWorker.ONE_TIME_WORK_NAME)
            workManager.cancelUniqueWork(SyncWorker.PERIODIC_WORK_NAME)
        }
    }
