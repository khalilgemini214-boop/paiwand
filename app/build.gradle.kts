plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val appVersionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1
val appVersionName = (project.findProperty("appVersionName") as String?) ?: "1.0.0"

val updateUrl = (project.findProperty("updateUrl") as String?) ?: ""
val downloadUrl = (project.findProperty("downloadUrl") as String?) ?: ""
val releasePage = (project.findProperty("releasePage") as String?) ?: ""

val ciKeystore = (project.findProperty("ciKeystore") as String?) ?: ""
val ciKeystorePassword = (project.findProperty("ciKeystorePassword") as String?) ?: ""
val ciKeyAlias = (project.findProperty("ciKeyAlias") as String?) ?: "paiwand"
val ciKeyPassword = (project.findProperty("ciKeyPassword") as String?) ?: ""

android {
    namespace = "com.paiwand.translate"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.paiwand.translate"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "UPDATE_URL", "\"$updateUrl\"")
        buildConfigField("String", "DOWNLOAD_URL", "\"$downloadUrl\"")
        buildConfigField("String", "RELEASE_PAGE", "\"$releasePage\"")
        buildConfigField("String", "DEFAULT_API_BASE", "\"https://api.deepseek.com/v1\"")
        buildConfigField("String", "DEFAULT_MODEL", "\"deepseek-chat\"")
    }

    signingConfigs {
        if (ciKeystore.isNotEmpty()) {
            create("ci") {
                storeFile = file(ciKeystore)
                storePassword = ciKeystorePassword
                keyAlias = ciKeyAlias
                keyPassword = ciKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            if (ciKeystore.isNotEmpty()) signingConfig = signingConfigs.getByName("ci")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (ciKeystore.isNotEmpty()) signingConfig = signingConfigs.getByName("ci")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.mlkit.text.recognition)

    debugImplementation(libs.compose.ui.tooling)
}
