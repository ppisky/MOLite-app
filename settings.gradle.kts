pluginManagement {
    val localConfig = java.util.Properties().apply {
        val config = file("local.properties")
        if (config.exists()) config.inputStream().use { load(it) }
    }
    repositories {
        maven(localConfig.getProperty("google.maven.url", "https://dl.google.com/dl/android/maven2/")) {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.google\\.testing.*")
            }
        }
        maven(localConfig.getProperty("central.maven.url", "https://repo.maven.apache.org/maven2/"))
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    val localConfig = java.util.Properties().apply {
        val config = file("local.properties")
        if (config.exists()) config.inputStream().use { load(it) }
    }
    repositories {
        maven(localConfig.getProperty("google.maven.url", "https://dl.google.com/dl/android/maven2/")) {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.google\\.testing.*")
            }
        }
        maven(localConfig.getProperty("central.maven.url", "https://repo.maven.apache.org/maven2/"))
        maven("https://raw.githubusercontent.com/rustls/rustls-platform-verifier/maven-archive/android-release-support/maven/") {
            content { includeGroup("org.rustls") }
        }
    }
}
rootProject.name = "MOLite"
include(":composeApp", ":androidApp")
