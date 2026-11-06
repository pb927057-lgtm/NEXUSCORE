package com.vel.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vel.app.network.VelCommunicationManager
import com.vel.app.pairing.PairedDevice
import com.vel.app.pairing.PairingRepository
import com.vel.app.ui.components.DemoBadge
import com.vel.app.ui.components.EmergencyRestartButton
import com.vel.app.ui.components.VelCard
import com.vel.app.ui.theme.*

@Composable
fun DeviceManagementScreen(
    pairingRepository: PairingRepository? = null,
    communicationManager: VelCommunicationManager? = null,
    onBackClick: () -> Unit
) {
    var devices by remember {
        mutableStateOf(pairingRepository?.getPairedDevices() ?: emptyList())
    }

    var deviceToRevoke by remember { mutableStateOf<PairedDevice?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = VelBgMain,
        topBar = {
            TopAppBar(
                title = { Text("Administrar dispositivos", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Volver",
                            tint = VelTextPrimary
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
                DemoBadge(text = "Etapa 3 • Revocación remota efectiva con persistencia en Relay")
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Dispositivos registrados",
                        style = MaterialTheme.typography.headlineMedium,
                        color = VelTextPrimary
                    )
                    Text(
                        text = "La revocación se registra de forma persistente en el Relay, cerrando de inmediato la conexión activa del Terminal.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VelTextSecondary
                    )
                }
            }

            if (actionMessage != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = VelSurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, VelSecurityGreen.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = actionMessage ?: "",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = VelSecurityGreen
                        )
                    }
                }
            }

            if (devices.isEmpty()) {
                item {
                    VelCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Devices,
                                contentDescription = null,
                                tint = VelTextSecondary,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "No hay dispositivos emparejados",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = VelTextPrimary
                            )
                            Text(
                                text = "Completa el proceso de vinculación para registrar y gestionar terminales de confianza.",
                                style = MaterialTheme.typography.bodySmall,
                                color = VelTextSecondary
                            )
                        }
                    }
                }
            } else {
                items(devices) { device ->
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
                                            text = "Rol: ${device.role.name} • SAS: ${device.sasCode}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = VelTextSecondary,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "Huella: ${device.shortFingerprint}",
                                            style = MaterialTheme.typography.labelSmall,
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

                            if (!device.isLocallyRevoked) {
                                OutlinedButton(
                                    onClick = { deviceToRevoke = device },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VelWarningAmber),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, VelWarningAmber.copy(alpha = 0.5f))
                                ) {
                                    Icon(Icons.Default.Block, contentDescription = null, tint = VelWarningAmber, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Revocar acceso remotamente",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = VelWarningAmber
                                    )
                                }
                            } else {
                                Text(
                                    text = "Dispositivo revocado. El acceso fue invalidado de forma permanente en el Relay.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VelWarningAmber
                                )
                            }

                            // Botón de emergencia para borrado y reinicio remoto oficial (android.com/find)
                            EmergencyRestartButton(
                                deviceName = device.name,
                                onEmergencyTriggered = {
                                    actionMessage = "Protocolo de emergencia activado para '${device.name}'."
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal de confirmación para revocación remota
    if (deviceToRevoke != null) {
        val target = deviceToRevoke!!
        AlertDialog(
            onDismissRequest = { deviceToRevoke = null },
            containerColor = VelSurface,
            titleContentColor = VelTextPrimary,
            textContentColor = VelTextSecondary,
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = VelWarningAmber,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Confirmación de revocación remota",
                    style = MaterialTheme.typography.titleLarge,
                    color = VelTextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "¿Deseas revocar la relación de confianza con '${target.name}'?",
                        style = MaterialTheme.typography.bodyLarge,
                        color = VelTextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Esta acción transmite la orden de revocación al servidor Relay, elimina la autorización de la base de datos persistente y desconecta forzosamente al dispositivo Terminal.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VelTextSecondary
                    )
                    Text(
                        text = "El terminal no podrá volver a conectarse con las credenciales anteriores bajo ninguna circunstancia.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VelWarningAmber
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        communicationManager?.requestRemoteRevocation(target.id)
                        pairingRepository?.revokeDeviceLocally(target.id)
                        devices = pairingRepository?.getPairedDevices() ?: emptyList()
                        actionMessage = "Solicitud de revocación transmitida al Relay para '${target.name}'."
                        deviceToRevoke = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VelWarningAmber)
                ) {
                    Text(
                        text = "Confirmar revocación remota",
                        color = VelBgMain,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { deviceToRevoke = null },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VelTextSecondary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VelBorder)
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}
