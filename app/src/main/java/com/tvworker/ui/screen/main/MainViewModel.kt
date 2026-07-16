package com.tvworker.ui.screen.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvworker.data.local.ApprovalLogEntity
import com.tvworker.data.repository.ApprovalRepository
import com.tvworker.service.AccessibilityHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(private val repository: ApprovalRepository) : ViewModel() {

    data class UiState(
        val isAccessibilityEnabled: Boolean = false,
        val isAutoApproveEnabled: Boolean = true,
        val approvedCount: Int = 0,
        val lastApproval: ApprovalLogEntity? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.autoApproveEnabled.collect { enabled ->
                _uiState.update { it.copy(isAutoApproveEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            repository.approvedCount.collect { count ->
                _uiState.update { it.copy(approvedCount = count) }
            }
        }

        viewModelScope.launch {
            repository.logs.collect { logs ->
                _uiState.update { it.copy(lastApproval = logs.firstOrNull()) }
            }
        }
    }

    fun refreshAccessibilityStatus(context: Context) {
        val enabled = AccessibilityHelper.isAccessibilityEnabled(context)
        _uiState.update { it.copy(isAccessibilityEnabled = enabled) }
    }

    fun toggleAutoApprove(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoApprove(enabled)
        }
    }
}
