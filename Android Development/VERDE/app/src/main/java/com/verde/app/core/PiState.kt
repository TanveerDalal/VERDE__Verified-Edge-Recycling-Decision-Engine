package com.verde.app.core

import com.verde.app.data.PiRepository
import kotlin.coroutines.cancellation.CancellationException

/** What we know about the Raspberry Pi. Only READY allows scanning with the Pi camera. */
enum class PiState {
    CHECKING,   // asking the Pi
    OFFLINE,    // the Pi didn't answer
    READY       // the Pi answered GET /health with "ok"
}

/**
 * Asks the Pi at [address] how it is, using the team's PiRepository (data/PiService.kt).
 * Never throws: no answer = OFFLINE.
 */
suspend fun askPiState(address: String): PiState = try {
    if (PiRepository(address).isOnline()) PiState.READY else PiState.OFFLINE
} catch (e: CancellationException) {
    throw e                 // the screen closed: stop quietly
} catch (e: Exception) {
    PiState.OFFLINE         // no Wi-Fi, wrong address, server.py not running…
}
