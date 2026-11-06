package com.a41probe.monitor.toolbox

import com.a41probe.monitor.data.toolbox.FrameInterpolationValue
import com.a41probe.monitor.data.toolbox.INTERPOLATION_PRESETS
import com.a41probe.monitor.data.toolbox.REFRESH_TARGETS
import com.a41probe.monitor.data.toolbox.ToolboxFeature
import com.a41probe.monitor.data.toolbox.ToolboxKey
import com.a41probe.monitor.data.toolbox.ToolboxNamespace
import com.a41probe.monitor.data.toolbox.deleteCommand
import com.a41probe.monitor.data.toolbox.deleteScript
import com.a41probe.monitor.data.toolbox.insertCommand
import com.a41probe.monitor.data.toolbox.isOriginTuningSupported
import com.a41probe.monitor.data.toolbox.isValidKeyName
import com.a41probe.monitor.data.toolbox.isValidSettingValue
import com.a41probe.monitor.data.toolbox.keysOf
import com.a41probe.monitor.data.toolbox.parseContentQueryLine
import com.a41probe.monitor.data.toolbox.parseSettingsGet
import com.a41probe.monitor.data.toolbox.readKeyId
import com.a41probe.monitor.data.toolbox.refreshValueOf
import com.a41probe.monitor.data.toolbox.resolveFeatureOn
import com.a41probe.monitor.data.toolbox.valuesToWrite
import com.a41probe.monitor.data.toolbox.writeScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mio 3.5 OriginOS 调校的纯逻辑单测（命令/格式/状态/设备门槛）。 */
class OriginSettingsTest {

    private val touchKey = ToolboxKey(ToolboxNamespace.GLOBAL, "game_memc_request_touch_rate")
    private val interpKey = ToolboxKey(ToolboxNamespace.SYSTEM, "gamecube_frame_interpolation")

    @Test
    fun deleteAndInsertCommandsMatchTestedAdbForm() {
        assertEquals(
            "settings delete global game_memc_request_touch_rate",
            deleteCommand(touchKey),
        )
        assertEquals(
            "content insert --uri content://settings/system --bind name:s:gamecube_frame_interpolation" +
                " --bind value:s:1:1:0:30:120",
            insertCommand(interpKey, "1:1:0:30:120"),
        )
        val script = writeScript(interpKey, "1:1:0:30:120")
        assertTrue(script.startsWith("settings delete system gamecube_frame_interpolation ; "))
        assertTrue(script.contains("content insert"))
        assertEquals("settings delete global game_memc_request_touch_rate", deleteScript(touchKey))
    }

