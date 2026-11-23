package com.verde.app.core

import java.util.concurrent.locks.ReentrantLock

/**
 * ExecuTorch on this phone runs one job at a time.
 * The image model and Llama share the same XNNPACK threads; running both at once froze the app
 * (second scan never finished, WhatsApp photos got no answer). Scans have priority: they stop
 * any Llama answer in progress, then run (about 100 ms).
 */
object EngineLock {
    val lock = ReentrantLock(true)
}
