// سيرفري الأصلي (Kotlin + Jetpack Compose). يتطور جنب غلاف TWA (:app) بحزمة تجريبية مستقلة،
// فيتثبت جنب التطبيق الحالي وما يلمسه. نفس مفتاح التوقيع، لأن السيرفر يتعرف على التطبيق من بصمة شهادته.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystorePath: String? = System.getenv("ORCH_KEYSTORE_PATH")
val keystorePassword: String? = System.getenv("ORCH_KEYSTORE_PASSWORD")
val runNumber: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.amjadoh.sirfari"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.amjadoh.orch.next"
        minSdk = 30 // مفاتيح البصمة القوية (setUserAuthenticationParameters) من أندرويد 11
        targetSdk = 35
        versionCode = runNumber
        versionName = "0.1.0-dev.$runNumber"
    }

    signingConfigs {
        create("release") {
            if (keystorePath != null && keystorePassword != null) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                storeType = "pkcs12"
                keyAlias = "orch"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures { compose = true }

    // اختبارات الشاشات بـ Robolectric (بدون محاكي): ترسم الشاشات وتحفظ صورها في build/shots
    testOptions { unitTests.isIncludeAndroidResources = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test.ext:junit:1.2.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
