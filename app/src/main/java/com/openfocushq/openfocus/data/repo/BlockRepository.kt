package com.openfocushq.openfocus.data.repo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import com.openfocushq.openfocus.blocking.BlockConfig
import com.openfocushq.openfocus.data.db.AppDatabase
import com.openfocushq.openfocus.data.db.BlockRuleEntity
import com.openfocushq.openfocus.data.db.BlockScheduleEntity
import com.openfocushq.openfocus.data.prefs.SettingsStore

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
