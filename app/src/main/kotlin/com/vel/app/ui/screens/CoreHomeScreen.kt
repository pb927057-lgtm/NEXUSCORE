package com.vel.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vel.app.network.RelayConnectionState
import com.vel.app.network.VelCommunicationManager
import com.vel.app.pairing.PairedDevice
import com.vel.app.pairing.PairingRepository
import com.vel.app.security.IdentityManager
import com.vel.app.subscription.SubscriptionManager
import com.vel.app.subscription.UserSubscriptionProfile
import com.vel.app.ui.components.DemoBadge
import com.vel.app.ui.components.VelCard
import com.vel.app.ui.navigation.DeviceRole
import com.vel.app.ui.theme.*

@Composable
fun CoreHomeScreen(
    identityManager: IdentityManager? = null,
    pairingRepository: PairingRepository? = null,
    communicationManager: VelCommunicationManager? = null,
    subscriptionManager: SubscriptionManager? = null,
    onNavigateToPairing: () -> Unit,
    onNavigateToManagement: () -> Unit,
    onChangeRoleClick: () -> Unit
) {
    val identity = remember { identityManager?.getOrCreateIdentity(DeviceRole.CORE) }
    val subscriptionProfile = remember {
        subscriptionManager?.getSubscriptionProfile() ?: UserSubscriptionProfile.fromJson(UserSubscriptionProfile.DEFAULT_JSON)
    }
    val pairedDevices = remember {
        mutableStateOf(pairingRepository?.getPairedDevices() ?: emptyList())
    }

    var connectionState by remember {
        mutableStateOf(communicationManager?.getConnectionState() ?: RelayConnectionState.DISCONNECTED)
    }

    var testMessageFeedback by remember { mutableStateOf<String?>(null) }
    var isSendingTestMessage by remember { mutableStateOf(false) }

    // Conectar al Relay al inicializar
    LaunchedEffect(communicationManager) {
        communicationManager?.setListener(object : VelCommunicationManager.CommunicationListener {
            override fun onConnectionStateChanged(state: RelayConnectionState, details: String?) {
                connectionState = state
            }

            override fun onTestMessageReceived(senderId: String, plaintext: String) {
                testMessageFeedback = "Mensaje recibido y descifrado de '$senderId': '$plaintext'"
            }

            override fun onRevocationCompleted(targetTerminalId: String, isSuccess: Boolean) {
                pairedDevices.value = pairingRepository?.getPairedDevices() ?: emptyList()
            }
        })
    }

    Scaffold(
        containerColor = VelBgMain,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "VEL CORE",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = VelPrimaryCyan
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = VelPrimaryCyan.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, VelPrimaryCyan.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "Principal",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = VelPrimaryCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onChangeRoleClick) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Cambiar rol",
                            tint = VelTextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VelSurface,
                    titleContentColor = VelTextPrimary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                DemoBadge(text = "Etapa 3 • Comunicación remota cifrada, Relay y Revocación")
            }

            // Identidad criptográfica real de este CORE
            item {
                VelCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Autoridad de confianza",
                                style = MaterialTheme.typography.titleMedium,
                                color = VelTextPrimary
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (connectionState) {
                                                RelayConnectionState.AUTHENTICATED -> VelSecurityGreen
                                                RelayConnectionState.CONNECTING, RelayConnectionState.AUTHENTICATING -> VelWarningAmber
                                                RelayConnectionState.REVOKED -> VelWarningAmber
                                                else -> VelTextSecondary
                                            }
                                        )
                                )
                                Text(
                                    text = when (connectionState) {
                                        RelayConnectionState.AUTHENTICATED -> "Relay Conectado"
                                        RelayConnectionState.CONNECTING -> "Conectando..."
                                        RelayConnectionState.AUTHENTICATING -> "Autenticando..."
                                        RelayConnectionState.REVOKED -> "Revocado"
                                        RelayConnectionState.DISCONNECTED -> "Sin conexión"
                                        RelayConnectionState.CONNECTED -> "WebSocket Activo"
                                        RelayConnectionState.ERROR -> "Error de conexión"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (connectionState) {
                                        RelayConnectionState.AUTHENTICATED -> VelSecurityGreen
                                        RelayConnectionState.CONNECTING, RelayConnectionState.AUTHENTICATING -> VelWarningAmber
                                        else -> VelTextSecondary
                                    }
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("ID del CORE:", style = MaterialTheme.typography.labelSmall, color = VelTextSecondary)
                                Text(
                                    identity?.deviceId ?: "VEL-CORE-LOCAL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VelPrimaryCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Huella digital (SHA-256):", style = MaterialTheme.typography.labelSmall, color = VelTextSecondary)
                                Text(
                                    identity?.shortFingerprint ?: "Inicializando...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VelTextPrimary,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            // Perfil de Suscripción y Cuenta
            item {
                VelCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = VelPrimaryCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Cuenta y Licencia",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = VelTextPrimary
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = VelSecurityGreen.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (subscriptionProfile.isActive) "Suscripción activa" else "Inactiva",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VelSecurityGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Usuario:", style = MaterialTheme.typography.labelSmall, color = VelTextSecondary)
                                Text(
                                    subscriptionProfile.email,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VelTextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Expira:", style = MaterialTheme.typography.labelSmall, color = VelTextSecondary)
                                Text(
                                    subscriptionProfile.expiresAt,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VelTextSecondary,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        HorizontalDivider(color = VelBorder)

                        Text(
                            text = "Dispositivos en licencia (${subscriptionProfile.linkedDevices.size}):",
                            style = MaterialTheme.typography.labelSmall,
                            color = VelTextSecondary
                        )

                        subscriptionProfile.linkedDevices.forEach { (devId, devInfo) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(VelSecurityGreen)
                                    )
                                    Text(
                                        text = devId,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VelTextPrimary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Text(
                                    text = "${devInfo.role} • ${devInfo.status}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VelPrimaryCyan
                                )
                            }
                        }
                    }
                }
            }

            // Sección Dispositivos
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dispositivos registrados",
                        style = MaterialTheme.typography.titleMedium,
                        color = VelTextPrimary
                    )
                    Text(
                        text = "${pairedDevices.value.size} dispositivo(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = VelTextSecondary
                    )
                }
            }

            // Lista real de terminales emparejadas o estado vacío
            if (pairedDevices.value.isEmpty()) {
                item {
                    VelCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Devices,
                                contentDescription = null,
                                tint = VelTextSecondary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Sin dispositivos emparejados",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = VelTextPrimary
                            )
                            Text(
                                text = "Pulsa 'Vincular dispositivo' para generar un código temporal e incorporar un Terminal móvil.",
                                style = MaterialTheme.typography.bodySmall,
                                color = VelTextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(pairedDevices.value) { device ->
                    VelCard {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(VelSurfaceElevated),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Smartphone,
                                            contentDescription = null,
                                            tint = if (device.isLocallyRevoked) VelWarningAmber else VelPrimaryCyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = device.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = VelTextPrimary
                                        )
                                        Text(
                                            text = "ID: ${device.id} • SAS: ${device.sasCode}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = VelTextSecondary,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (device.isLocallyRevoked)
                                        VelWarningAmber.copy(alpha = 0.15f)
                                    else
                                        VelSecurityGreen.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = device.statusText,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (device.isLocallyRevoked) VelWarningAmber else VelSecurityGreen
                                    )
                                }
                            }

                            // Botón de prueba de intercambio cifrado si está activo
                            if (!device.isLocallyRevoked) {
                                Button(
                                    onClick = {
                                        if (communicationManager == null) {
                                            testMessageFeedback = "Servicio de comunicación no inicializado."
                                            return@Button
                                        }
                                        isSendingTestMessage = true
                                        testMessageFeedback = "Cifrando y enviando paquete de prueba a ${device.name}..."

                                        // Clave de sesión simétrica obtenida estrictamente de la memoria activa (sin persistencia en disco)
                                        val sessionKey = communicationManager.getSessionKey(device.id)

                                        if (sessionKey == null) {
                                            isSendingTestMessage = false
                                            testMessageFeedback = "Error: No existe una sesión criptográfica activa en memoria para este dispositivo. CORE y MÓVIL deben establecer una nueva sesión."
                                            return@Button
                                        }

                                        communicationManager.sendEncryptedTestMessage(
                                            recipientId = device.id,
                                            sessionKey = sessionKey,
                                            plaintext = "Ping de seguridad desde CORE [${System.currentTimeMillis()}]"
                                        ) { result ->
                                            isSendingTestMessage = false
                                            testMessageFeedback = if (result.isConfirmed) {
                                                "Confirmación verificada de ${device.name} en ${result.roundTripTimeMs} ms."
                                            } else {
                                                "Fallo en la entrega: ${result.errorMessage ?: "Sin respuesta del Terminal."}"
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(38.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = VelSurfaceElevated, contentColor = VelPrimaryCyan),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = null, tint = VelPrimaryCyan, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Enviar mensaje de prueba cifrado", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            if (testMessageFeedback != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = VelSurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, VelPrimaryCyan.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = testMessageFeedback ?: "",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = VelPrimaryCyan
                        )
                    }
                }
            }

            // Botones de acción
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onNavigateToPairing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VelPrimaryCyan,
                            contentColor = VelBgMain
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AddLink, contentDescription = null, tint = VelBgMain)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Vincular dispositivo",
                            fontWeight = FontWeight.Bold,
                            color = VelBgMain
                        )
                    }

                    OutlinedButton(
                        onClick = onNavigateToManagement,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VelTextPrimary),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, VelBorder)
                    ) {
                        Icon(Icons.Default.Devices, contentDescription = null, tint = VelTextPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Administrar dispositivos y revocación remota",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
