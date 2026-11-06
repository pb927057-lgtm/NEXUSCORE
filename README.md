# VEL 0.1 — Sistema Móvil Criptográfico E2EE (Android Nativo)

Aplicación nativa de seguridad y soberanía digital para Android desarrollada en **Kotlin** con **Jetpack Compose**, arquitectura de dos dispositivos emparejados de extremo a extremo (**VEL CORE** y **VEL MÓVIL**), cifrado simétrico autenticado **AES-256-GCM**, derivación de claves con **ECDH P-256 + HKDF**, y confirmación visual contra ataques de intermediario (Man-In-The-Middle) mediante código corto **SAS** (Short Authentication String).

---

## Estructura del Proyecto Gradle (Estándar Nativo)

```text
.
├── app/
│   ├── build.gradle.kts       # Configuración del módulo de la aplicación (SDK 35, Jetpack Compose)
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/              # Código fuente Kotlin, recursos Compose y AndroidManifest.xml
│       └── test/              # 10 Pruebas unitarias de seguridad y ciclo de vida de claves
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties  # Gradle 8.10.2
├── .github/
│   └── workflows/
│       └── android.yml        # Flujo de CI/CD para compilación automática en GitHub Actions
├── build.gradle.kts           # Configuración raíz de plugins (AGP 8.7.2, Kotlin 2.0.21)
├── settings.gradle.kts        # Declaración de repositorios (Google, MavenCentral) y módulo :app
├── gradle.properties
├── gradlew                    # Script ejecutable de Gradle para Linux / macOS
├── gradlew.bat                # Script de Gradle para Windows
└── README.md
```

---

## Requisitos de Entorno para Compilación Nativa

- **JDK:** Java Development Kit 17 (Temurin o OpenJDK 17).
- **Android SDK:** API Level 35 (Build-tools 35.0.0).
- **Gradle:** 8.10.2 (incluido vía Gradle Wrapper).
- **Arquitectura de Dispositivos Soportada:** Android 8.0 (API 26) o superior (ARM64, x86_64).

---

## Comandos de Compilación

### 1. Ejecutar Suite de Pruebas Unitarias de Seguridad (10 Tests)
```bash
./gradlew testDebugUnitTest --stacktrace
```

### 2. Generar APK Debug Instalable
```bash
./gradlew assembleDebug --stacktrace
```
*Ubicación del APK generado:*  
`app/build/outputs/apk/debug/app-debug.apk`

---

## Integración Continua (GitHub Actions)

El repositorio incluye el archivo `.github/workflows/android.yml`. Al subir el proyecto a GitHub:
1. GitHub Actions detectará automáticamente el archivo de configuración.
2. Descargará JDK 17 y las dependencias de Android SDK 35.
3. Ejecutará automáticamente `./gradlew testDebugUnitTest` y `./gradlew assembleDebug`.
4. El archivo `app-debug.apk` estará disponible para descarga directa en la pestaña **Actions** -> **Artifacts** de tu repositorio.

---

## Despliegue en Teléfonos Android

1. Descargar o transferir `app-debug.apk` a los dos dispositivos.
2. Habilitar la instalación de aplicaciones de origen desconocido.
3. Instalar el mismo APK en ambos teléfonos:
   - **Teléfono 1:** Seleccionar rol **VEL CORE**.
   - **Teléfono 2:** Seleccionar rol **VEL MÓVIL (Terminal)**.
4. Conectar ambos al mismo servidor Relay ingresando la URL en la pantalla inicial de configuración.
