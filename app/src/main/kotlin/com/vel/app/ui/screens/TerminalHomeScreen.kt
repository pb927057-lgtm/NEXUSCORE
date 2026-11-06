package com.vel.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.vel.app.pairing.PairingRepository
import com.vel.app.security.IdentityManager
import com.vel.app.subscription.SubscriptionManager
import com.vel.app.subscription.UserSubscriptionProfile
import com.vel.app.ui.components.DemoBadge
import com.vel.app.ui.components.VelCard
import com.vel.app.ui.navigation.DeviceRole
import com.vel.app.ui.theme.*

@Composable
fun TerminalHomeScreen(
    identityManager: IdentityManager? = null,
    pairingRepository: PairingRepository? = null,
    communicationManager: VelCommunicationManager? = null,
    subscriptionManager: SubscriptionManager? = null,
    onNavigateToPairing: () -> Unit,
    onChangeRoleClick: () -> Unit
) {
    val identity = remember { identityManager?.getOrCreateIdentity(DeviceRole.TERMINAL) }
    val subscriptionProfile = remember {
        subscriptionManager?.getSubscriptionProfile() ?: UserSubscriptionProfile.fromJson(UserSubscriptionProfile.DEFAULT_JSON)
    }
    val pairedCoreDevice = remember {
        pairingRepository?.getPairedDevices()?.firstOrNull { it.role == DeviceRole.CORE }
    }

    var connectionState by remember {
        mutableStateOf(communicationManager?.getConnectionState() ?: RelayConnectionState.DISCONNECTED)
    }

    var connectionAttemptState by remember { mutableStateOf<String?>(null) }
    var isCheckingConnection by remember { mutableStateOf(false) }

    LaunchedEffect(communicationManager) {
        communicationManager?.setListener(object : VelCommunicationManager.CommunicationListener {
            override fun onConnectionStateChanged(state: RelayConnectionState, details: String?) {
                connectionState = state
            }

            override fun onTestMessageReceived(senderId: String, plaintext: String) {
                connectionAttemptState = "Mensaje recibido y descifrado de '$senderId': '$plaintext'"
            }

            override fun onRevocationCompleted(targetTerminalId: String, isSuccess: Boolean) {
                // Notificación de revocación
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
                            text = "VEL TERMINAL",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = VelTextPrimary
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = VelSurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, VelBorder)
                        ) {
                            Text(
                                text = "Móvil",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = VelTextSecondary,
                                fontWeight = FontWeight.Medium
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
                DemoBadge(text = "Etapa 3 • Comunicación remota, Relay y Reconexión")
            }

            // Banner si el dispositivo está revocado
            if (connectionState == RelayConnectionState.REVOKED) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = VelSurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, VelWarningAmber)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Block, contentDescription = null, tint = VelWarningAmber, modifier = Modifier.size(28.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Acceso revocado por VEL CORE",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = VelWarningAmber,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tu autorización ha sido invalidada de forma persistente en el servidor Relay. El terminal ha sido desconectado.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VelTextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Identidad criptográfica de este Terminal
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
                                text = "Identidad del Terminal",
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
                                Text("ID de Terminal:", style = MaterialTheme.typography.labelSmall, color = VelTextSecondary)
                                Text(
                                    identity?.deviceId ?: "VEL-TERM-LOCAL",
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

            // Perfil de Suscripción y Licencia
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
                                Text(
                                    text = "$devId (${devInfo.role})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VelTextPrimary,
                                    fontFamily = FontFamily.Monospace
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = VelSurfaceElevated
                                ) {
                                    Text(
                                        text = devInfo.status.uppercase(),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (devInfo.status == "active") VelSecurityGreen else VelWarningAmber
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Identificación del CORE asociado
            item {
                VelCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Dispositivo CORE asociado",
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
                                        .background(if (pairedCoreDevice != null && !pairedCoreDevice.isLocallyRevoked) VelSecurityGreen else VelWarningAmber)
                                )
                                Text(
                                    text = if (pairedCoreDevice != null) pairedCoreDevice.statusText else "Sin emparejar",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (pairedCoreDevice != null && !pairedCoreDevice.isLocallyRevoked) VelSecurityGreen else VelWarningAmber
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(VelSurfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = if (pairedCoreDevice != null) VelSecurityGreen else VelTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = pairedCoreDevice?.name ?: "Ningún CORE vinculado",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = VelTextPrimary
                                )
                                Text(
                                    text = if (pairedCoreDevice != null)
                                        "ID: ${pairedCoreDevice.id} • SAS: ${pairedCoreDevice.sasCode}"
                                    else
                                        "Inicia el emparejamiento para establecer confianza con tu CORE",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VelTextSecondary,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            // Botón de conexión y prueba cifrada
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (connectionState == RelayConnectionState.REVOKED) {
                                connectionAttemptState = "Operación rechazada: Dispositivo revocado."
                                return@Button
                            }
                            if (pairedCoreDevice == null) {
                                connectionAttemptState = "El terminal no está emparejado. Primero debes vincularlo introduciendo el código generado por tu VEL CORE."
                                return@Button
                            }

                            isCheckingConnection = true
                            connectionAttemptState = "Transmitiendo ping de prueba cifrado al CORE..."

                            // Clave de sesión simétrica obtenida estrictamente de la memoria activa (sin persistencia en disco)
                            val sessionKey = communicationManager?.getSessionKey(pairedCoreDevice.id)

                            if (sessionKey == null) {
                                isCheckingConnection = false
                                connectionAttemptState = "Error: No existe una sesión criptográfica activa en memoria con VEL CORE. Reinicie la sesión o vuelva a vincular el dispositivo."
                                return@Button
                            }

                            communicationManager?.sendEncryptedTestMessage(
                                recipientId = pairedCoreDevice.id,
                                sessionKey = sessionKey,
                                plaintext = "Ping desde Terminal [${System.currentTimeMillis()}]"
                            ) { result ->
                                isCheckingConnection = false
                                connectionAttemptState = if (result.isConfirmed) {
                                    "Confirmación recibida de CORE en ${result.roundTripTimeMs} ms. Canal cifrado E2EE operativo."
                                } else {
                                    "Fallo al contactar con CORE: ${result.errorMessage ?: "CORE no responde."}"
                                }
                            }
                        },
                        enabled = connectionState != RelayConnectionState.REVOKED,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VelPrimaryCyan,
                            contentColor = VelBgMain
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, tint = VelBgMain)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Enviar ping cifrado a CORE",
                            fontWeight = FontWeight.Bold,
                            color = VelBgMain
                        )
                    }

                    OutlinedButton(
                        onClick = onNavigateToPairing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VelTextPrimary),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, VelBorder)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = VelTextPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (pairedCoreDevice == null) "Vincular con tu VEL CORE" else "Vincular con otro CORE",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (connectionAttemptState != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = VelSurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, VelBorder)
                        ) {
                            Text(
                                text = connectionAttemptState ?: "",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (connectionState == RelayConnectionState.REVOKED) VelWarningAmber else VelPrimaryCyan
                            )
                        }
                    }
                }
            }

            // Estado del sistema de transporte
            item {
                Text(
                    text = "Estado del sistema de transporte",
                    style = MaterialTheme.typography.titleMedium,
                    color = VelTextPrimary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item {
                VelCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Servidor Relay", style = MaterialTheme.typography.bodyMedium, color = VelTextSecondary)
                            Text(
                                if (connectionState == RelayConnectionState.AUTHENTICATED) "Conectado (WSS)" else connectionState.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (connectionState == RelayConnectionState.AUTHENTICATED) VelSecurityGreen else VelWarningAmber
                            )
                        }
                        HorizontalDivider(color = VelBorder)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Cifrado de extremo a extremo", style = MaterialTheme.typography.bodyMedium, color = VelTextSecondary)
                            Text("AES-256-GCM Activo", style = MaterialTheme.typography.bodyMedium, color = VelSecurityGreen)
                        }
                        HorizontalDivider(color = VelBorder)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Reconexión automática", style = MaterialTheme.typography.bodyMedium, color = VelTextSecondary)
                            Text("Backoff exponencial activo", style = MaterialTheme.typography.bodyMedium, color = VelTextPrimary)
                        }
                    }
                }
            }
        }
    }
}
