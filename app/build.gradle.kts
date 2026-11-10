plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}


// Salin wallpaper dari docs ke drawable saat build, agar gambar ikut tertanam di APK.
val copySantetWallpaper by tasks.registering {
    val wallpaperSource = rootProject.file("docs/wallpaper.jpg")
    val wallpaperTarget = layout.projectDirectory.file("src/main/res/drawable-nodpi/santethp_wallpaper.jpg")
    inputs.file(wallpaperSource)
    outputs.file(wallpaperTarget)
    doLast {
        if (!wallpaperSource.exists()) {
            throw GradleException("File docs/wallpaper.jpg tidak ditemukan")
        }
        wallpaperTarget.asFile.parentFile.mkdirs()
        wallpaperSource.copyTo(wallpaperTarget.asFile, overwrite = true)
    }
}

tasks.named("preBuild").configure {
    dependsOn(copySantetWallpaper)
}

val appVersionCode = providers.gradleProperty("versionCode").orElse("1").get().toInt()

android {
    namespace = "com.santethp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.santethp"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = "1.0.$appVersionCode"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
            }
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_KEY_ALIAS")
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("io.livekit:livekit-android:2.30.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.3")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")

    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")
}
