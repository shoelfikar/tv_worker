package com.tvworker.ui.screen.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvworker.data.local.ApprovalLogEntity
import com.tvworker.data.repository.ApprovalRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LogViewModel(private val repository: ApprovalRepository) : ViewModel() {

    val logs: StateFlow<List<ApprovalLogEntity>> = repository.logs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearLogs() {
        viewModelScope.launch { repository.clearLogs() }
    }
}
