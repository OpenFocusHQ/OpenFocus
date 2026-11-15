package com.openfocushq.openfocus.data.sync

import com.openfocushq.openfocus.data.db.ActivityEventEntity

/**
 * Seam for future online / multiplayer features (parties, shared challenges, accountability
 * partners, leaderboards). Nothing in the app depends on a network today.
 *
 * A future implementation would:
 *  1. Push un-synced [ActivityEventEntity] rows (the outbox) and mark them synced.
 *  2. Pull remote events (e.g. a party member finishing a focus session) and surface them in a
 *     "party feed" without ever touching the local profile's XP directly.
 *  3. Use the UUID ids + updatedAt/deleted columns on entities for last-writer-wins replication.
 */
interface SyncGateway {
    val isEnabled: Boolean
    suspend fun pushEvents(events: List<ActivityEventEntity>): Result<Unit>
}

/** Default: fully offline. */
object OfflineSyncGateway : SyncGateway {
    override val isEnabled = false
    override suspend fun pushEvents(events: List<ActivityEventEntity>) = Result.success(Unit)
}
