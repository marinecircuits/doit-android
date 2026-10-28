plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

val appVersionName = providers.gradleProperty("doit.versionName").get()

/** Path to the shared marinecircuits release keystore (PKCS#12); the Publish Release workflow sets it. */
val releaseKeystore = providers.environmentVariable("ANDROID_SIGNING_KEYSTORE")

/** major * 10000 + minor * 100 + patch, so 1.2.3 becomes 10203. Android rejects 0, hence the floor of 1. */
fun versionCodeOf(versionName: String): Int {
    val (major, minor, patch) = versionName.substringBefore('-').split('.').map(String::toInt)
    return (major * 10_000 + minor * 100 + patch).coerceAtLeast(1)
}

android {
    namespace = "com.marinecircuits.doit"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.marinecircuits.doit"
        minSdk = 26
        targetSdk = 36
        versionCode = versionCodeOf(appVersionName)
        versionName = appVersionName
    }

    signingConfigs {
        if (releaseKeystore.isPresent) {
            create("release") {
                storeFile = file(releaseKeystore.get())
                storeType = "pkcs12"
                storePassword = providers.environmentVariable("ANDROID_SIGNING_STORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("ANDROID_SIGNING_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("ANDROID_SIGNING_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        debug {
            // The label comes from src/debug/res so a debug build installs next to the release build.
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// Bundle the changelog at build time, after the release workflow has updated it.
val bundleReleaseNotes = tasks.register<Sync>("bundleReleaseNotes") {
    from(rootProject.file("CHANGELOG.md"))
    into(layout.buildDirectory.dir("generated/releaseNotes"))
}
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/releaseNotes"))
tasks.named("preBuild").configure { dependsOn(bundleReleaseNotes) }

// Never produce an unsigned or debug-signed release APK.
val requireReleaseSigning = tasks.register("requireReleaseSigning") {
    val signingConfigured = releaseKeystore.isPresent
    doLast {
        check(signingConfigured) {
            "Release builds must be signed: set ANDROID_SIGNING_KEYSTORE and the other ANDROID_SIGNING_* variables."
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(requireReleaseSigning) }

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)
}
