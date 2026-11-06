package com.vel.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vel.app.network.VelCommunicationManager
import com.vel.app.network.VelRelayClient
import com.vel.app.pairing.PairingManager
import com.vel.app.pairing.PairingRepository
import com.vel.app.security.IdentityManager
import com.vel.app.subscription.SubscriptionManager
import com.vel.app.ui.SubscriptionGateScreen
import com.vel.app.ui.navigation.DeviceRole
import com.vel.app.ui.navigation.Screen
import com.vel.app.ui.screens.*
import com.vel.app.ui.theme.VelTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val PREFS_NETWORK = "vel_network_prefs"
        private const val KEY_RELAY_URL = "pref_relay_url"
        // Valor por defecto para pruebas en red local, túnel ngrok/Cloudflare o VPS
        private const val DEFAULT_RELAY_URL = "ws://192.168.1.100:8080"
    }

    private val identityManager by lazy { IdentityManager.getInstance(this) }
    private val pairingRepository by lazy { PairingRepository.getInstance(this) }
    private val pairingManager by lazy { PairingManager(identityManager, pairingRepository) }
    private val subscriptionManager by lazy { SubscriptionManager.getInstance(this) }

    private fun getStoredRelayUrl(): String {
        val prefs = getSharedPreferences(PREFS_NETWORK, Context.MODE_PRIVATE)
        return prefs.getString(KEY_RELAY_URL, DEFAULT_RELAY_URL) ?: DEFAULT_RELAY_URL
    }

    private fun saveRelayUrl(newUrl: String) {
        val cleanUrl = newUrl.trim()
        if (cleanUrl.isNotEmpty()) {
            val prefs = getSharedPreferences(PREFS_NETWORK, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_RELAY_URL, cleanUrl).apply()
            relayClient.updateRelayUrl(cleanUrl)
        }
    }

    // Cliente de transporte Relay con URL configurable desde SharedPreferences
    private val relayClient by lazy {
        VelRelayClient(
            relayUrl = getStoredRelayUrl(),
            identity = identityManager.getOrCreateIdentity()
        )
    }

    private val communicationManager by lazy {
        VelCommunicationManager(
            identity = identityManager.getOrCreateIdentity(),
            relayClient = relayClient,
            repository = pairingRepository
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VelTheme {
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Welcome) }
                var selectedRole by remember { mutableStateOf<DeviceRole?>(null) }
                var showServerConfigDialog by remember { mutableStateOf(false) }
                var currentRelayUrl by remember { mutableStateOf(getStoredRelayUrl()) }

                when (val screen = currentScreen) {
                    is Screen.SubscriptionGate -> {
                        var showPaymentDialog by remember { mutableStateOf(false) }

                        SubscriptionGateScreen(
                            onSubscriptionVerified = {
                                currentScreen = Screen.Welcome
                            },
                            onOpenPaymentPortal = {
                                showPaymentDialog = true
                            }
                        )

                        if (showPaymentDialog) {
                            AlertDialog(
                                onDismissRequest = { showPaymentDialog = false },
                                title = {
                                    Text(
                                        text = "Portal de Suscripción (Modo Pruebas)",
                                        color = Color(0xFF00CFE8),
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "Información de Licencia y Evaluación",
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "En la versión VEL 0.1, el sistema de cobro en línea no está activo. La comunicación de prueba entre dispositivos y la verificación criptográfica no están bloqueadas por pagos.",
                                            color = Color.LightGray,
                                            fontSize = 13.sp
                                        )
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showPaymentDialog = false
                                        currentScreen = Screen.Welcome
                                    }) {
                                        Text("Continuar a VEL", color = Color(0xFF00CFE8), fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showPaymentDialog = false }) {
                                        Text("Cerrar", color = Color.LightGray)
                                    }
                                },
                                containerColor = Color(0xFF111C2E)
                            )
                        }
                    }

                    is Screen.Welcome -> {
                        WelcomeScreen(
                            onStartClick = {
                                currentScreen = Screen.RoleSelection
                            },
                            onCheckLicenseClick = {
                                currentScreen = Screen.SubscriptionGate
                            },
                            onConfigureServerClick = {
                                showServerConfigDialog = true
                            }
                        )
                    }

                    is Screen.RoleSelection -> {
                        RoleSelectionScreen(
                            onSelectRole = { role ->
                                selectedRole = role
                                identityManager.updateRole(role)
                                currentScreen = if (role == DeviceRole.CORE) {
                                    Screen.CoreHome
                                } else {
                                    Screen.TerminalHome
                                }
                            },
                            onBackClick = {
                                currentScreen = Screen.Welcome
                            }
                        )
                    }

                    is Screen.CoreHome -> {
                        CoreHomeScreen(
                            identityManager = identityManager,
                            pairingRepository = pairingRepository,
                            communicationManager = communicationManager,
                            subscriptionManager = subscriptionManager,
                            onNavigateToPairing = {
                                currentScreen = Screen.Pairing(DeviceRole.CORE)
                            },
                            onNavigateToManagement = {
                                currentScreen = Screen.DeviceManagement
                            },
                            onChangeRoleClick = {
                                currentScreen = Screen.RoleSelection
                            }
                        )
                    }

                    is Screen.TerminalHome -> {
                        TerminalHomeScreen(
                            identityManager = identityManager,
                            pairingRepository = pairingRepository,
                            communicationManager = communicationManager,
                            subscriptionManager = subscriptionManager,
                            onNavigateToPairing = {
                                currentScreen = Screen.Pairing(DeviceRole.TERMINAL)
                            },
                            onChangeRoleClick = {
                                currentScreen = Screen.RoleSelection
                            }
                        )
                    }

                    is Screen.Pairing -> {
                        PairingScreen(
                            originRole = screen.originRole,
                            pairingManager = pairingManager,
                            identityManager = identityManager,
                            communicationManager = communicationManager,
                            onContinueClick = {
                                currentScreen = if (screen.originRole == DeviceRole.CORE) {
                                    Screen.CoreHome
                                } else {
                                    Screen.TerminalHome
                                }
                            },
                            onCancelClick = {
                                currentScreen = if (screen.originRole == DeviceRole.CORE) {
                                    Screen.CoreHome
                                } else {
                                    Screen.TerminalHome
                                }
                            }
                        )
                    }

                    is Screen.DeviceManagement -> {
                        DeviceManagementScreen(
                            pairingRepository = pairingRepository,
                            communicationManager = communicationManager,
                            onBackClick = {
                                currentScreen = Screen.CoreHome
                            }
                        )
                    }
                }

                if (showServerConfigDialog) {
                    var tempUrl by remember { mutableStateOf(currentRelayUrl) }
                    AlertDialog(
                        onDismissRequest = { showServerConfigDialog = false },
                        title = {
                            Text(
                                text = "Configurar Servidor Relay",
                                color = Color(0xFF00CFE8),
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Introduce la URL del WebSocket (IP local, túnel o dominio público):",
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                                OutlinedTextField(
                                    value = tempUrl,
                                    onValueChange = { tempUrl = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    placeholder = { Text("ws://192.168.1.100:8080", color = Color.Gray) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF00CFE8),
                                        unfocusedBorderColor = Color(0xFF22324C),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                                Text(
                                    text = "Ejemplos:\n• Red local: ws://192.168.1.50:8080\n• Túnel remoto: wss://tunel.ngrok-free.app",
                                    color = Color.LightGray.copy(alpha = 0.7f),
                                    fontSize = 11.sp
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                saveRelayUrl(tempUrl)
                                currentRelayUrl = tempUrl
                                showServerConfigDialog = false
                            }) {
                                Text("Guardar", color = Color(0xFF00CFE8), fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showServerConfigDialog = false }) {
                                Text("Cancelar", color = Color.LightGray)
                            }
                        },
                        containerColor = Color(0xFF111C2E)
                    )
                }
            }
        }
    }
}
