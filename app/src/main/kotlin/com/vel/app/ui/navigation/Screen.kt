package com.vel.app.ui.navigation

enum class DeviceRole {
    CORE,
    TERMINAL
}

sealed class Screen {
    object SubscriptionGate : Screen()
    object Welcome : Screen()
    object RoleSelection : Screen()
    object CoreHome : Screen()
    object TerminalHome : Screen()
    data class Pairing(val originRole: DeviceRole) : Screen()
    object DeviceManagement : Screen()
}
