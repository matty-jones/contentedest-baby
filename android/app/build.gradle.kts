plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")      // Kapt plugin moved before Hilt
    id("com.google.dagger.hilt.android") // Hilt plugin
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.contentedest.baby"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.contentedest.baby"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "BASE_URL", "\"http://192.168.86.3:8005/\"")
    }
    buildTypes {
	getByName("debug") {
	    buildConfigField("String", "BASE_URL", "\"http://192.168.86.3:8005/\"")
	}
	getByName("release") {
	    // keep same or point to whatever you use in prod
	    buildConfigField("String", "BASE_URL", "\"http://192.168.86.3:8005/\"")
	    // shrinker/proguard rules are fine; BASE_URL is inlined
	    isMinifyEnabled = false
	    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
	}
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15" // Compatible with Kotlin 2.0.21
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Vico charts for growth data visualization
    implementation("com.patrykandpatrick.vico:compose:2.1.4")
    implementation("com.patrykandpatrick.vico:compose-m3:2.1.4")
    implementation("com.patrykandpatrick.vico:core:2.1.4")
    implementation("com.patrykandpatrick.vico:views:2.1.4")

    // Media3 for RTSP nursery camera playback
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0") // Reverted to 1.2.0
    kapt("androidx.hilt:hilt-compiler:1.2.0")      // Reverted to 1.2.0
    // Removed kapt("androidx.hilt:hilt-work:1.2.0")

    implementation("androidx.room:room-runtime:2.7.0-rc02")
    kapt("androidx.room:room-compiler:2.7.0-rc02")
    implementation("androidx.room:room-ktx:2.7.0-rc02")

    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi:1.15.1")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("androidx.security:security-crypto-ktx:1.1.0-alpha06")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
