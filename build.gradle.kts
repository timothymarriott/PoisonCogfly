plugins {
    id("java")
    id("com.gradleup.shadow") version "9.3.0"
}

group = "dev.ambershadow"
version = property("version") as String

repositories {
    mavenCentral()
}

sourceSets {
    main {
        resources.srcDir(layout.buildDirectory.dir("native"))
    }
}

dependencies {
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("com.formdev:flatlaf:3.7")
    implementation("com.formdev:flatlaf-intellij-themes:3.7")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("net.harawata:appdirs:1.5.0")
    implementation("ch.qos.logback:logback-classic:1.5.37")
    implementation("com.formdev:svgSalamander:1.1.4")
    implementation("org.yaml:snakeyaml:2.2")
    implementation("com.google.zxing:core:3.5.3")
}

val compileWinFolderPicker by tasks.register("compileWinFolderPicker") {
    doFirst { mkdir("${layout.buildDirectory.get()}/native") }
    doLast {
        try {
            val s = if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) "g++" else "x86_64-w64-mingw32-g++"
            providers.exec {
                commandLine(
                    s,
                    "-shared",
                    "-o",
                    "${layout.buildDirectory.get()}/native/winfolderpicker.dll",
                    "resources/libs/folderpicker.cpp",
                    "-lole32",
                    "-luuid",
                    "-static",
                    "-static-libgcc",
                    "-static-libstdc++"
                )
            }.result.get()
        } catch (e: Exception) {
            if (System.getProperty("os.name").contains("Windows", ignoreCase = true))
                throw e
        }
    }
}
val compileTinyFileDialogs = tasks.register("compileTinyFileDialogs") {
    doFirst { mkdir("${layout.buildDirectory.get()}/native") }
    doLast {
        try {
            val s = if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) "gcc" else "x86_64-w64-mingw32-gcc"
            providers.exec {
                commandLine(
                    s,
                    "-shared",
                    "-o",
                    "${layout.buildDirectory.get()}/native/wintinyfiledialogs.dll",
                    "resources/libs/tinyfiledialogs.c",
                    "-lole32",
                    "-lcomdlg32",
                    "-lshell32",
                    "-luuid",
                    "-static",
                    "-static-libgcc"
                )
                isIgnoreExitValue = true
            }.result.get()
        } catch (e: Exception) {
            if (System.getProperty("os.name").contains("Windows", ignoreCase = true))
                throw e
        }
    }
}
val publishDownloader = tasks.register("publishDownloader") {
    val projectDir = file("tools/CogflyDownloader")
    val outDir = layout.buildDirectory.dir("native/downloader")
    inputs.property("rid", findProperty("downloaderRid")?.toString() ?: "")
    inputs.files(fileTree("tools") { exclude("**/bin/**", "**/obj/**") })
    outputs.dir(outDir)
    doLast {
        val os = System.getProperty("os.name").lowercase()
        val arch = if (System.getProperty("os.arch").lowercase().let { it == "aarch64" || it == "arm64" }) "arm64" else "x64"
        // -PdownloaderRid=win-x64 cross-builds the downloader for another OS (e.g. building a Windows jar from WSL)
        val rid = findProperty("downloaderRid")?.toString() ?: when {
            os.contains("windows") -> "win-$arch"
            os.contains("mac") -> "osx-$arch"
            else -> "linux-$arch"
        }
        try {
            delete(outDir)
            providers.exec {
                commandLine(
                    "dotnet", "publish", projectDir.absolutePath,
                    "-c", "Release", "-r", rid, "--self-contained",
                    "-p:PublishSingleFile=true",
                    "-p:EnableCompressionInSingleFile=true",
                    "-p:DebugType=None",
                    "-o", outDir.get().asFile.absolutePath
                )
            }.result.get()
        } catch (e: Exception) {
            // Without the .NET SDK the app still builds, it just can't download game versions.
            if (System.getenv("CI") != null)
                throw e
            logger.warn("Skipping CogflyDownloader: ${e.message}")
        }
    }
}

tasks.register("ver") {
    doLast {
        println(project.version)
    }
}
tasks.processResources {
    dependsOn(compileWinFolderPicker, compileTinyFileDialogs, publishDownloader)
}

tasks.shadowJar {
    archiveBaseName.set("PoisonCogfly")
    archiveClassifier.set("")
    archiveVersion.set(version.toString())
    manifest {
        attributes["Main-Class"] = "dev.ambershadow.cogfly.Cogfly"
        attributes["Implementation-Version"] = version
    }
}