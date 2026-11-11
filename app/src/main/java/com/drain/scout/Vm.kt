package com.drain.scout

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class UiState(
    val hasPermission: Boolean = false,
    val loading: Boolean = false,
    val snapshot: Snapshot? = null,
    val refLevel: Int? = null,
    val refTime: Long? = null,
    val updateAlerts: Boolean = true,
    val error: String? = null
)

internal class DrainScoutViewModel(app: Application) : AndroidViewModel(app) {

    private val appContext: Context = app
    private val prefs = app.getSharedPreferences("drainscout", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        UiState(
            refLevel = prefs.getInt(KEY_LEVEL, -1).takeIf { it >= 0 },
            refTime = prefs.getLong(KEY_TIME, 0L).takeIf { it > 0L },
            updateAlerts = UpdatePrefs.enabled(app)
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun refreshPermissionState() {
        _state.value = _state.value.copy(hasPermission = DiagnosticEngine.hasShellPermission())
    }

    fun capture() {
        if (_state.value.loading) return
        if (!DiagnosticEngine.hasShellPermission()) {
            _state.value = _state.value.copy(
                hasPermission = false,
                error = "Shizuku permission is required. Start Shizuku, then tap Connect Shizuku."
            )
            return
        }
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val snap = withContext(Dispatchers.IO) { DiagnosticEngine.capture() }
                val level = snap.battery?.level
                var refLevel = _state.value.refLevel
                var refTime = _state.value.refTime
                if (refLevel == null && level != null) {
                    refLevel = level
                    refTime = snap.capturedAt
                    saveReference(level, snap.capturedAt)
                }
                _state.value = _state.value.copy(
                    loading = false,
                    hasPermission = true,
                    snapshot = snap,
                    refLevel = refLevel,
                    refTime = refTime,
                    error = if (level == null) "No data came back. Is Shizuku still running?" else null
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = t.message ?: "Capture failed."
                )
            }
        }
    }

    fun setReference() {
        val snap = _state.value.snapshot ?: return
        val level = snap.battery?.level ?: return
        saveReference(level, snap.capturedAt)
        _state.value = _state.value.copy(refLevel = level, refTime = snap.capturedAt)
    }

    fun toggleUpdateAlerts() {
        val enabled = !_state.value.updateAlerts
        UpdatePrefs.setEnabled(appContext, enabled)
        UpdateScheduler.sync(appContext, enabled)
        _state.value = _state.value.copy(updateAlerts = enabled)
    }

    private fun saveReference(level: Int, time: Long) {
        prefs.edit().putInt(KEY_LEVEL, level).putLong(KEY_TIME, time).apply()
    }

    private companion object {
        const val KEY_LEVEL = "ref_level"
        const val KEY_TIME = "ref_time"
    }
}
