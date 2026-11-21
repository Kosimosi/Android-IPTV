package com.retro.iptv.remote

import android.content.Context
import android.hardware.ConsumerIrManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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
        if (profile == IrProfile.MI_BOX) {
            val xiaomiSent = transmitValidated(XIAOMI_CARRIER_HZ, buildXiaomi20BitFrame(0x86, 0xCC))
            return xiaomiSent || transmitValidated(CARRIER_HZ, buildExtendedNecFrame(0x847B, 0x02))
        }
        if (profile == IrProfile.XIAOMI_TV) {
            return transmitValidated(XIAOMI_CARRIER_HZ, buildXiaomi20BitFrame(0x86, 0xCC)) ||
                transmitValidated(CARRIER_HZ, buildExtendedNecFrame(0x00FF, 0x12))
        }
        val frames = when (profile) {
            IrProfile.SAMSUNG -> listOf(CARRIER_HZ to buildSamsungNecxFrame(0xE0E0, 0x99))
            IrProfile.SONY -> listOf(40_000 to buildSonyFrame(command = 0x15, address = 0x01))
            IrProfile.WALTON, IrProfile.LG -> listOf(CARRIER_HZ to buildExtendedNecFrame(0x04FB, 0x08))
            IrProfile.XIAOMI_TV, IrProfile.HISENSE_TCL -> listOf(CARRIER_HZ to buildExtendedNecFrame(0x00FF, 0x12))
            IrProfile.AOSP_BOX -> listOf(
                CARRIER_HZ to buildExtendedNecFrame(0x00FF, 0x0A),
                CARRIER_HZ to buildExtendedNecFrame(0x807F, 0x4D)
            )
            IrProfile.MI_BOX -> emptyList()
            IrProfile.MXQ_TX3 -> listOf(CARRIER_HZ to buildExtendedNecFrame(0x00FF, 0x12))
            IrProfile.MECOOL -> listOf(CARRIER_HZ to buildExtendedNecFrame(0x00FF, 0x0A))
            IrProfile.GENERIC_NEC -> listOf(CARRIER_HZ to buildExtendedNecFrame(0x00FF, 0x00))
        }
        return frames.map { (frequency, pattern) -> transmitValidated(frequency, pattern) }.any { it }
    }

    /** Best-effort source/input command; exact codes vary by TV model. */
    fun sendSourceInput(profile: IrProfile = selectedProfile): Boolean {
        val frame = when (profile) {
            IrProfile.SAMSUNG -> CARRIER_HZ to buildSamsungNecxFrame(0xE0E0, 0x80)
            IrProfile.SONY -> 40_000 to buildSonyFrame(command = 0x50, address = 0x01)
            IrProfile.WALTON, IrProfile.LG -> CARRIER_HZ to buildExtendedNecFrame(0x04FB, 0x1A)
            else -> CARRIER_HZ to buildExtendedNecFrame(0x00FF, 0x1A)
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

    fun sendKey(action: String, profile: IrProfile = selectedProfile): Boolean {
        if (!hasEmitter) return false
        val key = action.uppercase()
        if (profile == IrProfile.MI_BOX || profile == IrProfile.XIAOMI_TV) {
            val xiaomiCode = when (key) {
                "POWER" -> 0x86 to 204
                "UP" -> 0x86 to 0x05
                "DOWN" -> 0x86 to 0x06
                "LEFT" -> 0x86 to 0x0B
                "RIGHT" -> 0x86 to 0x0C
                "OK" -> 0x86 to 0x0D
                "BACK" -> 0x86 to 0x07
                "HOME" -> 0x86 to 0x08
                "MENU" -> 0x86 to 0x04
                "VOL_UP" -> 0x86 to 115
                "VOL_DOWN" -> 0x86 to 114
                "MUTE" -> 0x86 to 0xA1
                else -> null
            }
            if (xiaomiCode != null &&
                transmitValidated(XIAOMI_CARRIER_HZ, buildXiaomi20BitFrame(xiaomiCode.first, xiaomiCode.second))) return true
            if (profile == IrProfile.XIAOMI_TV) return false
            val necCommand = when (key) {
                "POWER" -> 0x02; "UP" -> 0x04; "DOWN" -> 0x05; "LEFT" -> 0x06
                "RIGHT" -> 0x07; "OK" -> 0x08; "HOME" -> 0x09; "BACK" -> 0x0A
                "MENU" -> 0x0B; "VOL_UP" -> 0x0C; "VOL_DOWN" -> 0x0D
                else -> null
            } ?: return false
            return transmitValidated(CARRIER_HZ, buildExtendedNecFrame(0x847B, necCommand))
        }
        val builtin = when (profile) {
            IrProfile.WALTON, IrProfile.LG -> {
                val command = when (key) {
                    "POWER" -> 0x08; "UP" -> 0x40; "DOWN" -> 0x41; "LEFT" -> 0x07; "RIGHT" -> 0x06
                    "OK" -> 0x44; "BACK" -> 0x28; "HOME" -> 0x7C
                    "VOL_UP" -> 0x02; "VOL_DOWN" -> 0x03; "MUTE" -> 0x09
                    else -> null
                } ?: return false
                CARRIER_HZ to buildExtendedNecFrame(0x04FB, command)
            }
            IrProfile.AOSP_BOX, IrProfile.MXQ_TX3, IrProfile.MECOOL, IrProfile.GENERIC_NEC -> {
                val command = when (key) {
                    "POWER" -> if (profile == IrProfile.AOSP_BOX) 0x0A else 0x12
                    "UP" -> 0x40; "DOWN" -> 0x41; "LEFT" -> 0x07; "RIGHT" -> 0x06
                    "OK" -> 0x44; "BACK" -> 0x28; "HOME" -> 0x7C
                    "VOL_UP" -> 0x02; "VOL_DOWN" -> 0x03; "MUTE" -> 0x09
                    else -> null
                } ?: return false
                CARRIER_HZ to buildExtendedNecFrame(0x00FF, command)
            }
            IrProfile.SAMSUNG -> {
                val command = when (key) {
                    "POWER" -> 0x02; "UP" -> 0x60; "DOWN" -> 0x61; "LEFT" -> 0x65
                    "RIGHT" -> 0x62; "OK" -> 0x68; "BACK" -> 0x58; "HOME" -> 0x79
                    "VOL_UP" -> 0x07; "VOL_DOWN" -> 0x0B; "MUTE" -> 0x0F
                    else -> null
                } ?: return false
                CARRIER_HZ to buildSamsungNecxFrame(0xE0E0, command)
            }
            else -> return false
        }
        return transmitValidated(builtin.first, builtin.second)
    }

    fun transmitIrdbCode(code: IrdbCode): Boolean {
        if (!hasEmitter) return false
        val protocol = code.protocol.uppercase().replace("_", "").replace("-", "")
        val necAddress16 = ((code.device and 0xFF) shl 8) or
            (if (code.subdevice >= 0) code.subdevice and 0xFF else code.device.inv() and 0xFF)
        val samsungAddress16 = (code.device and 0xFF) or
            (if (code.subdevice >= 0) (code.subdevice and 0xFF) shl 8 else 0)
        val frame = when (protocol) {
            "NEC", "NEC1", "NEC2", "NECEXT", "NECEXTENDED", "NECX1" ->
                CARRIER_HZ to buildExtendedNecFrame(necAddress16, code.function)
            "NECX2", "SAMSUNG32", "SAMSUNG" ->
                CARRIER_HZ to buildSamsungNecxFrame(samsungAddress16, code.function)
            "SONY12", "SIRC12" -> 40_000 to buildSonySircFrame(code.function, code.device, 12)
            "SONY15", "SIRC15" -> 40_000 to buildSonySircFrame(code.function, code.device, 15)
            "SONY20", "SIRC20" -> 40_000 to buildSonySircFrame(code.function, code.device, 20)
            else -> return false
        }
        return transmitValidated(frame.first, frame.second)
    }

    companion object {
        private const val CARRIER_HZ = 38_000
        private const val XIAOMI_CARRIER_HZ = 36_000
        private const val KEY_CATEGORY = "savedDeviceCategory"
        private const val KEY_PROFILE = "savedIrProfile"
        private const val KEY_SAVED_BRAND = "savedTvBrand"
        private const val KEY_BRAND = "selected_tv_brand"

        private fun legacyProfile(value: String?): IrProfile = when (value) {
            "SAMSUNG" -> IrProfile.SAMSUNG
            "LG" -> IrProfile.LG
            "SONY" -> IrProfile.SONY
            "XIAOMI", "XIAOMI_TV" -> IrProfile.XIAOMI_TV
            "HISENSE", "HISENSE_TCL" -> IrProfile.HISENSE_TCL
            "GENERIC", "GENERIC_NEC" -> IrProfile.GENERIC_NEC
            "AOSP_BOX" -> IrProfile.AOSP_BOX
            "MI_BOX" -> IrProfile.MI_BOX
            "MXQ_TX3" -> IrProfile.MXQ_TX3
            "MECOOL" -> IrProfile.MECOOL
            else -> IrProfile.WALTON
        }

        fun buildExtendedNecFrame(customCode16Bit: Int, command: Int): IntArray {
            val bytes = intArrayOf(
                customCode16Bit and 0xFF,
                (customCode16Bit ushr 8) and 0xFF,
                command and 0xFF,
                command.inv() and 0xFF
            )
            val pulses = ArrayList<Int>(68)
            pulses += 9000
            pulses += 4500
            bytes.forEach { byteValue ->
                for (bit in 0 until 8) {
                    pulses += 560
                    pulses += if (((byteValue ushr bit) and 1) == 1) 1690 else 560
                }
            }
            pulses += 560
            pulses += 40_000
            return pulses.toIntArray()
        }

        fun buildSamsungNecxFrame(custom16: Int, command: Int): IntArray {
            val bytes = intArrayOf(
                custom16 and 0xFF,
                (custom16 ushr 8) and 0xFF,
                command and 0xFF,
                command.inv() and 0xFF
            )
            val pulses = ArrayList<Int>(68)
            pulses += 4500
            pulses += 4500
            bytes.forEach { byteValue ->
                for (bit in 0 until 8) {
                    pulses += 560
                    pulses += if (((byteValue ushr bit) and 1) == 1) 1690 else 560
                }
            }
            pulses += 560
            pulses += 46_900
            return pulses.toIntArray()
        }

        /** Xiaomi RC-MM 20-bit: device + function + 4-bit XOR checksum. */
        fun buildXiaomi20BitFrame(device: Int = 0x86, function: Int): IntArray {
            val d = device and 0xFF
            val f = function and 0xFF
            val checksum = (((d ushr 4) and 0xF) xor (d and 0xF) xor
                ((f ushr 4) and 0xF) xor (f and 0xF)) and 0xF
            val data = (d shl 12) or (f shl 4) or checksum
            val pulses = ArrayList<Int>(44)
            pulses += 1000
            pulses += 580
            for (symbol in 0 until 10) {
                val pair = (data ushr (18 - symbol * 2)) and 0x3
                pulses += 580
                pulses += (2 + pair) * 290
            }
            pulses += 580
            pulses += 30_000
            return pulses.toIntArray()
        }

        fun buildSonySircFrame(command: Int, address: Int, bitCount: Int): IntArray {
            require(bitCount == 12 || bitCount == 15 || bitCount == 20)
            val addressBits = bitCount - 7
            val data = (command and 0x7F) or ((address and ((1 shl addressBits) - 1)) shl 7)
            val pulses = ArrayList<Int>(bitCount * 2 + 4)
            pulses += 2400
            pulses += 600
            for (bit in 0 until bitCount) {
                pulses += if (((data ushr bit) and 1) == 1) 1200 else 600
                pulses += 600
            }
            pulses += 600
            pulses += 45_000
            return pulses.toIntArray()
        }

        /** Legacy NEC ordering retained for existing device profiles and IRDB device/subdevice fields. */
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


/** One IRDB device CSV path, e.g. Samsung/TV/7,7.csv. */
data class IrdbEntry(val brand: String, val deviceType: String, val filePath: String) {
    val model: String get() = filePath.substringAfterLast('/').removeSuffix(".csv")
    val displayName: String get() = "$brand · $deviceType · $model"
}

data class IrdbCode(
    val functionName: String,
    val protocol: String,
    val device: Int,
    val subdevice: Int,
    val function: Int
)

/** On-demand IRDB client. Only the currently selected CSV is cached. */
class IrdbFetcher(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("retro_irdb_cache", Context.MODE_PRIVATE)

    suspend fun fetchIndex(): List<IrdbEntry> = withContext(Dispatchers.IO) {
        fetchText("$BASE_URL/index").lineSequence().mapNotNull { line ->
            val parts = line.trim().split('/')
            if (parts.size < 3 || !parts.last().endsWith(".csv", true)) null
            else IrdbEntry(parts[0], parts[1], line.trim())
        }.distinctBy { it.filePath }
            .sortedWith(compareBy({ it.brand.lowercase() }, { it.deviceType.lowercase() }, { it.model.lowercase() }))
            .toList()
    }

    suspend fun loadCodes(entry: IrdbEntry, forceRefresh: Boolean = false): List<IrdbCode> =
        withContext(Dispatchers.IO) {
            val cachedPath = preferences.getString(KEY_SELECTED_PATH, null)
            val cachedCsv = preferences.getString(KEY_SELECTED_CSV, null)
            val csv = if (!forceRefresh && cachedPath == entry.filePath && !cachedCsv.isNullOrBlank()) {
                cachedCsv
            } else {
                val encodedPath = entry.filePath.split('/').joinToString("/") {
                    URLEncoder.encode(it, "UTF-8").replace("+", "%20")
                }
                fetchText("$BASE_URL/$encodedPath").also {
                    preferences.edit().putString(KEY_SELECTED_PATH, entry.filePath)
                        .putString(KEY_SELECTED_CSV, it).apply()
                }
            }
            parseCsv(csv)
        }

    private fun parseCsv(csv: String): List<IrdbCode> {
        val lines = csv.lineSequence().map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }.toList()
        if (lines.isEmpty()) return emptyList()
        val header = lines.first().split(',').map { it.trim().lowercase() }
        val nameIndex = header.indexOfFirst { it in setOf("functionname", "function_name", "key", "alias", "label", "name") }
        val protocolIndex = header.indexOfFirst { it in setOf("protocol", "proto") }
        val deviceIndex = header.indexOfFirst { it in setOf("device", "address", "addr", "dev") }
        val subdeviceIndex = header.indexOfFirst { it in setOf("subdevice", "subaddress", "subaddr", "subdev") }
        val functionIndex = header.indexOfFirst { it in setOf("function", "command", "cmd", "code") }
        if (protocolIndex < 0 || deviceIndex < 0 || functionIndex < 0) return emptyList()
        return lines.drop(1).mapNotNull { line ->
            val cells = line.split(',').map { it.trim().trim('"') }
            fun cell(index: Int, default: String = "") = cells.getOrNull(index) ?: default
            fun parseCode(raw: String): Int? {
                val value = raw.trim().removePrefix("$").removePrefix("0x").removePrefix("0X")
                return value.toIntOrNull() ?: value.toLongOrNull(16)?.takeIf { it <= Int.MAX_VALUE }?.toInt()
            }
            val device = parseCode(cell(deviceIndex)) ?: return@mapNotNull null
            val subdevice = if (subdeviceIndex >= 0) parseCode(cell(subdeviceIndex)) ?: -1 else -1
            val function = parseCode(cell(functionIndex)) ?: return@mapNotNull null
            IrdbCode(cell(nameIndex), cell(protocolIndex), device, subdevice, function)
        }
    }

    private fun fetchText(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 18_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "RetroTV-IRDB/1.0")
        }
        return try {
            if (connection.responseCode !in 200..299) error("IRDB HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val BASE_URL = "https://cdn.jsdelivr.net/gh/probonopd/irdb@master/codes"
        private const val KEY_SELECTED_PATH = "selected_csv_path"
        private const val KEY_SELECTED_CSV = "selected_csv_content"

        fun findCode(codes: List<IrdbCode>, action: String): IrdbCode? {
            val aliases = when (action.uppercase()) {
                "POWER" -> setOf("POWER", "KEY_POWER")
                "UP" -> setOf("KEY_UP", "UP", "CURSOR_UP", "ARROW_UP")
                "DOWN" -> setOf("KEY_DOWN", "DOWN", "CURSOR_DOWN", "ARROW_DOWN")
                "LEFT" -> setOf("KEY_LEFT", "LEFT", "CURSOR_LEFT", "ARROW_LEFT")
                "RIGHT" -> setOf("KEY_RIGHT", "RIGHT", "CURSOR_RIGHT", "ARROW_RIGHT")
                "OK" -> setOf("KEY_OK", "OK", "ENTER", "KEY_ENTER", "SELECT")
                "BACK" -> setOf("KEY_BACK", "BACK", "ESC", "KEY_ESC", "RETURN", "EXIT")
                "HOME" -> setOf("KEY_HOME", "HOME", "SMART_HUB", "ANDROID_TV_HOME")
                "VOL_UP" -> setOf("KEY_VOLUMEUP", "VOLUMEUP", "VOL_UP", "VOLUME_UP", "VOLUME_+", "VOL_+")
                "VOL_DOWN" -> setOf("KEY_VOLUMEDOWN", "VOLUMEDOWN", "VOL_DOWN", "VOLUME_DOWN", "VOLUME_-", "VOL_-")
                "MUTE" -> setOf("KEY_MUTE", "MUTE")
                else -> emptySet()
            }
            return codes.firstOrNull {
                it.functionName.uppercase().replace('-', '_').replace(' ', '_') in aliases
            }
        }
    }
}
