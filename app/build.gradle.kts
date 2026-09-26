import java.util.Properties

plugins {
    id("com.android.application")
}

// Optional CLI signing. Android Studio's "Generate Signed App Bundle" doesn't need this.
// keystore.properties is gitignored; see keystore.properties.example.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.reveille.foldtoggle"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.reveille.foldtoggle"
        minSdk = 34
        targetSdk = 36
        // versionCode = major*1_000_000 + minor*1_000 + patch (monotonic across Play tracks)
        versionCode = 1_000_000
        versionName = "1.0.0"
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = System.getenv("KEYSTORE_PASSWORD") ?: keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }
}
