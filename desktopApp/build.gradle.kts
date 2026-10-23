import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "cal.cofi.atlas.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            // Nombre del .exe, de la carpeta de instalacion y de la app en
            // "Aplicaciones instaladas". Fue "cal.cofi.atlas" hasta 1.1.0 y
            // "CalcofiLarvas" en 1.2.0.
            packageName = "CalcofiLarvae"
            packageVersion = "1.2.2"
            description = "Larval fish identification with the CalCOFI Atlas 33"

            windows {
                shortcut = true // acceso directo en el escritorio
                menu = true // entrada en el menu de inicio
                // Carpeta del menu de inicio. Sin esto, jpackage usa el vendor
                // ("Unknown").
                menuGroup = "CalcofiLarvae"
                // El que jpackage derivo para "cal.cofi.atlas" (1.0.1, 1.1.0).
                // Fijo para que un MSI nuevo reemplace a esos aunque cambie
                // el nombre: con otro UUID Windows los tomaria como dos apps.
                upgradeUuid = "F5A595C0-EB45-3F70-BB15-6CFBBC9BF082"
            }
        }
    }
}

// ------------------------------------------------------------ instalador MSI

/*
 * :desktopApp:packageMsiInstalador -> build/installer/CalcofiLarvae-<version>.msi
 *
 * El MSI con la interfaz de installer/wix/main.wxs: distingue instalacion de
 * actualizacion, acceso directo opcional y abrir la app al terminar.
 * packageMsi de Compose sigue funcionando pero con la interfaz de jpackage:
 * vacia el --resource-dir en cada corrida, asi que no hay forma de pasarle
 * plantillas WiX. Este task empaqueta la misma app que arma
 * createDistributable, con el jpackage del JDK de Compose y el WiX que
 * Compose descarga (o el de WIX_PATH). Solo Windows.
 */
val distribucion = compose.desktop.application.nativeDistributions
val plantillasWix = layout.projectDirectory.dir("installer/wix")
val salidaInstalador = layout.buildDirectory.dir("installer")
// Ruta corta a proposito: light.exe falla (error 103) si alguna ruta de la
// copia de la app que arma jpackage pasa de 260 caracteres.
val temporalInstalador = layout.buildDirectory.dir("tmp/msi")

val limpiarInstalador by tasks.registering(Delete::class) {
    delete(salidaInstalador, temporalInstalador)
}

val packageMsiInstalador by tasks.registering(Exec::class) {
    group = "compose desktop"
    description = "Builds the Windows MSI with the custom installer UI (installer/wix)."
    dependsOn("createDistributable", ":unzipWix", limpiarInstalador)

    val nombre = requireNotNull(distribucion.packageName)
    val version = requireNotNull(
        distribucion.windows.msiPackageVersion ?: distribucion.windows.packageVersion ?: distribucion.packageVersion,
    )
    val imagenApp = layout.buildDirectory.dir("compose/binaries/main/app/$nombre")
    val icono = distribucion.windows.iconFile.orElse(
        layout.buildDirectory.file(
            "compose/default-resources/${libs.versions.composeMultiplatform.get()}/default-icon-windows.ico",
        ),
    )
    val wix = System.getenv("WIX_PATH") ?: rootProject.layout.buildDirectory.dir("wix311").get().asFile.path

    inputs.dir(imagenApp)
    inputs.dir(plantillasWix)
    outputs.dir(salidaInstalador)

    executable = File(compose.desktop.application.javaHome, "bin/jpackage.exe").path
    environment("PATH", wix + File.pathSeparator + System.getenv("PATH"))
    // main.wxs lee de aca los .bmp (jpackage no los copia a su directorio).
    environment("CALCOFI_WIX_RECURSOS", plantillasWix.asFile.path)
    args(
        "--type", "msi",
        "--app-image", imagenApp.get().asFile.path,
        "--name", nombre,
        "--app-version", version,
        "--description", requireNotNull(distribucion.description),
        "--icon", icono.get().asFile.path,
        "--win-upgrade-uuid", requireNotNull(distribucion.windows.upgradeUuid),
        "--win-dir-chooser",
        "--win-menu",
        "--win-menu-group", requireNotNull(distribucion.windows.menuGroup),
        "--win-shortcut",
        // Condiciona el acceso del escritorio a JP_INSTALL_DESKTOP_SHORTCUT,
        // que main.wxs llena con el checkbox de la confirmacion.
        "--win-shortcut-prompt",
        "--resource-dir", plantillasWix.asFile.path,
        "--temp", temporalInstalador.get().asFile.path,
        "--dest", salidaInstalador.get().asFile.path,
    )
}
