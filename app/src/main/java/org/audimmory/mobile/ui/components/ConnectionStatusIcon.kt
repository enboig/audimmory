package org.audimmory.mobile.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.audimmory.mobile.R
import org.audimmory.mobile.data.repository.ConnectionStatus
import org.audimmory.mobile.data.repository.ConnectionStatusRepository
import javax.inject.Inject

@HiltViewModel
class ConnectionStatusViewModel
    @Inject
    constructor(
        repository: ConnectionStatusRepository,
    ) : ViewModel() {
        val status: StateFlow<ConnectionStatus> =
            repository.status
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionStatus.Connected)
    }

@Composable
fun ConnectionStatusIcon(
    modifier: Modifier = Modifier,
    viewModel: ConnectionStatusViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()

    when (status) {
        ConnectionStatus.Connected -> Unit
        ConnectionStatus.NoInternet ->
            Icon(
                Icons.Default.WifiOff,
                contentDescription = stringResource(R.string.status_no_internet),
                tint = MaterialTheme.colorScheme.error,
                modifier = modifier.size(20.dp),
            )
        ConnectionStatus.ServerUnavailable ->
            Icon(
                Icons.Default.CloudOff,
                contentDescription = stringResource(R.string.status_server_unavailable),
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = modifier.size(20.dp),
            )
    }
}
