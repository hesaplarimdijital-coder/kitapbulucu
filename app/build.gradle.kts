plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// The same key signs every APK, so a new build installs over the old one and keeps its data.
// CI decodes it from a repository secret; without it (e.g. a local build) the debug key is used.
val keystorePath: String? = System.getenv("KB_KEYSTORE")?.takeIf { file(it).exists() }

android {
    namespace = "com.hesaplarimdijital.kitapbulucu"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hesaplarimdijital.kitapbulucu"
        minSdk = 26
        targetSdk = 35
        // every CI run gets a higher number, so Android always accepts the update
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("shared") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KB_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KB_KEY_ALIAS") ?: "kitapbulucu"
                keyPassword = System.getenv("KB_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("shared") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "2g"
                val screenshots = layout.buildDirectory.dir("outputs/screenshots")
                it.systemProperty("kitapbulucu.screenshotDir", screenshots.get().asFile.path)
                // declared so a test result restored from the build cache brings the screenshots along
                it.outputs.dir(screenshots).withPropertyName("screenshots")
                val samples = layout.buildDirectory.dir("outputs/samples")
                it.systemProperty("kitapbulucu.sampleDir", samples.get().asFile.path)
                it.outputs.dir(samples).withPropertyName("samples")
                it.testLogging {
                    events("failed")
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                }
            }
        }
    }
}

dependencies {
    implementation(libs.desktop.kit)
    implementation(libs.activity.compose)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.test.core)
    testImplementation(libs.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(platform(libs.compose.bom))
    debugImplementation(libs.compose.ui.test.manifest)
}
