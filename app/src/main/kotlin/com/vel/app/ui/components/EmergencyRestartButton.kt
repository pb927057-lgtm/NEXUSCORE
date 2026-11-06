package com.vel.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Componente de Protocolo de Emergencia para dispositivos VEL.
 * Portado desde la especificación Flutter a Jetpack Compose nativo.
 * Al activarse, muestra un diálogo de doble confirmación crítica y
 * abre el servicio oficial de borrado y reinicio remoto (https://android.com/find).
 */
@Composable
fun EmergencyRestartButton(
    deviceName: String,
    modifier: Modifier = Modifier,
    onEmergencyTriggered: (() -> Unit)? = null
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf<String?>(null) }
    val uriHandler = LocalUriHandler.current

    val emergencyRed = Color(0xFFB91C1C) // Rojo de emergencia
    val criticalRed = Color(0xFF7F1D1D)  // Rojo crítico para botón de ejecución

    Box(modifier = modifier.fillMaxWidth()) {
        Button(
            onClick = { showConfirmDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = emergencyRed),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "EMERGENCIA-DESACTIVAR-REINICIAR",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(
                    text = "⚠️ ALERTA CRÍTICA DE EMERGENCIA",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = "Estás a punto de activar el protocolo de emergencia para el terminal \"$deviceName\". " +
                            "Esto te redirigirá a la consola oficial para revocar accesos y ejecutar el reinicio/borrado de fábrica remoto.",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onEmergencyTriggered?.invoke()
                        try {
                            uriHandler.openUri("https://android.com/find")
                        } catch (e: Exception) {
                            showErrorDialog = "Error al intentar conectar: ${e.message}"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = criticalRed)
                ) {
                    Text("EJECUTAR", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancelar", color = Color.LightGray)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    if (showErrorDialog != null) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = null },
            title = { Text("Atención", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text(showErrorDialog ?: "", color = Color.LightGray) },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = null }) {
                    Text("OK", color = Color(0xFF00CFE8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }
}
