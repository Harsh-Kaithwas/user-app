plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.harsh.lwinr"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.harsh.lwinr"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isDebuggable = true  // ✅ Logs + debugger enabled
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // Firebase (stable BOM + explicit versions)
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))  // ✅ Stable
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.2.0")  // ✅ Latest

    // AdMob
    implementation("com.google.android.gms:play-services-ads:23.3.0")

    // Material Components (Dialogs ke liye)
    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

}
