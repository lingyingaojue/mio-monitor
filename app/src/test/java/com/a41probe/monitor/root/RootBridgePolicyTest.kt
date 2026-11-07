package com.a41probe.monitor.root

import com.a41probe.monitor.data.ROOT_MANAGERS
import com.a41probe.monitor.data.ROOT_SU_CANDIDATES
import com.a41probe.monitor.data.RootState
import com.a41probe.monitor.data.RootStatus
import com.a41probe.monitor.data.parseRootUid
import com.a41probe.monitor.data.rootManagerInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mio 3.14：Root 管理器检测、su 候选与 uid 输出解析的纯 JVM 单测。
 * 真机 su 授权由设备验收覆盖。
 */
class RootBridgePolicyTest {

    @Test
    fun parsesUidVariantsFromDifferentRootManagers() {
        assertTrue(parseRootUid("0"))
        assertTrue(parseRootUid("0\n"))
        assertTrue(parseRootUid("uid=0(root) gid=0(root) groups=0(root)"))
        assertTrue(parseRootUid("uid=0 gid=0 groups=0"))
        assertFalse(parseRootUid("uid=2000(shell) gid=2000(shell)"))
        assertFalse(parseRootUid("Permission denied"))
        assertFalse(parseRootUid("su: not found"))
        assertFalse(parseRootUid(null))
        assertFalse(parseRootUid(""))
    }

    @Test
    fun suCandidatesCoverKnownRootManagers() {
        assertEquals("su", ROOT_SU_CANDIDATES.first())
        assertEquals("候选不能重复", ROOT_SU_CANDIDATES.size, ROOT_SU_CANDIDATES.toSet().size)
        assertTrue(ROOT_SU_CANDIDATES.contains("/data/adb/ksu/bin/su"))
        assertTrue(ROOT_SU_CANDIDATES.contains("/data/adb/ap/bin/su"))
        assertTrue(ROOT_SU_CANDIDATES.contains("/data/adb/magisk/su"))
        assertTrue(ROOT_SU_CANDIDATES.contains("/debug_ramdisk/su"))
    }

    @Test
    fun managerPackagesMapToReadableLabels() {
        assertEquals("KernelSU Next", rootManagerInfo("com.rifsxd.ksunext")?.label)
        assertEquals("KernelSU", rootManagerInfo("me.weishu.kernelsu")?.label)
        assertEquals("Magisk", rootManagerInfo("com.topjohnwu.magisk")?.label)
        assertEquals("APatch", rootManagerInfo("me.bmax.apatch")?.label)
        assertEquals(null, rootManagerInfo("com.example.unknown"))
        val packages = ROOT_MANAGERS.map { it.packageName }.toSet()
        assertEquals("管理器包名不能重复", ROOT_MANAGERS.size, packages.size)
    }

    @Test
    fun statusActiveOnlyWhenStateActive() {
        assertTrue(RootStatus(RootState.ACTIVE, "KernelSU Next", "com.rifsxd.ksunext").isActive)
        listOf(RootState.UNKNOWN, RootState.DENIED, RootState.MANAGER_ONLY, RootState.NOT_FOUND)
            .forEach { assertFalse(RootStatus(it).isActive) }
    }
}
