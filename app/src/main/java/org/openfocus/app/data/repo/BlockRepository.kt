package org.openfocus.app.data.repo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.openfocus.app.blocking.BlockConfig
import org.openfocus.app.data.db.AppDatabase
import org.openfocus.app.data.db.BlockRuleEntity
import org.openfocus.app.data.db.BlockScheduleEntity
import org.openfocus.app.data.prefs.SettingsStore

class BlockRepository(db: AppDatabase, private val settings: SettingsStore, scope: CoroutineScope) {
    private val dao = db.blockDao()

    val rules = dao.observeRules()
    val schedules = dao.observeSchedules()

    /** Always-hot snapshot read synchronously by the accessibility service. */
    val config: StateFlow<BlockConfig> =
        combine(rules, schedules, settings.state) { r, s, st ->
            BlockConfig(rules = r.associateBy { it.packageName }, schedules = s, settings = st)
        }.stateIn(scope, SharingStarted.Eagerly, BlockConfig(settings = settings.value))

    suspend fun upsertRule(rule: BlockRuleEntity) = dao.upsertRule(rule.copy(updatedAt = System.currentTimeMillis()))
    suspend fun removeRule(pkg: String) = dao.deleteRule(pkg)
    suspend fun upsertSchedule(s: BlockScheduleEntity) = dao.upsertSchedule(s)
    suspend fun deleteSchedule(s: BlockScheduleEntity) = dao.deleteSchedule(s)
}
