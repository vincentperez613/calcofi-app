# Calcofi Larvas

Aplicación de referencia para apoyar la identificación de larvas de peces del atlas CALCOFI. Incluye un flujo de análisis y un catálogo de especies con búsqueda por especie o familia, ficha con datos del atlas y sus imágenes. El catálogo muestra thumbnails en la lista y la imagen fullres al abrir una especie.

## Módulos

- `androidApp`: aplicación para Android.
- `desktopApp`: aplicación de escritorio JVM; en Windows puede generar un instalador MSI.
- `shared`: interfaz Compose, lógica y recursos compartidos, incluidos los datos del atlas y las imágenes en `composeResources/files`.

## Configurar el entorno

1. Instala Android Studio y un JDK 21. El Gradle Wrapper del proyecto usa Gradle 9.1 y selecciona Java 21.
2. En Android Studio, instala Android SDK Platform 36 (compile/target SDK) y las herramientas de plataforma desde **SDK Manager**.
3. Abre la carpeta raíz del proyecto y espera a que termine la sincronización de Gradle. Si Gradle no encuentra el SDK, configura `sdk.dir` en `local.properties` para que apunte a la ubicación del Android SDK en tu equipo. No compartas ese archivo si contiene una ruta local.

Se necesita conexión a Internet la primera vez para descargar Gradle y las dependencias.

## Ejecutar

Desde la raíz, en PowerShell:

```powershell
# Escritorio
.\gradlew.bat :desktopApp:run

# Android: inicia un emulador o conecta un dispositivo con depuración USB
.\gradlew.bat :androidApp:installDebug
```

En macOS/Linux, usa `./gradlew` en lugar de `.\gradlew.bat`.

## Compilar y empaquetar

### APK de prueba

```powershell
.\gradlew.bat :androidApp:assembleDebug
```

El APK se genera en `androidApp/build/outputs/apk/debug/`. Para distribución o Google Play, genera un build `release` y configura una clave de firma; el build `release` no queda firmado para publicación por defecto.

### Escritorio en Windows

```powershell
# Genera una carpeta distribuible con la aplicación y su runtime
.\gradlew.bat :desktopApp:createDistributable

# Genera un instalador MSI (requiere WiX en Windows)
.\gradlew.bat :desktopApp:packageMsi
```

Los artefactos se generan bajo `desktopApp/build/compose/binaries/`. Distribuye el instalador MSI o la carpeta completa creada por `createDistributable`; no copies solamente el `.exe`, porque necesita los archivos y el runtime que lo acompañan.

## Datos e imágenes

El catálogo agrupa las especies por familia y permite buscarlas por nombre o familia. Los índices `thumbnails/indice.json` y `fullres/indice.json`, junto con sus imágenes, están en `shared/src/commonMain/composeResources/files/figures/`. Si se actualizan las imágenes, conserva los IDs de especie y los nombres de archivo indicados por cada índice.
