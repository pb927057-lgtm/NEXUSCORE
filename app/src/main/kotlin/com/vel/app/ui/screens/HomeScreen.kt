package com.vel.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vel.app.R

/**
 * Pantalla de referencia contextual para el uso de strings localizados automáticamente
 * (español, inglés, portugués) con stringResource y estados de suscripción / sincronización.
 */
@Composable
fun HomeScreen(subscriptionStatus: String) {
    Column(modifier = Modifier.padding(16.dp)) {
        // stringResource lee automáticamente del archivo de idioma del teléfono
        Text(text = stringResource(id = R.string.app_name))
        
        Text(text = if (subscriptionStatus == "active") {
            stringResource(id = R.string.status_active)
        } else {
            stringResource(id = R.string.status_expired)
        })
        
        Button(onClick = { /* Lógica de conexión E2EE */ }) {
            Text(text = stringResource(id = R.string.btn_sync))
        }
    }
}
