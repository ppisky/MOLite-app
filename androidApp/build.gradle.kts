plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "me.rerere.rikkahub.preview"
    compileSdk = 37
    buildToolsVersion = "37.0.0"
    defaultConfig {
        applicationId = "io.github.ppisky.molite"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "me.rerere.rikkahub.preview.CoreSmokeInstrumentation"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    buildFeatures { compose = true }
    sourceSets.getByName("main").jniLibs.srcDir(rootProject.layout.buildDirectory.dir("native/android").get().asFile)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
val buildAndroidCore by tasks.registering(Exec::class) {
    workingDir(rootDir)
    commandLine("python", "tools/build_native.py", "--platform", "android")
}
tasks.named("preBuild") { dependsOn(buildAndroidCore) }
dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
    implementation("org.rustls:rustls-platform-verifier:0.2.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}
