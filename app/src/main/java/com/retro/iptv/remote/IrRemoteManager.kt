package com.retro.iptv.remote

import android.content.Context
import android.hardware.ConsumerIrManager

enum class TvBrand(val label: String) {
    WALTON("Walton"), SAMSUNG("Samsung"), LG("LG"),
    SONY("Sony"), XIAOMI("Xiaomi"), HISENSE("Hisense")
}

class IrRemoteManager(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    private val preferences = appContext.getSharedPreferences("retro_ir_remote", Context.MODE_PRIVATE)

    val hasEmitter: Boolean get() = manager?.hasIrEmitter() == true
    fun hasIrBlaster(): Boolean = hasEmitter

    fun getSelectedBrand(): TvBrand {
        val saved = preferences.getString(KEY_BRAND, TvBrand.WALTON.name)
        return TvBrand.values().firstOrNull { it.name == saved } ?: TvBrand.WALTON
    }

    fun setSelectedBrand(brand: TvBrand) {
        preferences.edit().putString(KEY_BRAND, brand.name).apply()
    }

    // Generic manufacturer-family power frames. Model-specific IR addresses can differ.
    fun sendPowerToggle(brand: TvBrand = getSelectedBrand()): Boolean {
        val frame = when (brand) {
            TvBrand.SAMSUNG -> necFrame(0xE0, 0xE0, 0x40)
            TvBrand.LG -> necFrame(0x20, 0xDF, 0x10)
            TvBrand.WALTON, TvBrand.XIAOMI -> necFrame(0x00, 0xFF, 0x02)
            TvBrand.HISENSE -> rc5Frame(address = 0, command = 0x0C)
            TvBrand.SONY -> sonySirc12(0xA90)
        }
        return transmitValidated(frame.first, frame.second)
    }

    fun transmitValidated(carrierFrequencyHz: Int, patternMicros: IntArray): Boolean {
        if (!hasEmitter || carrierFrequencyHz !in 20_000..60_000 ||
            patternMicros.isEmpty() || patternMicros.size > 1024 ||
            patternMicros.any { it !in 1..100_000 }) return false
        return runCatching {
            manager?.transmit(carrierFrequencyHz, patternMicros)
            true
        }.getOrDefault(false)
    }

    private fun necFrame(address: Int, inverseAddress: Int, command: Int): Pair<Int, IntArray> {
        val bytes = intArrayOf(address, inverseAddress, command, command.inv() and 0xFF)
        val pulses = ArrayList<Int>(68)
        pulses += 9000
        pulses += 4500
        bytes.forEach { byte ->
            for (bit in 0 until 8) {
                pulses += 560
                pulses += if (((byte shr bit) and 1) == 1) 1690 else 560
            }
        }
        pulses += 560
        return 38_000 to pulses.toIntArray()
    }

    private fun rc5Frame(address: Int, command: Int): Pair<Int, IntArray> {
        val field = if (command < 0x40) 1 else 0
        val frame = (1 shl 13) or (field shl 12) or
            ((address and 0x1F) shl 6) or (command and 0x3F)
        val levels = ArrayList<Boolean>(28)
        for (bit in 13 downTo 0) {
            val one = ((frame shr bit) and 1) == 1
            levels += one
            levels += !one
        }
        val pulses = ArrayList<Int>(28)
        var current = levels.first()
        var duration = 889
        for (index in 1 until levels.size) {
            if (levels[index] == current) duration += 889
            else {
                pulses += duration
                duration = 889
                current = levels[index]
            }
        }
        pulses += duration
        return 36_000 to pulses.toIntArray()
    }

    private fun sonySirc12(command: Int): Pair<Int, IntArray> {
        val pulses = ArrayList<Int>(26)
        pulses += 2400
        pulses += 600
        for (bit in 0 until 12) {
            pulses += if (((command shr bit) and 1) == 1) 1200 else 600
            pulses += 600
        }
        return 40_000 to pulses.toIntArray()
    }

    companion object { private const val KEY_BRAND = "selected_tv_brand" }
}
