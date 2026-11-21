package com.retro.iptv.remote

import android.content.Context
import android.hardware.ConsumerIrManager

enum class DeviceCategory(val label: String) {
    SMART_TV("Smart TV"),
    TV_BOX("Android TV Box")
}

enum class IrProfile(val category: DeviceCategory, val label: String) {
    WALTON(DeviceCategory.SMART_TV, "Walton"),
    SAMSUNG(DeviceCategory.SMART_TV, "Samsung"),
    LG(DeviceCategory.SMART_TV, "LG"),
    SONY(DeviceCategory.SMART_TV, "Sony"),
    XIAOMI_TV(DeviceCategory.SMART_TV, "Xiaomi TV"),
    HISENSE_TCL(DeviceCategory.SMART_TV, "Hisense / TCL"),
    AOSP_BOX(DeviceCategory.TV_BOX, "AOSP / X96 / H96"),
    MI_BOX(DeviceCategory.TV_BOX, "Xiaomi Mi Box"),
    MXQ_TX3(DeviceCategory.TV_BOX, "MXQ / TX3 / T95"),
    MECOOL(DeviceCategory.TV_BOX, "Mecool"),
    GENERIC_NEC(DeviceCategory.TV_BOX, "Generic NEC")
}

/** Legacy names retained only to migrate older saved brand preferences. */
enum class TvBrand(val label: String) {
    WALTON("Walton"), SAMSUNG("Samsung"), LG("LG"), SONY("Sony"),
    XIAOMI("Xiaomi"), HISENSE("Hisense"), GENERIC("Generic")
}

