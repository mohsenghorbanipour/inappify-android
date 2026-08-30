import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

val sampleProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.isFile) {
        propertiesFile.inputStream().use(::load)
    }
}

val sampleProperty: (String, String) -> String = { name, defaultValue ->
    providers.gradleProperty(name).orNull
        ?: sampleProperties.getProperty(name)
        ?: defaultValue
}

val buildConfigString: (String) -> String = { value ->
    "\"" +
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n") +
        "\""
}

val sampleApplicationId = sampleProperty("inappify.sample.applicationId", "com.inappify.sample")
val sampleVersionCode = sampleProperty("inappify.sample.versionCode", "1").toIntOrNull() ?: 1
val sampleVersionName = sampleProperty("inappify.sample.versionName", "1.0")
val sampleApiKey = sampleProperty("inappify.sample.apiKey", "")
val sampleBazaarPublicKey = sampleProperty("inappify.sample.bazaarPublicKey", "")
val sampleCountry = sampleProperty("inappify.sample.country", "IR")

val sampleStoreFile = sampleProperty("inappify.sample.storeFile", "")
val sampleStorePassword = sampleProperty("inappify.sample.storePassword", "")
val sampleKeyAlias = sampleProperty("inappify.sample.keyAlias", "")
val sampleKeyPassword = sampleProperty("inappify.sample.keyPassword", "")
val sampleSigningValues = listOf(
    sampleStoreFile,
    sampleStorePassword,
    sampleKeyAlias,
    sampleKeyPassword,
)
val hasAnySampleSigning = sampleSigningValues.any(String::isNotBlank)
val hasSampleSigning = sampleSigningValues.all(String::isNotBlank)

check(!hasAnySampleSigning || hasSampleSigning) {
    "Sample signing is incomplete. Set storeFile, storePassword, keyAlias, and keyPassword together."
}

android {
    namespace = "com.inappify.sample"
    compileSdk = 34

    defaultConfig {
        applicationId = sampleApplicationId
        minSdk = 21
        targetSdk = 34
        versionCode = sampleVersionCode
        versionName = sampleVersionName

        buildConfigField("String", "INAPPIFY_API_KEY", buildConfigString(sampleApiKey))
        buildConfigField("String", "BAZAAR_PUBLIC_KEY", buildConfigString(sampleBazaarPublicKey))
        buildConfigField("String", "INAPPIFY_COUNTRY", buildConfigString(sampleCountry))

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasSampleSigning) {
            create("sample") {
                storeFile = rootProject.file(sampleStoreFile)
                storePassword = sampleStorePassword
                keyAlias = sampleKeyAlias
                keyPassword = sampleKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            if (hasSampleSigning) {
                signingConfig = signingConfigs.getByName("sample")
            }
        }

        release {
            isMinifyEnabled = false
            if (hasSampleSigning) {
                signingConfig = signingConfigs.getByName("sample")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    // The build runs on JDK 17 and emits Java 8-compatible bytecode.
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(project(":sdk"))
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)
}
