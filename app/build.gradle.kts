plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.gradleup.nmcp) apply false
    alias(libs.plugins.kotlin.compose)
}

fun nativeArchitectures(): List<String> {
    val value = project.properties["nativeArchitectures"]
    val archs = value?.toString()?.split(",") ?: listOf("x86_64", "arm64-v8a")
    return archs.filter { it != "armeabi-v7a" && it != "x86" } // Not building for 32-bit architectures
}

android {
    namespace = "org.nehuatl.sample"
    compileSdk = 35
    ndkVersion = "25.1.8937393"

    defaultConfig {
        applicationId = "org.nehuatl.sample"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                abiFilters("arm64-v8a")
                arguments += listOf(
                    "-DLLAMA_BUILD_COMMON=ON",
                    "-DCMAKE_BUILD_TYPE=Release"
                )
                cppFlags("")
            }
        }
        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters.add("arm64-v8a")
        }
    }

    buildTypes {
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        prefab = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    api(project(":llamaCpp"))
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}