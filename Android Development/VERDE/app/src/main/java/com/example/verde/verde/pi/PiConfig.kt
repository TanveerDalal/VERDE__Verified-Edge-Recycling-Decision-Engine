package com.example.verde.pi

/** Where the Raspberry Pi is. Change the IP to your Pi's (`hostname -I` on the Pi). Port 5000 = config.py. */
object PiConfig {
    const val ADDRESS = "http://192.168.1.42:5000"
}
