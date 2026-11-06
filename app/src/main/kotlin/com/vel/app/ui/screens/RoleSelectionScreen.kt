package com.vel.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vel.app.ui.components.DemoBadge
import com.vel.app.ui.components.VelCard
import com.vel.app.ui.navigation.DeviceRole
import com.vel.app.ui.theme.*

@Composable
fun RoleSelectionScreen(
    onSelectRole: (DeviceRole) -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold(
        containerColor = VelBgMain,
        topBar = {
            TopAppBar(
                title = { Text("Configuración inicial", style = MaterialTheme.typography.titleMedium) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Selecciona el rol de este teléfono",
                    style = MaterialTheme.typography.headlineMedium,
                    color = VelTextPrimary
                )
                Text(
                    text = "Ambos teléfonos ejecutan la misma aplicación. Elige cómo funcionará este dispositivo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VelTextSecondary
                )
            }

            DemoBadge(text = "La selección puede cambiarse en cualquier momento")

            // Opción 1: VEL CORE
            RoleCard(
                title = "VEL CORE",
                subtitle = "Dispositivo principal",
                description = "Tu dispositivo principal. Administra y protege tus dispositivos autorizados.",
                icon = Icons.Default.Shield,
                accentColor = VelPrimaryCyan,
                buttonText = "Configurar como VEL CORE",
                onClick = { onSelectRole(DeviceRole.CORE) }
            )

            // Opción 2: VEL TERMINAL
            RoleCard(
                title = "VEL TERMINAL",
                subtitle = "Dispositivo de uso diario",
                description = "Tu dispositivo de uso diario. Conéctalo con tu VEL CORE.",
                icon = Icons.Default.Smartphone,
                accentColor = VelTextPrimary,
                buttonText = "Configurar como VEL TERMINAL",
                onClick = { onSelectRole(DeviceRole.TERMINAL) }
            )
        }
    }
}

@Composable
private fun RoleCard(
    title: String,
    subtitle: String,
    description: String,
    icon: ImageVector,
    accentColor: androidx.compose.ui.graphics.Color,
    buttonText: String,
    onClick: () -> Unit
) {
    VelCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(VelSurfaceElevated)
                        .border(1.dp, VelBorder, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = VelTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = VelTextSecondary
                    )
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = VelTextSecondary
            )

            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (accentColor == VelPrimaryCyan) VelPrimaryCyan else VelSurfaceElevated,
                    contentColor = if (accentColor == VelPrimaryCyan) VelBgMain else VelTextPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                border = if (accentColor != VelPrimaryCyan) androidx.compose.foundation.BorderStroke(1.dp, VelBorder) else null
            ) {
                Text(
                    text = buttonText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
