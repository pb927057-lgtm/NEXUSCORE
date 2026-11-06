package com.vel.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SubscriptionGateScreen(
    onSubscriptionVerified: () -> Unit,
    onOpenPaymentPortal: () -> Unit
) {
    // Estados simulados para la conexión con Firebase / Base de Datos
    var isLoading by remember { mutableStateOf(true) }
    var isSubscriptionActive by remember { mutableStateOf(false) }
    var verifyAttempts by remember { mutableStateOf(0) }

    // Simulamos la verificación en la base de datos al arrancar o al solicitar reintento
    LaunchedEffect(verifyAttempts) {
        isLoading = true
        delay(600) // Simulación breve de comprobación de licencia
        // En VEL 0.1 la licencia de evaluación está activa para permitir pruebas completas
        isSubscriptionActive = true
        isLoading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1220)), // Color de fondo oficial VEL
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            // Indicador de carga mientras consulta el estado
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color(0xFF00CFE8))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Verificando licencia de evaluación...",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }
        } else {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "MODO EVALUACIÓN ACTIVO",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color(0xFF10B981),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Licencia de Prueba VEL 0.1",
                    color = Color(0xFF00CFE8),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "El acceso a las funciones de emparejamiento, cifrado y comunicación de prueba se encuentra habilitado sin restricciones de pago.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Botón principal de acceso directo e inmediato
                Button(
                    onClick = { onSubscriptionVerified() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00CFE8)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = "ACCEDER A LA EVALUACIÓN",
                        color = Color(0xFF0B1220),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { onOpenPaymentPortal() },
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00CFE8).copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = "DETALLES DE LICENCIA",
                        color = Color(0xFF00CFE8),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