class IrRemoteManager(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    private val preferences = appContext.getSharedPreferences("retro_ir_remote", Context.MODE_PRIVATE)
    private val setupPreferences = appContext.getSharedPreferences("retro_mobile_remote_setup", Context.MODE_PRIVATE)

    val hasEmitter: Boolean get() = manager?.hasIrEmitter() == true
    fun hasIrBlaster(): Boolean = hasEmitter

    var selectedCategory: DeviceCategory
        get() = runCatching {
            DeviceCategory.valueOf(setupPreferences.getString(KEY_CATEGORY, null) ?: selectedProfile.category.name)
        }.getOrDefault(DeviceCategory.SMART_TV)
        set(value) { setupPreferences.edit().putString(KEY_CATEGORY, value.name).apply() }

    var selectedProfile: IrProfile
        get() {
            val saved = setupPreferences.getString(KEY_PROFILE, null)
                ?: legacyProfile(setupPreferences.getString(KEY_SAVED_BRAND, preferences.getString(KEY_BRAND, null))).name
            return runCatching { IrProfile.valueOf(saved) }.getOrDefault(IrProfile.WALTON)
        }
        set(value) {
            setupPreferences.edit()
                .putString(KEY_CATEGORY, value.category.name)
                .putString(KEY_PROFILE, value.name)
                .putString(KEY_SAVED_BRAND, value.name)
                .apply()
        }

    fun profilesFor(category: DeviceCategory): List<IrProfile> =
        IrProfile.values().filter { it.category == category }

    fun transmitPower(profile: IrProfile = selectedProfile): Boolean {
        if (!hasEmitter) return false
        val frames = when (profile) {
            IrProfile.SAMSUNG -> listOf(CARRIER_HZ to buildSamsungFrame(0xE0E09966L))
            IrProfile.SONY -> listOf(40_000 to buildSonyFrame(command = 0x15, address = 0x01))
            IrProfile.WALTON, IrProfile.LG -> listOf(CARRIER_HZ to buildNecFrame(0x04FB, 0x08))
            IrProfile.XIAOMI_TV, IrProfile.HISENSE_TCL -> listOf(CARRIER_HZ to buildNecFrame(0x00FF, 0x12))
            IrProfile.AOSP_BOX -> listOf(
                CARRIER_HZ to buildNecFrame(0x00FF, 0x0A),
                CARRIER_HZ to buildNecFrame(0x807F, 0x4D)
            )
            IrProfile.MI_BOX -> listOf(
                CARRIER_HZ to buildNecFrame(0x40BF, 0x0A),
                CARRIER_HZ to buildNecFrame(0x00FF, 0x18)
            )
            IrProfile.MXQ_TX3 -> listOf(CARRIER_HZ to buildNecFrame(0x00FF, 0x12))
            IrProfile.MECOOL -> listOf(CARRIER_HZ to buildNecFrame(0x00FF, 0x0A))
            IrProfile.GENERIC_NEC -> listOf(CARRIER_HZ to buildNecFrame(0x00FF, 0x00))
        }
        return frames.map { (frequency, pattern) -> transmitValidated(frequency, pattern) }.any { it }
    }

    /** Best-effort source/input command; exact codes vary by TV model. */
    fun sendSourceInput(profile: IrProfile = selectedProfile): Boolean {
        val frame = when (profile) {
            IrProfile.SAMSUNG -> CARRIER_HZ to buildSamsungFrame(0xE0E0807FL)
            IrProfile.SONY -> 40_000 to buildSonyFrame(command = 0x50, address = 0x01)
            IrProfile.WALTON, IrProfile.LG -> CARRIER_HZ to buildNecFrame(0x04FB, 0x1A)
            else -> CARRIER_HZ to buildNecFrame(0x00FF, 0x1A)
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

    fun sendPowerToggle(profile: IrProfile = selectedProfile): Boolean = transmitPower(profile)

    companion object {
        private const val CARRIER_HZ = 38_000
        private const val KEY_CATEGORY = "savedDeviceCategory"
        private const val KEY_PROFILE = "savedIrProfile"
        private const val KEY_SAVED_BRAND = "savedTvBrand"
        private const val KEY_BRAND = "selected_tv_brand"

        private fun legacyProfile(value: String?): IrProfile = when (value) {
            "SAMSUNG" -> IrProfile.SAMSUNG
            "LG" -> IrProfile.LG
            "SONY" -> IrProfile.SONY
            "XIAOMI" -> IrProfile.XIAOMI_TV
            "HISENSE" -> IrProfile.HISENSE_TCL
            "GENERIC" -> IrProfile.GENERIC_NEC
            else -> IrProfile.WALTON
        }

        /** 32-bit extended NEC frame: 16-bit address + command + inverted command. */
        fun buildNecFrame(address: Int, command: Int): IntArray {
            val pulses = ArrayList<Int>(68)
            pulses += 9000
            pulses += 4500
            fun appendBits(value: Int, count: Int) {
                for (bit in 0 until count) {
                    pulses += 560
                    pulses += if (((value ushr bit) and 1) == 1) 1690 else 560
                }
            }
            appendBits(address and 0xFFFF, 16)
            appendBits(command and 0xFF, 8)
            appendBits(command.inv() and 0xFF, 8)
            pulses += 560
            pulses += 40_000
            return pulses.toIntArray()
        }

        /** Complete 68-duration Samsung 32-bit frame. */
        fun buildSamsungFrame(data: Long): IntArray {
            val pulses = ArrayList<Int>(68)
            pulses += 4500
            pulses += 4500
            for (bit in 0 until 32) {
                pulses += 560
                pulses += if (((data ushr bit) and 1L) == 1L) 1690 else 560
            }
            pulses += 560
            pulses += 46_900
            return pulses.toIntArray()
        }

        /** Complete Sony SIRC 12-bit frame, including the terminal mark and gap. */
        fun buildSonyFrame(command: Int, address: Int): IntArray {
            val data = (command and 0x7F) or ((address and 0x1F) shl 7)
            val pulses = ArrayList<Int>(28)
            pulses += 2400
            pulses += 600
            for (bit in 0 until 12) {
                pulses += if (((data ushr bit) and 1) == 1) 1200 else 600
                pulses += 600
            }
            pulses += 600
            pulses += 45_000
            return pulses.toIntArray()
        }
    }
}
