package io.github.jsnjack.wallet.core

/** One card file in the Drive app folder, as the file listing describes it. */
data class RemoteEntry(val cardId: String, val fileId: String, val updatedAt: Long)

/** What one sync run transfers: files to download, and local card ids to upload. */
data class SyncPlan(val download: List<RemoteEntry>, val upload: List<String>)

/**
 * Sync rules. Each card is one file; the copy with the later "last changed"
 * time wins, so a sync never needs to download a file to compare it.
 */
object SyncRules {
    private const val FILE_PREFIX = "card-"
    private const val FILE_SUFFIX = ".json"

    /** The Drive file name that holds card [cardId]. */
    fun fileName(cardId: String): String = "$FILE_PREFIX$cardId$FILE_SUFFIX"

    /** The card id in a Drive file name, or null for files that are not card files. */
    fun cardId(fileName: String?): String? =
        fileName?.takeIf { it.startsWith(FILE_PREFIX) && it.endsWith(FILE_SUFFIX) }
            ?.removePrefix(FILE_PREFIX)?.removeSuffix(FILE_SUFFIX)?.takeIf { it.isNotEmpty() }

    /** True when an incoming copy changed at [incoming] should replace a local copy changed at [existing]. */
    fun isNewer(incoming: Long, existing: Long?): Boolean = existing == null || incoming > existing

    /** One entry per card: when two devices created the same file, the newest one counts. */
    fun newestPerCard(entries: List<RemoteEntry>): Map<String, RemoteEntry> =
        entries.groupBy { it.cardId }.mapValues { (_, copies) -> copies.maxBy { it.updatedAt } }

    /** Compares local "last changed" times by card id with the remote listing. */
    fun plan(local: Map<String, Long>, remote: Map<String, RemoteEntry>): SyncPlan {
        val download = remote.values.filter { isNewer(it.updatedAt, local[it.cardId]) }
        val upload = local.filter { (id, updatedAt) -> isNewer(updatedAt, remote[id]?.updatedAt) }.keys
        return SyncPlan(download.sortedBy { it.cardId }, upload.sorted())
    }
}
