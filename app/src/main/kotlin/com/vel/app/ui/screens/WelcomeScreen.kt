package com.vel.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vel.app.ui.components.DemoBadge
import com.vel.app.ui.components.VelLogo
import com.vel.app.ui.theme.*

@Composable
fun WelcomeScreen(
    onStartClick: () -> Unit,
    onCheckLicenseClick: () -> Unit = {},
    onConfigureServerClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VelBgMain)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 40.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DemoBadge(text = "Etapa 1 • Interfaz y Diseño")

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                VelLogo(size = 96.dp)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "VEL",
                        style = MaterialTheme.typography.headlineLarge,
                        color = VelTextPrimary,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Protección inteligente de tu información",
                        style = MaterialTheme.typography.bodyLarge,
                        color = VelTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onStartClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VelPrimaryCyan,
                        contentColor = VelBgMain
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Comenzar",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = VelBgMain
                    )
                }

                TextButton(
                    onClick = onCheckLicenseClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Verificar Licencia y Soberanía",
                        color = VelPrimaryCyan,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }

                TextButton(
                    onClick = onConfigureServerClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Configurar Servidor Relay (WebSocket)",
                        color = VelTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Normal
                    )
                }

                Text(
                    text = "Versión 0.1 • Soberanía y protección criptográfica",
                    style = MaterialTheme.typography.bodySmall,
                    color = VelTextSecondary.copy(alpha = 0.7f)
                )
            }
        }
    }
}
