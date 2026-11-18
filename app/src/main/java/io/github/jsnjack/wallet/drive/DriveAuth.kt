package io.github.jsnjack.wallet.drive

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The result of asking Google for Drive access. */
sealed interface AuthOutcome {
    /** Access is granted; [token] is a short-lived OAuth access token. */
    data class Granted(val token: String) : AuthOutcome

    /** The user must pick an account or approve access; launch [intent] from an activity. */
    data class NeedsConsent(val intent: PendingIntent) : AuthOutcome
}

/** Access to the hidden Drive app folder through Google Play services. */
object DriveAuth {
    /** The only Drive permission the app asks for: its own hidden folder. */
    const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"

    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(SCOPE)))
        .build()

    /** Asks for access; silent when the user approved it before. Throws [IOException] on failure. */
    suspend fun authorize(context: Context): AuthOutcome {
        val result = try {
            Identity.getAuthorizationClient(context).authorize(request).await()
        } catch (e: ApiException) {
            throw IOException("Drive authorization failed with status ${e.statusCode}", e)
        }
        return outcome(result)
    }

    /** The outcome after the consent screen returned [data]. Throws [IOException] when it was cancelled. */
    fun afterConsent(context: Context, data: Intent?): AuthOutcome {
        val result = try {
            Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
        } catch (e: ApiException) {
            throw IOException("Drive consent failed with status ${e.statusCode}", e)
        }
        return outcome(result)
    }

    private fun outcome(result: AuthorizationResult): AuthOutcome {
        if (result.hasResolution()) {
            val intent = result.pendingIntent ?: throw IOException("Drive authorization needs consent but gave no screen")
            return AuthOutcome.NeedsConsent(intent)
        }
        val token = result.accessToken ?: throw IOException("Drive authorization returned no access token")
        return AuthOutcome.Granted(token)
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
}
