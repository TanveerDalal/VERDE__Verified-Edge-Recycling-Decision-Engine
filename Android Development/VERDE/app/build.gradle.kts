plugins {
    alias(libs.plugins.android.application)          // this is an Android app
}

android {
    namespace = "com.example.verde"                  // package of your code (R, binding classes)
    compileSdk {
        version = release(37)                        // Android version used to build
    }

    defaultConfig {
        applicationId = "com.example.verde"          // the app's unique ID on the phone
        minSdk = 26                                  // works on Android 8.0 and newer
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false                       // no code shrinking (simpler for a uni project)
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // View Binding: lets Kotlin use ActivityMainBinding etc.
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)       // enableEdgeToEdge, permission pop-up
    implementation(libs.androidx.appcompat)          // AppCompatActivity
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)                    // MaterialButton, MaterialCardView, Material3 theme

    // CameraX: phone camera (preview + taking photos)
    val cameraxVersion = "1.6.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")   // PreviewView

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}