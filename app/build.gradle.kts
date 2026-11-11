// تطبيق المنظّم: غلاف TWA (Trusted Web Activity) يفتح واجهة orch-console بمحرك Chrome.
// ما فيه كود للتطبيق نفسه هنا: كل شي على السيرفر (داخل Tailscale بس). الموافقة بالبصمة تشتغل لأنها Chrome.
plugins {
    id("com.android.application")
}

val keystorePath: String? = System.getenv("ORCH_KEYSTORE_PATH")
val keystorePassword: String? = System.getenv("ORCH_KEYSTORE_PASSWORD")

android {
    namespace = "com.amjadoh.orch"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.amjadoh.orch"
        minSdk = 24
        targetSdk = 35
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "1.0.0"
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
            // بدون مفتاح ما ينبني release (عشان ما يطلع APK غير موقّع بالغلط)
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.google.androidbrowserhelper:androidbrowserhelper:2.5.0")
}
