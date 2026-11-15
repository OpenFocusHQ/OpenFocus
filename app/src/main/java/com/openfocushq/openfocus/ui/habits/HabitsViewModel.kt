package com.openfocushq.openfocus.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.openfocushq.openfocus.AppContainer

class HabitsViewModel(private val c: AppContainer) : ViewModel() {
    val habits = c.habits.observeStates().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun tap(id: String) = viewModelScope.launch { c.habits.tap(id) }
    fun undo(id: String) = viewModelScope.launch { c.habits.untap(id) }
}
