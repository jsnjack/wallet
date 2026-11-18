package io.github.jsnjack.wallet.drive

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.jsnjack.wallet.LOG_TAG
import io.github.jsnjack.wallet.WalletApplication
import java.io.IOException

/** Background sync run; asks Google for access silently and gives up when it needs the user. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sync = (applicationContext as WalletApplication).sync
        if (!sync.enabled) return Result.success()
        return try {
            when (val outcome = DriveAuth.authorize(applicationContext)) {
                is AuthOutcome.NeedsConsent -> {
                    sync.markNeedsSignIn()
                    Result.success()
                }
                is AuthOutcome.Granted -> {
                    sync.syncWithToken(outcome.token)
                    Result.success()
                }
            }
        } catch (e: IOException) {
            Log.w(LOG_TAG, "background sync attempt ${runAttemptCount + 1} failed: ${e.message}")
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val MAX_RETRIES = 4
    }
}
