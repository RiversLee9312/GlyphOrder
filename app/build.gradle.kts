import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val projectLegalAssets = layout.buildDirectory.dir("generated/projectLegalAssets")
android.sourceSets.getByName("main").assets.directories.add(projectLegalAssets.get().asFile.absolutePath)
val syncProjectNotices by tasks.registering(Sync::class) {
    from(rootProject.file("LICENSE"), rootProject.file("NOTICE"), rootProject.file("PRIVACY.md"), rootProject.file("THIRD_PARTY_NOTICES.md"))
    into(projectLegalAssets.map { it.dir("legal/project") })
}

tasks.register("exportRuntimeDependencies") {
    doLast {
        val artifacts = listOf("debugRuntimeClasspath", "releaseRuntimeClasspath").flatMap {
            configurations.getByName(it).resolvedConfiguration.resolvedArtifacts
        }.distinctBy { it.moduleVersion.id.toString() }
        val records = artifacts.map { artifact ->
            val id = artifact.moduleVersion.id
            mapOf("group" to id.group, "name" to id.name, "version" to id.version, "file" to artifact.file.absolutePath)
        }.sortedBy { "${it["group"]}:${it["name"]}" }
        val report = layout.buildDirectory.file("reports/runtime-dependencies.json").get().asFile
        report.parentFile.mkdirs()
        report.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(records)))
    }
}

val verifyLegalCatalog by tasks.registering {
    dependsOn(syncProjectNotices)
    doLast {
        val assets = file("src/main/assets")
        @Suppress("UNCHECKED_CAST")
        val entries = JsonSlurper().parse(assets.resolve("legal/libraries.json")) as List<Map<String, Any>>
        val known = entries.map { it.getValue("coordinate").toString() }.toSet()
        val resolved = listOf("debugRuntimeClasspath", "releaseRuntimeClasspath").flatMap {
            configurations.getByName(it).resolvedConfiguration.resolvedArtifacts.map { artifact -> artifact.moduleVersion.id.toString() }
        }.toSet()
        check(known == resolved) { "Dependency notices are stale. Run :app:exportRuntimeDependencies, then python tools/prepare-legal.py. Missing: ${resolved - known}; stale: ${known - resolved}" }
        entries.forEach { entry ->
            check(assets.resolve(entry.getValue("licenseAsset").toString()).isFile)
            (entry["noticeAsset"] as? String)?.let { check(assets.resolve(it).isFile) }
        }
    }
}
tasks.named("preBuild").configure { dependsOn(verifyLegalCatalog) }
val releasePropertiesFile = rootProject.file("keystore.properties")
val releaseProperties = Properties().apply {
    if (releasePropertiesFile.isFile) releasePropertiesFile.inputStream().use { load(it) }
}
android {
    namespace = "dev.glyphorder.app"
    compileSdk = 36
    ndkVersion = "28.2.13676358"
    testBuildType = providers.gradleProperty("glyphorder.testBuildType").orElse("debug").get()
    defaultConfig {
        applicationId = "dev.glyphorder.app"
        minSdk = 34
        targetSdk = 36
        versionCode = 3
        versionName = "0.1.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild.cmake.arguments += "-DANDROID_STL=c++_static"
    }
    buildFeatures { compose = true; buildConfig = true }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    if (releasePropertiesFile.isFile) {
        signingConfigs.create("release") {
            storeFile = rootProject.file(releaseProperties.getProperty("storeFile"))
            storePassword = releaseProperties.getProperty("storePassword")
            keyAlias = releaseProperties.getProperty("keyAlias")
            keyPassword = releaseProperties.getProperty("keyPassword")
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.5.0-alpha14")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
