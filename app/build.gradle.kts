plugins {
    id("com.android.application")
}

// Release signing is configured outside the repository in the Gradle user home's
// gradle.properties (default ~/.gradle, or GRADLE_USER_HOME if set):
//   GOWITHLINE_STORE_FILE, GOWITHLINE_STORE_PASSWORD, GOWITHLINE_KEY_ALIAS, GOWITHLINE_KEY_PASSWORD
// See README for the keystore backup requirement.
val releaseStoreFile: String? = providers.gradleProperty("GOWITHLINE_STORE_FILE").orNull
val releaseStorePassword: String? = providers.gradleProperty("GOWITHLINE_STORE_PASSWORD").orNull
val releaseKeyAlias: String? = providers.gradleProperty("GOWITHLINE_KEY_ALIAS").orNull
val releaseKeyPassword: String? = providers.gradleProperty("GOWITHLINE_KEY_PASSWORD").orNull
val hasReleaseSigning = !releaseStoreFile.isNullOrBlank()
        && !releaseStorePassword.isNullOrBlank()
        && !releaseKeyAlias.isNullOrBlank()
        && !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.gowithline.mock"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gowithline.mock"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    testImplementation("junit:junit:4.13.2")
}
