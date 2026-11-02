import com.android.build.api.dsl.AndroidSourceDirectorySet
import com.android.build.api.variant.FilterConfiguration
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

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
    alias(libs.plugins.hilt)
    alias(libs.plugins.kover)
    // For the instrumented coverage report only. Kover stays the JVM agent: the Test tasks
    // below turn JaCoCo's agent off, so no test JVM carries two.
    jacoco
}

// Room writes versioned schema JSON when AppDatabase.exportSchema is true.
ksp {
    arg("room.schemaLocation", "${project.projectDir}/schemas")
}

// JVM coverage for :app:testGoogleDebugUnitTest, reported by the `unit` CI job
// (koverXmlReportGoogleDebug). Instrumented coverage would need the device job and is
// not collected: Compose screens and activities show 0% here and are proven by the
// instrumented suite instead. Generated code and @Preview scaffolding are left out so
// the number reflects hand-written Kotlin.
kover {
    reports {
        filters {
            excludes {
                classes(
                    "*.BuildConfig",
                    "*_Factory",
                    "*_Factory\$*",
                    "*_Impl",
                    "*_Impl\$*",
                    "*_HiltModules*",
                    "*_LazyMapKey",
                    "*_MembersInjector",
                )
                packages(
                    "dagger.hilt.internal",
                    "hilt_aggregated_deps",
                    "net.reichholf.dreamdroid.ui.previews",
                )
            }
        }
    }
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.sqlite.bundled)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    implementation(libs.preference)
    implementation(libs.recyclerview)
    implementation(libs.tv.material)
    implementation(libs.annotation)
    implementation(libs.fragment)
    implementation(libs.lifecycle.runtime)
    implementation(libs.work.runtime)
    implementation(libs.material)
    implementation(libs.vlc)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ui)
    implementation(libs.okhttp)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.core)
    implementation(libs.serialization.json)
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
    implementation(libs.compose.adaptive.layout)
    implementation(libs.reorderable)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    // Phase 2.6e: Glance chassis for Virtual Remote (dense RCU stays RemoteViews via AndroidRemoteViews).
    implementation(libs.glance.appwidget)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.work.testing)
    kspAndroidTest(libs.hilt.compiler)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
    // JVM unit tests need the desktop native library. The Android AAR only
    // ships bionic libsqliteJni.so, which the host JVM cannot load.
    testImplementation(libs.sqlite.bundled.jvm)
}

private val baseVersionCode = 471

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
        versionName = "2.0.471"
        minSdk = 26
        targetSdk = 37
        // Runs every instrumented test on HiltTestApplication (docs/hilt-migration.md, PR 15).
        testInstrumentationRunner = "net.reichholf.dreamdroid.testutil.HiltTestRunner"
        // CI splits the instrumented suite across runners (android-ci.yml androidTest
        // matrix): -PtestShardCount=3 -PtestShardIndex=0..2. AndroidJUnitRunner reads
        // numShards/shardIndex. Not -Pandroid.testInstrumentationRunnerArguments… (AGENTS.md).
        providers.gradleProperty("testShardCount").orNull?.let { count ->
            testInstrumentationRunnerArguments["numShards"] = count
            testInstrumentationRunnerArguments["shardIndex"] =
                providers.gradleProperty("testShardIndex").get()
        }
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
        getByName("debug") {
            // Debug-only hosts for instrumented tests (a Hilt activity), never in release.
            manifest.srcFile("debug/AndroidManifest.xml")
            replaceDirs(java, "debug/java")
            replaceDirs(kotlin, "debug/java")
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
            isDefault = true
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
            // A bundle splits by ABI itself; with ABI splits on, resource shrinking leaves one
            // shrunk-resources file per split and bundling fails
            // (https://issuetracker.google.com/402800800).
            val bundling = gradle.startParameter.taskNames.any {
                it.substringAfterLast(':').startsWith("bundle")
            }
            isEnable = !bundling && (project.hasProperty("arm64Apk") || !project.hasProperty("ci"))
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
            isDefault = true
            buildConfigField("long", "BUILD_TIME", "0L")
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "@string/app_name_debug")
            // -PinstrumentCoverage: JaCoCo-instrument the app for the instrumented suite, so
            // AGP pulls a coverage.ec off the device after the connected run. Slower and a
            // different APK, so only the coverage run asks for it. Unit tests keep Kover.
            enableAndroidTestCoverage = project.hasProperty("instrumentCoverage")
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
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        // android.util.Log and friends return defaults on the JVM, so tests can drive
        // EnigmaHttp error paths (docs/hilt-migration.md, decision 10).
        unitTests.isReturnDefaultValues = true
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

// Kover instruments the unit-test JVM; the jacoco plugin would attach its own agent to the
// same Test tasks. Keep that agent off: only the report task type is used.
tasks.withType<Test>().configureEach {
    extensions.findByType(JacocoTaskExtension::class.java)?.isEnabled = false
}

/**
 * Instrumented coverage: the androidTest job's shards each upload the device's `coverage.ec`,
 * and this turns all of them into one report — JacocoReport merges the exec files itself.
 * The class dirs are AGP 9's build intermediates; if AGP moves them this fails loudly here
 * instead of reporting zero.
 */
// Same generated code the Kover report leaves out, so the two numbers stay comparable.
val GENERATED = listOf(
    "**/R.class",
    "**/R$*.class",
    "**/BuildConfig.class",
    "**/*_Factory.class",
    "**/*_Impl.class",
    "**/*_HiltModules*",
    "**/Hilt_*.class",
    "**/*_MembersInjector.class",
)

tasks.register<JacocoReport>("instrumentedCoverageReport") {
    group = "verification"
    description = "Coverage of the instrumented suite, from coverage-in/**/*.ec."
    // The report reads compiled classes, so the app has to be built first.
    dependsOn("assembleGoogleDebug")
    executionData.setFrom(
        fileTree(layout.projectDirectory.dir("coverage-in")) { include("**/*.ec") },
    )
    classDirectories.setFrom(
        fileTree(
            layout.buildDirectory.dir(
                "intermediates/built_in_kotlinc/googleDebug/compileGoogleDebugKotlin/classes",
            ),
        ) { exclude(GENERATED) },
        fileTree(
            layout.buildDirectory.dir(
                "intermediates/javac/googleDebug/compileGoogleDebugJavaWithJavac/classes",
            ),
        ) { exclude(GENERATED) },
    )
    sourceDirectories.setFrom(files("src"))
    reports {
        xml.required.set(true)
        xml.outputLocation.set(
            layout.buildDirectory.file("reports/instrumented-coverage/report.xml"),
        )
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/instrumented-coverage/html"))
    }
}

tasks.register("prTests") {
    group = "verification"
    description = "What the unit job in .github/workflows/android-ci.yml runs."
    dependsOn(
        "testGoogleDebugUnitTest",
        "compileGoogleDebugAndroidTestKotlin",
        "compileGoogleDebugAndroidTestJavaWithJavac",
        "koverXmlReportGoogleDebug",
    )
}

tasks.register("prLint") {
    group = "verification"
    description = "What the lint job in .github/workflows/android-ci.yml runs."
    dependsOn(rootProject.tasks.named("spotlessCheck"), "lintGoogleDebug")
}

tasks.register("prCheck") {
    group = "verification"
    description = "Everything the PR jobs in .github/workflows/android-ci.yml run."
    dependsOn("prTests", "prLint")
}
