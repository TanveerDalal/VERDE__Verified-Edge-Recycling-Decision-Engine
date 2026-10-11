package com.verde.app.pi

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verde.app.data.PiRepository
import com.verde.app.data.PiScanOutcome
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException

enum class ConnectionStatus { Unknown, Checking, Online, Offline }

// Everything the Pi screen displays, in one place
data class PiUiState(
    val address: String = "10.66.129.96",
    val connection: ConnectionStatus = ConnectionStatus.Unknown,
    val isScanning: Boolean = false,
    val outcome: PiScanOutcome? = null,
    val error: String? = null
)

class PiViewModel : ViewModel() {

    // The screen reads this; only the ViewModel can change it
    var uiState by mutableStateOf(PiUiState())
        private set

    fun onAddressChange(newAddress: String) {
        // A new address means we no longer know if the Pi is online
        uiState = uiState.copy(
            address = newAddress,
            connection = ConnectionStatus.Unknown,
            error = null
        )
    }

    fun connect() {
        val address = uiState.address
        uiState = uiState.copy(connection = ConnectionStatus.Checking, error = null)
        viewModelScope.launch {
            uiState = try {
                val online = PiRepository(address).isOnline()
                uiState.copy(
                    connection = if (online) ConnectionStatus.Online else ConnectionStatus.Offline,
                    error = if (online) null else "The Pi replied, but not with \"ok\"."
                )
            } catch (e: CancellationException) {
                throw e // the screen was closed; let the coroutine stop normally
            } catch (e: Exception) {
                uiState.copy(connection = ConnectionStatus.Offline, error = friendlyError(e))
            }
        }
    }

    fun scan() {
        val address = uiState.address
        uiState = uiState.copy(isScanning = true, outcome = null, error = null)
        viewModelScope.launch {
            uiState = try {
                val outcome = PiRepository(address).scan()
                uiState.copy(isScanning = false, outcome = outcome)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState.copy(
                    isScanning = false,
                    connection = ConnectionStatus.Offline,
                    error = friendlyError(e)
                )
            }
        }
    }

    // Turn technical network errors into messages a user can act on
    private fun friendlyError(e: Exception): String = when (e) {
        is SocketTimeoutException ->
            "The Pi took too long to reply. Is server.py still running?"
        is ConnectException, is NoRouteToHostException ->
            "Couldn't reach the Pi. Check the address, that both devices are on the hotspot, and that server.py is running."
        is UnknownHostException, is IllegalArgumentException ->
            "That address doesn't look right. It should look like 10.66.129.96"
        else ->
            "Something went wrong: ${e.message ?: e::class.simpleName}"
    }
}