package com.maxlutz.instasaved.sync

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

/**
 * Access to the user's Google Drive, read-only (ADR-0004), through Google Identity's AuthorizationClient. Once the
 * user has granted it, each call hands a fresh access token without asking again.
 */
object DriveAuthorization {
    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.readonly")))
        .build()

    sealed interface Outcome {
        data class Granted(val accessToken: String) : Outcome

        /** The user must pick an account or consent first, through [consent]; [fromConsent] reads the answer. */
        data class NeedsConsent(val consent: PendingIntent) : Outcome

        data class Failed(val problem: SyncProblem) : Outcome
    }

    suspend fun authorize(context: Context): Outcome = try {
        outcomeOf(Identity.getAuthorizationClient(context).authorize(request).await())
    } catch (e: ApiException) {
        Outcome.Failed(problemOf(e))
    }

    /** The outcome of the consent screen [Outcome.NeedsConsent] opened, from the Intent it answered with. */
    fun fromConsent(context: Context, data: Intent?): Outcome = try {
        outcomeOf(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data))
    } catch (e: ApiException) {
        Outcome.Failed(problemOf(e))
    }

    private fun outcomeOf(result: AuthorizationResult): Outcome {
        val consent = result.pendingIntent
        val token = result.accessToken
        return when {
            result.hasResolution() && consent != null -> Outcome.NeedsConsent(consent)
            token != null -> Outcome.Granted(token)
            else -> Outcome.Failed(SyncProblem.AccessNotGranted)
        }
    }

    private fun problemOf(e: ApiException) =
        if (e.statusCode == CommonStatusCodes.NETWORK_ERROR) SyncProblem.Offline else SyncProblem.AccessNotGranted
}
