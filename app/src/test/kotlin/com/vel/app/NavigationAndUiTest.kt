package com.vel.app

import com.vel.app.ui.navigation.DeviceRole
import com.vel.app.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NavigationAndUiTest {

    @Test
    fun testDeviceRolesAreAvailable() {
        val core = DeviceRole.CORE
        val terminal = DeviceRole.TERMINAL

        assertNotNull(core)
        assertNotNull(terminal)
        assertEquals("CORE", core.name)
        assertEquals("TERMINAL", terminal.name)
    }

    @Test
    fun testScreenHierarchy() {
        val welcome: Screen = Screen.Welcome
        val roleSelection: Screen = Screen.RoleSelection
        val coreHome: Screen = Screen.CoreHome
        val terminalHome: Screen = Screen.TerminalHome
        val pairing: Screen = Screen.Pairing(DeviceRole.CORE)
        val management: Screen = Screen.DeviceManagement

        assertNotNull(welcome)
        assertNotNull(roleSelection)
        assertNotNull(coreHome)
        assertNotNull(terminalHome)
        assertNotNull(pairing)
        assertNotNull(management)
        assertEquals(DeviceRole.CORE, (pairing as Screen.Pairing).originRole)
    }
}
