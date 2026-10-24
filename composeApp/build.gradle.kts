import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
}
kotlin {
    android {
        namespace = "me.rerere.rikkahub.ui"
        compileSdk = 37
        buildToolsVersion = "37.0.0"
        minSdk = 26
        androidResources.enable = true
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
    jvm("desktop") {
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.compose.material3)
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        val jvmSharedMain by creating {
            dependsOn(commonMain.get())
        }
        androidMain.get().dependsOn(jvmSharedMain)
        val desktopMain by getting {
            dependsOn(jvmSharedMain)
            dependencies { implementation(compose.desktop.currentOs) }
        }
        val desktopTest by getting {
            dependencies {
                implementation(compose.desktop.uiTestJUnit4)
                implementation(compose.desktop.currentOs)
            }
        }
    }
    compilerOptions {
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    }
}
compose.resources { packageOfResClass = "me.rerere.rikkahub.resources" }
compose.desktop {
    application {
        mainClass = "me.rerere.rikkahub.MainKt"
        nativeDistributions {
            appResourcesRootDir.set(rootProject.layout.buildDirectory.dir("native/packaged"))
            targetFormats(TargetFormat.Msi)
            packageName = "MOLite"
            packageVersion = "0.1.0"
            copyright = "RikkaHub contributors — AGPL-3.0"
            windows { iconFile.set(rootProject.file("native/molite.ico")) }
        }
    }
}
val buildDesktopCore by tasks.registering(Exec::class) {
    workingDir(rootDir)
    commandLine("python", "tools/build_native.py", "--platform", "desktop")
}
tasks.matching { it.name in listOf("prepareAppResources", "createDistributable", "packageMsi", "runDistributable") }.configureEach { dependsOn(buildDesktopCore) }
tasks.withType<JavaExec>().configureEach {
    dependsOn(buildDesktopCore)
    systemProperty("molite.native.dir", rootProject.layout.buildDirectory.dir("native/desktop").get().asFile.absolutePath)
}
tasks.withType<Test>().configureEach {
    dependsOn(buildDesktopCore)
    systemProperty("molite.native.dir", rootProject.layout.buildDirectory.dir("native/desktop").get().asFile.absolutePath)
    systemProperty("java.awt.headless", "true")
    systemProperty("skiko.renderApi", "SOFTWARE")
    systemProperty("ui.screenshot.dir", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
}
