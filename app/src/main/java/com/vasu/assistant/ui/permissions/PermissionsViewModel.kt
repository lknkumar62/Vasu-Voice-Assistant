package com.vasu.assistant.ui.permissions

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PermissionsUiState(
    val grantedCount: Int = 0,
    val totalCount: Int = 0,
    val lastRefresh: Long = System.currentTimeMillis()
)

@HiltViewModel
class PermissionsViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(PermissionsUiState())
    val uiState: StateFlow<PermissionsUiState> = _uiState.asStateFlow()

    fun refreshPermissions(context: Context) {
        viewModelScope.launch {
            val permissions = listOf(
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.POST_NOTIFICATIONS,
                android.Manifest.permission.READ_CONTACTS,
                android.Manifest.permission.CALL_PHONE,
                android.Manifest.permission.SEND_SMS,
                android.Manifest.permission.READ_PHONE_STATE,
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO,
                android.Manifest.permission.READ_MEDIA_AUDIO,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.BLUETOOTH,
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.SCHEDULE_EXACT_ALARM,
                android.Manifest.permission.SYSTEM_ALERT_WINDOW,
                android.Manifest.permission.QUERY_ALL_PACKAGES
            )

            val granted = permissions.count { perm ->
                androidx.core.content.ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            }

            _uiState.value = PermissionsUiState(
                grantedCount = granted,
                totalCount = permissions.size,
                lastRefresh = System.currentTimeMillis()
            )
        }
    }
}
