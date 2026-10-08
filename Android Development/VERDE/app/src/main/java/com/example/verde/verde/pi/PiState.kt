package com.example.verde.pi

/** What we know about the Raspberry Pi. Only READY allows scanning with the Pi camera. */
enum class PiState {
    CHECKING,   // asking the Pi
    OFFLINE,    // the Pi didn't answer
    NO_CAMERA,  // the Pi answered, but has no working camera
    READY       // the Pi and its camera are ready
}
