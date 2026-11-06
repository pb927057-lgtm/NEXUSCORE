package com.vel.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vel.app.ui.theme.*

@Composable
fun VelLogo(modifier: Modifier = Modifier, size: Dp = 64.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(
                Brush.linearGradient(
                    colors = listOf(VelSurfaceElevated, VelSurface)
                )
            )
            .border(1.dp, VelBorder, RoundedCornerShape(size * 0.28f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.55f)) {
            val w = this.size.width
            val h = this.size.height

            // Escudo exterior
            val shieldPath = Path().apply {
                moveTo(w * 0.5f, 0f)
                lineTo(w, h * 0.25f)
                lineTo(w * 0.85f, h * 0.8f)
                lineTo(w * 0.5f, h)
                lineTo(w * 0.15f, h * 0.8f)
                lineTo(0f, h * 0.25f)
                close()
            }
            drawPath(
                path = shieldPath,
                color = VelPrimaryCyan.copy(alpha = 0.25f),
                style = Stroke(width = 2.5f)
            )

            // Letra V central
            val vPath = Path().apply {
                moveTo(w * 0.28f, h * 0.32f)
                lineTo(w * 0.5f, h * 0.72f)
                lineTo(w * 0.72f, h * 0.32f)
            }
            drawPath(
                path = vPath,
                color = VelPrimaryCyan,
                style = Stroke(width = 4.5f)
            )

            // Punto focal
            drawCircle(
                color = VelPrimaryCyan,
                radius = 3f,
                center = Offset(w * 0.5f, h * 0.72f)
            )
        }
    }
}

@Composable
fun DemoBadge(
    text: String = "Modo demostración",
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = VelSurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, VelBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(VelWarningAmber)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = VelWarningAmber,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun VelCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, VelBorder, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = VelSurface),
        content = content
    )
}
