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

    doFirst {
        commandLine(
            jpackageExe.get(),
            "--type", "app-image",
            "--name", "Overlay",
            "--input", jarFile.parentFile.absolutePath,
            "--main-jar", jarFile.name,
            "--main-class", appMainClass,
            "--dest", outputDir.get().asFile.absolutePath
        )
    }
}

tasks.test {
    useJUnitPlatform()
}
