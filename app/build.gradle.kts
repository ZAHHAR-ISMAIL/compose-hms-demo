import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("kotlin-parcelize")
    id("com.huawei.agconnect")
}

val localConfig = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

android {
    namespace = "com.demo.hmscomposeapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.demo.hmscomposeapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "com.demo.hmscomposeapp.MapTestRunner"
        vectorDrawables.useSupportLibrary = true
    }

    flavorDimensions += "store"
    productFlavors {
        create("google") {
            dimension = "store"
            manifestPlaceholders["googleMapsApiKey"] = providers
                .gradleProperty("GOOGLE_MAPS_API_KEY")
                .getOrElse(localConfig.getProperty("GOOGLE_MAPS_API_KEY", ""))
        }
        create("huawei") {
            dimension = "store"
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("hms.keystore")
            keyAlias = "alias_name"
            keyPassword = "hms@12345678"
            storePassword = "hms@12345678"
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.03.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.savedstate:savedstate-compose:1.4.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material")

    add("googleImplementation", "com.google.android.gms:play-services-location:21.3.0") { version { strictly("21.3.0") } }
    add("googleImplementation", "com.google.android.gms:play-services-maps:19.0.0") {
        version { strictly("19.0.0") }
    }
    add("googleImplementation", "com.google.maps.android:maps-compose:6.5.0") { version { strictly("6.5.0") } }
    add("googleImplementation", "com.google.maps.android:android-maps-utils:3.8.2") {
        version { strictly("3.8.2") }
    }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    add("huaweiImplementation", "com.huawei.hms:location:6.16.0.302")
    add("huaweiImplementation", "com.huawei.hms:maps:6.15.1.318")
    // HMS cannot render the supplied world-envelope mask reliably; ordinary polygons stay native.
    add("huaweiImplementation", "io.github.earcut4j:earcut4j:2.2.2") {
        version { strictly("2.2.2") }
    }
}

tasks.register("verifyMapIsolation") {
    group = "verification"
    description = "Reject opposite-provider map and location dependencies in every app flavor."
    doLast {
        for (flavor in listOf("google", "huawei")) {
            for (buildType in listOf("Debug", "Release")) {
                val classpath = configurations.getByName("${flavor}${buildType}RuntimeClasspath")
                val graph = classpath.incoming.resolutionResult
                check(graph.allDependencies.none { it is org.gradle.api.artifacts.result.UnresolvedDependencyResult }) {
                    "Cannot verify an unresolved ${flavor}${buildType} dependency graph"
                }
                val forbidden = graph.allComponents.mapNotNull { it.moduleVersion }.filter { id ->
                    if (flavor == "huawei") {
                        id.group == "com.google.maps.android" ||
                            (id.group == "com.google.android.gms" && id.name in setOf("play-services-maps", "play-services-location"))
                    } else (id.group == "com.huawei.hms" && id.name in setOf("maps", "location")) || id.group == "io.github.earcut4j"
                }
                check(forbidden.isEmpty()) { "${flavor}${buildType} includes forbidden SDKs: $forbidden" }
                val resolved = graph.allComponents.mapNotNull { it.moduleVersion }.associate { "${it.group}:${it.name}" to it.version }
                val expected = if (flavor == "google") mapOf(
                    "com.google.android.gms:play-services-maps" to "19.0.0",
                    "com.google.android.gms:play-services-location" to "21.3.0",
                    "com.google.maps.android:maps-compose" to "6.5.0",
                    "com.google.maps.android:android-maps-utils" to "3.8.2",
                ) else mapOf("com.huawei.hms:maps" to "6.15.1.318", "com.huawei.hms:location" to "6.16.0.302",
                    "io.github.earcut4j:earcut4j" to "2.2.2")
                expected.forEach { (module, version) -> check(resolved[module] == version) { "$module resolved to ${resolved[module]}, expected $version" } }
                check("com.google.maps.android:maps-utils-ktx" !in resolved) { "Unused Maps Utils KTX must not enter this demo" }
                logger.lifecycle("PASS ${flavor}${buildType}: isolated dependencies and exact production baseline verified")
            }
        }
    }
}