    @Test
    fun invalidKeysAndValuesAreRejected() {
        assertFalse(isValidKeyName("touch_rate; rm -rf /"))
        assertFalse(isValidKeyName(""))
        assertFalse(isValidSettingValue("1; reboot"))
        assertFalse(isValidSettingValue(""))
        assertTrue(isValidKeyName("game_memc_request_touch_rate"))
        assertTrue(isValidSettingValue("1:1:0:30:144"))
        assertThrows(IllegalArgumentException::class.java) {
            deleteCommand(ToolboxKey(ToolboxNamespace.GLOBAL, "bad key"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            insertCommand(touchKey, "1; reboot")
        }
    }

    @Test
    fun interpolationValueRoundTrips() {
        val value = FrameInterpolationValue(fromFps = 30, toFps = 144)
        assertEquals("1:1:0:30:144", value.format())
        val parsed = FrameInterpolationValue.parse("1:1:0:30:144")
        assertEquals(30, parsed?.fromFps)
        assertEquals(144, parsed?.toFps)
        assertTrue(parsed?.enabled == true)
        // 关闭值（系统默认）
        val disabled = FrameInterpolationValue.parse("0:-1:0:0:0")
        assertEquals(false, disabled?.enabled)
        // 非法值
        assertNull(FrameInterpolationValue.parse(null))
        assertNull(FrameInterpolationValue.parse("garbage"))
        assertNull(FrameInterpolationValue.parse("1:1:0:0:120"))
        assertNull(FrameInterpolationValue.parse("1:1:0:144:30"))
    }

    @Test
    fun presetsAndRefreshTargetsAreValid() {
        REFRESH_TARGETS.forEach { hz ->
            assertEquals("%.1f".format(hz.toFloat()), refreshValueOf(hz))
            assertTrue(isValidSettingValue(refreshValueOf(hz)))
        }
        INTERPOLATION_PRESETS.forEach { preset ->
            assertTrue(preset.fromFps in listOf(30, 60))
            assertTrue(preset.toFps in listOf(120, 144))
            assertTrue(preset.fromFps < preset.toFps)
            assertTrue(isValidSettingValue(FrameInterpolationValue(preset.fromFps, preset.toFps).format()))
        }
    }

    @Test
    fun featureOnStateIsResolvedFromRawValues() {
        assertTrue(
            resolveFeatureOn(
                ToolboxFeature.TOUCH_BOOST,
                mapOf("global.game_memc_request_touch_rate" to "1"),
            ),
        )
        assertFalse(
            resolveFeatureOn(
                ToolboxFeature.TOUCH_BOOST,
                mapOf("global.game_memc_request_touch_rate" to "0"),
            ),
        )
        assertTrue(
            resolveFeatureOn(
                ToolboxFeature.FRAME_INTERPOLATION,
                mapOf("system.gamecube_frame_interpolation" to "1:1:0:30:120"),
            ),
        )
        assertFalse(
            resolveFeatureOn(
                ToolboxFeature.FRAME_INTERPOLATION,
                mapOf("system.gamecube_frame_interpolation" to "0:-1:0:0:0"),
            ),
        )
        assertTrue(
            resolveFeatureOn(
                ToolboxFeature.REFRESH_LOCK,
                mapOf("system.min_refresh_rate" to "144.0", "system.peak_refresh_rate" to "144.0"),
            ),
        )
        assertFalse(
            resolveFeatureOn(
                ToolboxFeature.REFRESH_LOCK,
                mapOf("system.min_refresh_rate" to null, "system.peak_refresh_rate" to "144.0"),
            ),
        )
        assertTrue(
            resolveFeatureOn(
                ToolboxFeature.VISUAL_ENHANCE,
                mapOf("system.gamecube_hawkeye_effective" to "1"),
            ),
        )
    }

    @Test
    fun writeValuesAreDerivedFromConfig() {
        val interp = FrameInterpolationValue(30, 120)
        assertEquals(
            mapOf(touchKey to "1"),
            valuesToWrite(ToolboxFeature.TOUCH_BOOST, interp, refreshHz = 144),
        )
        assertEquals(
            mapOf(interpKey to "1:1:0:30:120"),
            valuesToWrite(ToolboxFeature.FRAME_INTERPOLATION, interp, refreshHz = 144),
        )
        val refresh = valuesToWrite(ToolboxFeature.REFRESH_LOCK, interp, refreshHz = 144)
        assertEquals(2, refresh.size)
        assertTrue(refresh.values.all { it == "144.0" })
        assertEquals(
            mapOf(ToolboxKey(ToolboxNamespace.SYSTEM, "gamecube_hawkeye_effective") to "1"),
            valuesToWrite(ToolboxFeature.VISUAL_ENHANCE, interp, refreshHz = 144),
        )
    }

    @Test
    fun parsersHandleAdbOutput() {
        assertNull(parseSettingsGet(null))
        assertNull(parseSettingsGet("null"))
        assertNull(parseSettingsGet(""))
        assertEquals("144.0", parseSettingsGet("144.0\n"))
        assertEquals(
            "0",
            parseContentQueryLine(
                "Row: 82 name=game_memc_request_touch_rate, value=0",
                "game_memc_request_touch_rate",
            ),
        )
        assertNull(parseContentQueryLine("Row: 1 name=other, value=1", "game_memc_request_touch_rate"))
    }

    @Test
    fun keyIdsAndDeviceGate() {
        assertEquals("global.game_memc_request_touch_rate", readKeyId(touchKey))
        assertEquals(1, keysOf(ToolboxFeature.TOUCH_BOOST).size)
        assertEquals(2, keysOf(ToolboxFeature.REFRESH_LOCK).size)
        assertTrue(isOriginTuningSupported("vivo", "vivo", "PD2463E_A_17.2.9.7.W10"))
        assertTrue(isOriginTuningSupported("iQOO", "vivo", ""))
        assertFalse(isOriginTuningSupported("samsung", "samsung", "UP1A.231005.007"))
        assertFalse(isOriginTuningSupported("Google", "google", "sdk_gphone64_x86_64"))
    }
}
