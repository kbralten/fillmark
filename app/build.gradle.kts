plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}

val buildNumber = providers.gradleProperty("versionCode").map { it.toInt() }.getOrElse(1)
val versionNameProp = providers.gradleProperty("versionName").getOrElse("1.0.$buildNumber")

val releaseStoreFileProp = providers.gradleProperty("RELEASE_STORE_FILE")
  .orElse(providers.environmentVariable("RELEASE_STORE_FILE"))
  .orNull
val releaseStorePasswordProp = providers.gradleProperty("RELEASE_STORE_PASSWORD")
  .orElse(providers.environmentVariable("RELEASE_STORE_PASSWORD"))
  .orNull
val releaseKeyAliasProp = providers.gradleProperty("RELEASE_KEY_ALIAS")
  .orElse(providers.environmentVariable("RELEASE_KEY_ALIAS"))
  .orNull
val releaseKeyPasswordProp = providers.gradleProperty("RELEASE_KEY_PASSWORD")
  .orElse(providers.environmentVariable("RELEASE_KEY_PASSWORD"))
  .orNull
val hasReleaseSigning = !releaseStoreFileProp.isNullOrBlank() &&
  !releaseStorePasswordProp.isNullOrBlank() &&
  !releaseKeyAliasProp.isNullOrBlank() &&
  !releaseKeyPasswordProp.isNullOrBlank() &&
  file(releaseStoreFileProp).exists()

android {
  namespace = "com.fillmark.app"
  compileSdk = 36

  signingConfigs {
    getByName("debug") {
      storeFile = file("debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
    if (hasReleaseSigning) {
      create("release") {
        storeFile = file(releaseStoreFileProp!!)
        storePassword = releaseStorePasswordProp
        keyAlias = releaseKeyAliasProp
        keyPassword = releaseKeyPasswordProp
      }
    }
  }

  defaultConfig {
    applicationId = "ca.bralten.fillmark"
    minSdk = 29
    targetSdk = 36
    versionCode = buildNumber
    versionName = versionNameProp
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (hasReleaseSigning) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
    debug {
      signingConfig = signingConfigs.getByName("debug")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = true
    shaders = false
  }

  testOptions {
    unitTests.isReturnDefaultValues = true
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

kotlin {
  jvmToolchain(17)
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

// Prevent concurrent KSP variant tasks from racing on the shared Room schema JSON file
tasks.matching { it.name == "kspReleaseKotlin" }.configureEach {
  mustRunAfter(tasks.matching { it.name == "kspDebugKotlin" })
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.core.splashscreen)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.kotlinx.serialization.json)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Room Persistence
  implementation(libs.room.runtime)
  implementation(libs.room.ktx)
  ksp(libs.room.compiler)

  // DataStore Preferences
  implementation(libs.androidx.datastore.preferences)

  // Jetpack Glance Widgets
  implementation(libs.androidx.glance)
  implementation(libs.androidx.glance.appwidget)
  implementation(libs.androidx.glance.material3)

  // Health Connect
  implementation(libs.androidx.health.connect)

  // WorkManager
  implementation(libs.androidx.work.runtime.ktx)
}
