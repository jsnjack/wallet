package io.github.jsnjack.wallet.core

/** Why turning on Google Drive sync failed, in terms the sync section can explain. */
enum class ConnectProblem {
    /** Google does not know this app: no OAuth client for its package name and signing key. */
    NOT_REGISTERED,

    /** The Google Cloud project behind the app has the Drive API switched off. */
    API_DISABLED,

    /** Google could not be reached. */
    NETWORK,

    /** Anything else; the code is shown so it can be looked up. */
    OTHER,
    ;

    companion object {
        // Play services CommonStatusCodes, which the auth API reports.
        private const val NETWORK_ERROR = 7
        private const val DEVELOPER_ERROR = 10
        private const val CANCELED = 16
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403

        /** The problem for a Play services status code; null when the user cancelled, which is not a problem. */
        fun fromAuthStatus(status: Int): ConnectProblem? = when (status) {
            CANCELED -> null
            DEVELOPER_ERROR -> NOT_REGISTERED
            NETWORK_ERROR -> NETWORK
            else -> OTHER
        }

        /** The problem for an HTTP status from the Drive API during the first sync. */
        fun fromHttpStatus(status: Int): ConnectProblem = when (status) {
            HTTP_UNAUTHORIZED -> NOT_REGISTERED
            HTTP_FORBIDDEN -> API_DISABLED
            else -> OTHER
        }
    }
}
