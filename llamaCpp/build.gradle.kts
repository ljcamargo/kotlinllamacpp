import com.vanniktech.maven.publish.AndroidSingleVariantLibrary

plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish) apply true
    alias(libs.plugins.gradleup.nmcp) apply true
}

mavenPublishing {
    configure(
        AndroidSingleVariantLibrary(
            variant = "release",
            sourcesJar = true,
            publishJavadocJar = true,
        )
    )
}

signing {
    useGpgCmd()
}

// Hook signing into publications
publishing {
    publications {
        withType<MavenPublication> {
            signing.sign(this)
        }
    }
}

fun nativeArchitectures(): List<String> {
    val value = project.properties["nativeArchitectures"]
    val archs = value?.toString()?.split(",") ?: listOf("x86_64", "arm64-v8a")
    return archs.filter { it != "armeabi-v7a" && it != "x86" } // Not building for 32-bit architectures
}

fun detectTargetPlatform(): String? {
    val taskName = gradle.startParameter.taskRequests
        .flatMap { it.args }
        .find { it.contains("buildCMake") }

    return when {
        taskName?.contains("x86_64", ignoreCase = true) == true -> "x86_64"
        taskName?.contains("arm64", ignoreCase = true) == true -> "arm64"
        else -> null // Default or unknown platform
    }
}
val libsForPlatform = when (detectTargetPlatform()) {
    "arm64" -> listOf("rnllama_v8", "rnllama_v8_2_fp16", "rnllama_v8_4_fp16_dotprod_i8mm")
    "x86_64" -> listOf("rnllama_x86_64", "rnllama")
    else -> listOf("rnllama", "rnllama_v8") // Default or fallback libraries
}

android {
    namespace = "org.nehuatl.llamacpp"
    compileSdk = 36
    ndkVersion = "29.0.13846066"

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                abiFilters.add("arm64-v8a")
                //abiFilters(*nativeArchitectures().toTypedArray())
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
    /*buildFeatures {
        prefabPublishing = true
    }

    prefab {
        libsForPlatform.forEach { libName ->
            create(libName) {
                headers = "src/main/cpp/lib" // Common header path
            }
        }
    }*/

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}