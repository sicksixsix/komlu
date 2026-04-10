// app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.komiklu.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.komiklu.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // Base URL CDN — ganti sesuai konfigurasi
        buildConfigField("String", "BASE_URL",      "\"https://cdn.komiklu.com/\"")
        buildConfigField("String", "API_BASE_URL",  "\"https://api.komiklu.com/\"")
        buildConfigField("String", "IMAGE_CDN_URL", "\"https://img.komiklu.com/\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)

    // Navigation Component
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // ViewModel + LiveData + Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Retrofit + OkHttp + Gson
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // Coil — Image loading dengan caching
    implementation(libs.coil)
    implementation(libs.coil.svg)

    // Room — Local database (history, favorit)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore — Preferences ringan
    implementation(libs.androidx.datastore.preferences)

    // Hilt — Dependency Injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    // Paging 3 — Load More pagination
    implementation(libs.androidx.paging.runtime.ktx)

    // SwipeRefreshLayout
    implementation(libs.androidx.swiperefreshlayout)

    // Shimmer — Loading skeleton
    implementation(libs.shimmer)

    // ViewPager2 — Banner/Slideshow
    implementation(libs.androidx.viewpager2)

    // PhotoView — Zoom in/out untuk reader
    implementation(libs.photoview)

    // Encrypted SharedPreferences — Token storage
    implementation(libs.androidx.security.crypto)

    // Gson
    implementation(libs.gson)
}
