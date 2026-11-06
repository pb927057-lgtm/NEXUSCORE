# ==============================================================================
# Reglas de ProGuard / R8 para VEL 0.1
# ==============================================================================

# Preservar clases y modelos de datos serializados
-keepclassmembers class * {
    @org.json.* <fields>;
}

# Modelos del protocolo de red y paquetes cifrados WirePacket
-keep class com.vel.app.network.WirePacket { *; }
-keep class com.vel.app.network.TestMessageExchangeResult { *; }
-keep class com.vel.app.network.RelayConnectionState { *; }

# Modelos de emparejamiento, identidades y confianza
-keep class com.vel.app.pairing.PairedDevice { *; }
-keep class com.vel.app.pairing.PairingSession { *; }
-keep class com.vel.app.security.DeviceIdentity { *; }
-keep class com.vel.app.subscription.SubscriptionProfile { *; }
-keep class com.vel.app.subscription.LinkedDeviceInfo { *; }
-keep class com.vel.app.ui.navigation.DeviceRole { *; }

# OkHttp 3 / 4 (Cliente WebSocket)
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Jetpack Compose runtime
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# Java Cryptography Architecture (JCA) y AndroidKeyStore
-keepclassmembers class * extends java.security.Provider { *; }
-keepclassmembers class * extends java.security.KeyStore { *; }
