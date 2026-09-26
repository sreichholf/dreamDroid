import com.android.build.api.dsl.AndroidSourceDirectorySet
import com.android.build.api.variant.FilterConfiguration
import org.gradle.api.tasks.util.PatternFilterable

private fun replaceDirs(source: AndroidSourceDirectorySet, dir: String) {
    val dirs = source.directories as MutableSet<String>
    dirs.clear()
    dirs.add(dir)
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Room writes versioned schema JSON when AppDatabase.exportSchema is true.
ksp {
    arg("room.schemaLocation", "${project.projectDir}/schemas")
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.sqlite.bundled)

    implementation(libs.preference)
    implementation(libs.recyclerview)
    implementation(libs.tv.material)
    implementation(libs.annotation)
    implementation(libs.fragment)
    implementation(libs.lifecycle.runtime)
    implementation(libs.work.runtime)
    implementation(libs.material)
    implementation(libs.vlc)
    implementation(libs.okhttp)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.core)
    implementation(libs.coil)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.commons.net)
    implementation(libs.jmdns)
    implementation(libs.gson)

    implementation(platform(libs.compose.bom))
    androidTestImplementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.adaptive)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    // Phase 2.6e: Glance chassis for Virtual Remote (dense RCU stays RemoteViews via AndroidRemoteViews).
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.okhttp.mockwebserver)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    // JVM unit tests need the desktop native library. The Android AAR only
    // ships bionic libsqliteJni.so, which the host JVM cannot load.
    testImplementation(libs.sqlite.bundled.jvm)
}

private val baseVersionCode = 464

private val abiVersionCodes = mapOf(
    "armeabi" to 1,
    "armeabi-v7a" to 2,
    "arm64-v8a" to 3,
    "mips" to 5,
    "mips64" to 6,
    "x86" to 8,
    "x86_64" to 9,
)

android {
    namespace = "net.reichholf.dreamdroid"
    compileSdk = project.property("COMPILE_SDK_VERSION").toString().toInt()

    defaultConfig {
        versionCode = baseVersionCode
        versionName = "2.0.464"
        minSdk = 26
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true
        buildConfigField("int", "MIN_SDK", "26")
    }
    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            replaceDirs(java, "src")
            (java as PatternFilterable).exclude("test/**", "androidTest/**")
            // AGP 9 built-in Kotlin does not compile .kt from java srcDirs.
            replaceDirs(kotlin, "src")
            (kotlin as PatternFilterable).exclude("test/**", "androidTest/**")
            replaceDirs(res, "res")
        }
        getByName("androidTest") {
            replaceDirs(java, "androidTest/java")
            // Instrumented tests live in androidTest/java (AGENTS.md).
            replaceDirs(kotlin, "androidTest/java")
            replaceDirs(res, "androidTest/res")
            replaceDirs(resources, "androidTest/resources")
        }
        getByName("test") {
            replaceDirs(java, "test/java")
            replaceDirs(kotlin, "test/java")
            replaceDirs(resources, "test/resources")
        }
    }
    // Shared debug cert (repo-root debug.keystore) so CI, Cloud, and local
    // googleDebug APKs are interchangeable. AGP's default is ~/.android/debug.keystore.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    flavorDimensions += "distribution"
    productFlavors {
        create("google") {
            dimension = "distribution"
            applicationId = "net.reichholf.dreamdroid"
        }
        create("fdroid") {
            dimension = "distribution"
            applicationId = "net.reichholf.dreamdroid"
        }
        create("amazon") {
            dimension = "distribution"
            applicationId = "net.reichholf.dreamdroid.amazon"
        }
    }
    splits {
        abi {
            // -Pci: one fat APK (unit/androidTest jobs).
            // -Parm64Apk: ABI-split arm64-only (CI artifact upload).
            isEnable = project.hasProperty("arm64Apk") || !project.hasProperty("ci")
            reset()
            if (project.hasProperty("arm64Apk")) {
                include("arm64-v8a")
                isUniversalApk = false
            } else {
                include("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
                isUniversalApk = true
            }
        }
    }

    // ABI versionCode overrides moved to androidComponents (AGP 9 removed applicationVariants).

    buildTypes {
        getByName("debug") {
            buildConfigField("long", "BUILD_TIME", "0L")
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "@string/app_name_debug")
            resValue("string", "app_name_tv", "@string/app_name_tv_debug")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            isDebuggable = false
            buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")
            resValue("string", "app_name", "@string/app_name_release")
            resValue("string", "app_name_tv", "@string/app_name_tv_release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
    buildFeatures {
        buildConfig = true
        compose = true
        // AGP 9 defaults resValues to false; debug/release use resValue.
        resValues = true
    }
}

// Unit-test classpaths otherwise keep sqlite-bundled-android ahead of the JVM jar.
configurations.configureEach {
    if (name.endsWith("UnitTestRuntimeClasspath")) {
        resolutionStrategy.dependencySubstitution {
            substitute(module("androidx.sqlite:sqlite-bundled-android")).using(
                module("androidx.sqlite:sqlite-bundled-jvm:${libs.versions.sqlite.get()}"),
            )
        }
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.outputs.forEach { output ->
            val abiFilter = output.filters.find { filter ->
                filter.filterType == FilterConfiguration.FilterType.ABI
            }
            val abiCode = abiVersionCodes[abiFilter?.identifier] ?: 99
            val base = output.versionCode.orNull ?: baseVersionCode
            output.versionCode.set(abiCode * 10_000_000 + base)
        }
    }
}
