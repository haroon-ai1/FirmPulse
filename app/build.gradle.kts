plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
val releaseKeystore = providers.environmentVariable("FIRMPULSE_KEYSTORE").orNull
android {
    namespace = "io.github.haroonjadoon.firmscope"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "io.github.haroonjadoon.firmscope"
        minSdk = 28
        targetSdk = 36
        versionCode = 101
        versionName = "1.0.0"
    }
    if (!releaseKeystore.isNullOrBlank()) {
        signingConfigs.create("ownerRelease") {
            storeFile = file(releaseKeystore)
            storePassword = providers.environmentVariable("FIRMPULSE_KEYSTORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("FIRMPULSE_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("FIRMPULSE_KEY_PASSWORD").get()
        }
    }
    buildTypes.getByName("release") {
        isDebuggable = false
        isMinifyEnabled = false
        if (!releaseKeystore.isNullOrBlank()) signingConfig = signingConfigs.getByName("ownerRelease")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            listOf("https.proxyHost", "https.proxyPort", "http.proxyHost", "http.proxyPort", "javax.net.ssl.trustStore").forEach { key ->
                System.getProperty(key)?.let { value -> it.systemProperty(key, value) }
            }
            it.jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED", "--add-opens=java.base/java.util=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED", "--add-opens=java.base/java.net=ALL-UNNAMED", "--add-opens=java.base/java.security=ALL-UNNAMED", "--add-opens=java.base/java.text=ALL-UNNAMED", "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
        }
    }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
}
