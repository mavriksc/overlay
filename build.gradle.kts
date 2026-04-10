plugins {
    id("org.jetbrains.compose") version "1.8.2"
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.compose") version "2.2.21"
    kotlin("plugin.serialization") version "2.3.0"
}

val appMainClass = "org.mavriksc.overlay.ApplicationKt"

group = "org.mavriksc"
version = "1.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    // JNA for Windows process enumeration
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
    // Coroutines for background tasks
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:5.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0-RC")
    implementation(compose.desktop.currentOs)
    implementation(compose.components.resources)
    implementation(compose.material3)

}

kotlin {
    jvmToolchain(22)
}

compose.desktop {
    application {
        mainClass = appMainClass
    }
}

tasks.jar {
    // Build a runnable fat JAR on the classpath (no module-info required).
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = appMainClass
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

val fatJar = tasks.named<Jar>("jar")

tasks.register<Exec>("jpackageImage") {
    dependsOn(fatJar)
    group = "distribution"
    description = "Builds a standalone app image with a launcher exe (no installer)."

    val toolchainLauncher = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(22))
    }
    val jpackageExe = toolchainLauncher.map { launcher ->
        launcher.metadata.installationPath.file("bin/jpackage").asFile.absolutePath
    }

    val jarFile = fatJar.get().archiveFile.get().asFile
    val outputDir = layout.buildDirectory.dir("jpackage")
    val appImageDir = outputDir.map { it.dir("overlay") }
    val packageReadme = outputDir.map { it.file("README.txt") }

    doFirst {
        delete(appImageDir)
        commandLine(
            jpackageExe.get(),
            "--type", "app-image",
            "--name", "overlay",
            "--input", jarFile.parentFile.absolutePath,
            "--main-jar", jarFile.name,
            "--main-class", appMainClass,
            "--java-options", "-Doverlay.log.dir=\$LOCALAPPDATA\\lol-overlay\\logs",
            "--dest", outputDir.get().asFile.absolutePath
        )
    }

    doLast {
        packageReadme.get().asFile.writeText(
            """
            Download the zip and decompress it somewhere on your computer.
            Run overlay.exe.
            Logs are here if there are problems:
            %LOCALAPPDATA%\lol-overlay\logs
            Report issues on GitHub:
            https://github.com/mavriksc/overlay/issues
            """.trimIndent()
        )
    }
}

tasks.register<Zip>("jpackageZip") {
    dependsOn("jpackageImage")
    group = "distribution"
    description = "Builds a zip of the overlay app image for distribution."

    val outputDir = layout.buildDirectory.dir("jpackage")
    val appImageDir = outputDir.map { it.dir("overlay") }

    from(appImageDir)
    destinationDirectory.set(outputDir)
    archiveFileName.set("overlay.zip")
}

tasks.test {
    useJUnitPlatform()
}
