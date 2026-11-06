package com.vel.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vel.app.pairing.PairingManager
import com.vel.app.pairing.PairingProcessResult
import com.vel.app.pairing.PairingSession
import com.vel.app.pairing.PairingState
import com.vel.app.security.IdentityManager
import com.vel.app.ui.components.DemoBadge
import com.vel.app.ui.components.VelCard
import com.vel.app.ui.navigation.DeviceRole
import com.vel.app.ui.theme.*

import com.vel.app.network.RelayConnectionState
import com.vel.app.network.VelCommunicationManager
import java.util.UUID

@Composable
fun PairingScreen(
    originRole: DeviceRole,
    pairingManager: PairingManager? = null,
    identityManager: IdentityManager? = null,
    communicationManager: VelCommunicationManager? = null,
    onContinueClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    val identity = remember { identityManager?.getOrCreateIdentity(originRole) }

    // Sesión de emparejamiento para CORE
    var coreSession by remember {
        mutableStateOf<PairingSession?>(
            if (originRole == DeviceRole.CORE && pairingManager != null) {
                pairingManager.createPairingSession(DeviceRole.CORE)
            } else null
        )
    }

    var temporalCodeInput by remember {
        mutableStateOf(coreSession?.ephemeralCode ?: "VEL-")
    }

    // Estados de verificación
    var pairingState by remember { mutableStateOf<PairingState>(PairingState.Unpaired) }
    var sasCodeDerived by remember { mutableStateOf<String?>(null) }
    var peerFingerprintDetected by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var confirmedDeviceName by remember { mutableStateOf("") }
    var remotePeerDeviceId by remember { mutableStateOf<String?>(null) }
    var remotePeerPublicKey by remember { mutableStateOf<String?>(null) }
    var isWaitingPeer by remember { mutableStateOf(false) }

    // Escucha y registro en el servidor Relay para emparejamiento entre dispositivos
    DisposableEffect(communicationManager) {
        if (communicationManager == null) return@DisposableEffect onDispose {}

        val commListener = object : VelCommunicationManager.CommunicationListener {
            override fun onConnectionStateChanged(state: RelayConnectionState, details: String?) {}
            override fun onTestMessageReceived(senderId: String, plaintext: String) {}
            override fun onRevocationCompleted(targetTerminalId: String, isSuccess: Boolean) {}

            override fun onPairingOfferRegistered(code: String) {
                isWaitingPeer = true
            }

            override fun onPairingPeerReady(
                code: String,
                peerDeviceId: String,
                peerRole: String,
                peerFingerprint: String,
                sasCode: String,
                peerPublicKeyBase64: String
            ) {
                isWaitingPeer = false
                sasCodeDerived = sasCode
                peerFingerprintDetected = peerFingerprint
                remotePeerDeviceId = peerDeviceId
                remotePeerPublicKey = peerPublicKeyBase64

                val dummySession = coreSession ?: PairingSession(
                    sessionId = UUID.randomUUID().toString(),
                    ephemeralCode = code,
                    originRole = if (peerRole == "CORE") DeviceRole.CORE else DeviceRole.TERMINAL,
                    initiatorDeviceId = peerDeviceId,
                    initiatorFingerprint = peerFingerprint,
                    initiatorPublicKeyBase64 = peerPublicKeyBase64,
                    createdAt = System.currentTimeMillis(),
                    expiresAt = System.currentTimeMillis() + PairingManager.SESSION_TIMEOUT_MS
                )

                pairingState = PairingState.AwaitingConfirmation(
                    session = dummySession,
                    sasCode = sasCode,
                    peerFingerprint = peerFingerprint,
                    peerRole = if (peerRole == "CORE") DeviceRole.CORE else DeviceRole.TERMINAL
                )
            }

            override fun onPairingError(error: String, message: String) {
                isWaitingPeer = false
                errorMessage = message
                pairingState = PairingState.Error(message)
            }
        }

        communicationManager.setListener(commListener)

        // Si es CORE, anunciar oferta de emparejamiento en el Relay
        if (originRole == DeviceRole.CORE && coreSession != null) {
            communicationManager.sendPairingOffer(coreSession!!.ephemeralCode, coreSession!!.expiresAt)
            isWaitingPeer = true
        }

        onDispose {
            communicationManager.setListener(null)
        }
    }

    Scaffold(
        containerColor = VelBgMain,
        topBar = {
            TopAppBar(
                title = { Text("Vincular dispositivo", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onCancelClick) {
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            DemoBadge(text = "Etapa 2 • Seguridad básica ECDH P-256 + SAS")

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (originRole == DeviceRole.CORE)
                        "Código de emparejamiento CORE"
                    else
                        "Vincular con tu VEL CORE",
                    style = MaterialTheme.typography.headlineMedium,
                    color = VelTextPrimary
                )
                Text(
                    text = if (originRole == DeviceRole.CORE)
                        "Introduce este código temporal en tu VEL Terminal. El código expira en 5 minutos y no puede reutilizarse."
                    else
                        "Ingresa el código temporal mostrado en tu VEL CORE para negociar el secreto compartido e intercambiar identidades.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VelTextSecondary
                )
            }

            // Información de identidad de este dispositivo
            if (identity != null) {
                VelCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Identificador local", style = MaterialTheme.typography.labelSmall, color = VelTextSecondary)
                            Text(identity.deviceId, style = MaterialTheme.typography.labelSmall, color = VelPrimaryCyan, fontWeight = FontWeight.SemiBold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Huella digital (SHA-256)", style = MaterialTheme.typography.labelSmall, color = VelTextSecondary)
                            Text(identity.shortFingerprint, style = MaterialTheme.typography.labelSmall, color = VelTextPrimary, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            // Vista para CORE: Muestra el código temporal generado
            if (originRole == DeviceRole.CORE) {
                val session = coreSession
                VelCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Código temporal de un solo uso",
                            style = MaterialTheme.typography.titleMedium,
                            color = VelTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Código resaltado
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(VelSurfaceElevated)
                                .border(1.5.dp, VelPrimaryCyan.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 24.dp, vertical = 14.dp)
                        ) {
                            Text(
                                text = session?.ephemeralCode ?: "VEL-DEMO",
                                style = MaterialTheme.typography.headlineLarge,
                                color = VelPrimaryCyan,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 2.sp
                            )
                        }

                        Text(
                            text = "Válido por 5 minutos • Protección contra repetición activada",
                            style = MaterialTheme.typography.bodySmall,
                            color = VelTextSecondary,
                            textAlign = TextAlign.Center
                        )

                        if (isWaitingPeer && sasCodeDerived == null) {
                            Text(
                                text = "Oferta publicada en el Relay. Esperando a que el Terminal ingrese el código...",
                                style = MaterialTheme.typography.bodySmall,
                                color = VelPrimaryCyan,
                                textAlign = TextAlign.Center
                            )
                        }

                        // Espacio reservado para cámara/QR futuro
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(VelBgMain)
                                .border(1.dp, VelBorder, RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.QrCode, contentDescription = null, tint = VelTextSecondary, modifier = Modifier.size(20.dp))
                                Text(
                                    text = "Lectura QR reservada para mejora con cámara física",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VelTextSecondary
                                )
                            }
                        }
                    }
                }
            } else {
                // Vista para TERMINAL: Ingreso y validación de código
                VelCard {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Ingresar código temporal de CORE",
                            style = MaterialTheme.typography.titleMedium,
                            color = VelTextPrimary
                        )

                        OutlinedTextField(
                            value = temporalCodeInput,
                            onValueChange = {
                                temporalCodeInput = it
                                errorMessage = null
                            },
                            label = { Text("Código temporal (ej. VEL-1234)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VelPrimaryCyan,
                                unfocusedBorderColor = VelBorder,
                                focusedTextColor = VelTextPrimary,
                                unfocusedTextColor = VelTextPrimary
                            )
                        )

                        Button(
                            onClick = {
                                errorMessage = null
                                val localResult = pairingManager?.processPairingCode(
                                    inputCode = temporalCodeInput,
                                    receiverRole = DeviceRole.TERMINAL
                                )
                                if (localResult is PairingProcessResult.SuccessAwaitingConfirmation) {
                                    sasCodeDerived = localResult.sasCode
                                    peerFingerprintDetected = localResult.peerFingerprint
                                    pairingState = PairingState.AwaitingConfirmation(
                                        session = localResult.session,
                                        sasCode = localResult.sasCode,
                                        peerFingerprint = localResult.peerFingerprint,
                                        peerRole = localResult.peerRole
                                    )
                                } else {
                                    // Búsqueda de oferta en el servidor de relevo (WSS Relay)
                                    if (communicationManager != null) {
                                        isWaitingPeer = true
                                        val cleanCode = temporalCodeInput.trim().uppercase()
                                        val sent = communicationManager.sendPairingClaim(cleanCode)
                                        if (!sent) {
                                            isWaitingPeer = false
                                            errorMessage = "Sin conexión activa con el servidor de relevo. Verifique la URL de Relay."
                                        }
                                    } else {
                                        val err = (localResult as? PairingProcessResult.Error)?.message
                                            ?: "No se pudo procesar el código de emparejamiento."
                                        errorMessage = err
                                        pairingState = PairingState.Error(err)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = VelPrimaryCyan, contentColor = VelBgMain)
                        ) {
                            Text(
                                text = if (isWaitingPeer) "Buscando en Relay..." else "Negociar acuerdo de claves (ECDH)",
                                fontWeight = FontWeight.Bold,
                                color = VelBgMain
                            )
                        }
                    }
                }
            }

            // Mensaje de Error si lo hay
            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = VelSurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, VelWarningAmber.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = VelWarningAmber)
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = VelWarningAmber
                        )
                    }
                }
            }

            // Panel de confirmación SAS (Short Authentication String)
            if (sasCodeDerived != null && pairingState is PairingState.AwaitingConfirmation) {
                val awaitState = pairingState as PairingState.AwaitingConfirmation
                VelCard {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Código de verificación SAS (6 dígitos)",
                            style = MaterialTheme.typography.titleMedium,
                            color = VelTextPrimary,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Verifica que este número coincida con la pantalla de tu otro dispositivo:",
                            style = MaterialTheme.typography.bodySmall,
                            color = VelTextSecondary,
                            textAlign = TextAlign.Center
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(VelBgMain)
                                .border(1.5.dp, VelSecurityGreen, RoundedCornerShape(12.dp))
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = sasCodeDerived ?: "000000",
                                style = MaterialTheme.typography.headlineLarge,
                                color = VelSecurityGreen,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 4.sp
                            )
                        }

                        Text(
                            text = "Huella remota de confianza:\n${peerFingerprintDetected?.take(18) ?: ""}...",
                            style = MaterialTheme.typography.labelSmall,
                            color = VelTextSecondary,
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily.Monospace
                        )

                        Button(
                            onClick = {
                                if (pairingManager != null) {
                                    val paired = if (remotePeerDeviceId != null && remotePeerPublicKey != null) {
                                        pairingManager.confirmPairingWithPeer(
                                            peerDeviceId = remotePeerDeviceId!!,
                                            peerRole = if (originRole == DeviceRole.CORE) DeviceRole.TERMINAL else DeviceRole.CORE,
                                            peerFingerprint = peerFingerprintDetected ?: "",
                                            peerPublicKeyBase64 = remotePeerPublicKey!!,
                                            sasCode = awaitState.sasCode,
                                            customDeviceName = if (confirmedDeviceName.isNotBlank()) confirmedDeviceName else null
                                        )
                                    } else {
                                        pairingManager.confirmPairing(
                                            session = awaitState.session,
                                            sasCode = awaitState.sasCode,
                                            customDeviceName = if (confirmedDeviceName.isNotBlank()) confirmedDeviceName else null
                                        )
                                    }
                                    pairingState = PairingState.VerifiedPaired(paired)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VelSecurityGreen, contentColor = VelBgMain)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = VelBgMain)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Confirmar emparejamiento", fontWeight = FontWeight.Bold, color = VelBgMain)
                        }
                    }
                }
            }

            // Estado de éxito verificado
            if (pairingState is PairingState.VerifiedPaired) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = VelSurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, VelSecurityGreen)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = VelSecurityGreen)
                            Text(
                                text = "¡Emparejado y verificado con éxito!",
                                style = MaterialTheme.typography.titleMedium,
                                color = VelSecurityGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Se ha establecido una relación de confianza criptográfica mutua protegida con clave de sesión derivada vía HKDF.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VelTextPrimary
                        )
                        Text(
                            text = "Nota técnica: La relación de confianza ha quedado guardada localmente. La sincronización automática de mensajes a través de internet requerirá el servidor de relevo de la Etapa 3.",
                            style = MaterialTheme.typography.labelSmall,
                            color = VelTextSecondary
                        )
                    }
                }
            }

            // Botones de acción inferiores
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onContinueClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VelPrimaryCyan,
                        contentColor = VelBgMain
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (pairingState is PairingState.VerifiedPaired) "Finalizar" else "Continuar",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = VelBgMain
                    )
                }

                OutlinedButton(
                    onClick = onCancelClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VelTextSecondary),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VelBorder)
                ) {
                    Text("Volver")
                }
            }
        }
    }
}
