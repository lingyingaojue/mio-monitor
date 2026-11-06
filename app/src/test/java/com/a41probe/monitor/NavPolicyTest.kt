package com.a41probe.monitor

import org.junit.Assert.assertEquals
import org.junit.Test

/** 导航策略单测：5 主 tab + 页面返回栈规则。 */
class NavPolicyTest {

    @Test
    fun fiveMainTabsInOrder() {
        assertEquals(
            listOf(Screen.DASH, Screen.CPU, Screen.GPU, Screen.BAT, Screen.THERM),
            MAIN_TAB_SCREENS,
        )
        assertEquals(5, MAIN_TAB_SCREENS.size)
    }

    @Test
    fun subPagesBackToSettings() {
        for (page in listOf(Screen.SENS, Screen.AGENT, Screen.MONITOR)) {
            assertEquals(Screen.SET, backTarget(page, Screen.DASH))
        }
    }

    @Test
    fun settingsBackToOriginTab() {
        assertEquals(Screen.CPU, backTarget(Screen.SET, Screen.CPU))
        assertEquals(Screen.THERM, backTarget(Screen.SET, Screen.THERM))
    }

    @Test
    fun deviceDetailBackToMonitor() {
        assertEquals(Screen.MONITOR, backTarget(Screen.RDETAIL, Screen.CPU))
    }
}
