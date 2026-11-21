package com.retro.iptv.remote

import android.content.Context
import android.hardware.ConsumerIrManager

class IrRemoteManager(context: Context) {
    private val manager = context.applicationContext.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    val hasEmitter: Boolean get() = manager?.hasIrEmitter() == true

    fun transmitValidated(carrierFrequencyHz: Int, patternMicros: IntArray): Boolean {
        if (!hasEmitter || carrierFrequencyHz !in 20_000..60_000 ||
            patternMicros.isEmpty() || patternMicros.size > 1024 ||
            patternMicros.any { it !in 1..100_000 }) return false
        return runCatching {
            manager?.transmit(carrierFrequencyHz, patternMicros)
            true
        }.getOrDefault(false)
    }
}