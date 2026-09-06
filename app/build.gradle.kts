plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

val versionNameProvider = providers.gradleProperty("version.name")
val versionCodeProvider = providers.gradleProperty("version.code").map(String::toInt)

android {
    namespace = "eu.hxreborn.pixelinjector"
    compileSdk = 37

    defaultConfig {
        applicationId = "eu.hxreborn.pixelinjector"
        minSdk = 33
        targetSdk = 37
        versionCode = versionCodeProvider.get()
        versionName = versionNameProvider.get()

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        create("release") {
            fun secret(name: String): String? =
                providers
                    .gradleProperty(name)
                    .orElse(providers.environmentVariable(name))
                    .orNull

            val storeFilePath = secret("RELEASE_STORE_FILE")
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                storePassword = secret("RELEASE_STORE_PASSWORD")
                keyAlias = secret("RELEASE_KEY_ALIAS")
                keyPassword = secret("RELEASE_KEY_PASSWORD")
                storeType = secret("RELEASE_STORE_TYPE") ?: "PKCS12"
            } else {
                logger.warn("RELEASE_STORE_FILE not found. Release signing is disabled.")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            vcsInfo {
                include = false
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig =
                signingConfigs
                    .getByName("release")
                    .takeIf { it.storeFile != null }
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    androidResources {
        localeFilters += "en"
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            merges += "META-INF/xposed/**"
            excludes += "META-INF/LICENSE*"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        disable.addAll(listOf("PrivateApi", "DiscouragedPrivateApi"))
    }
}

kotlin {
    jvmToolchain(21)
}

val ktlintCli: Configuration by configurations.creating

dependencies {
    ktlintCli(libs.ktlint.cli)

    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)

    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.core.ktx)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.libsu.core)
    implementation(libs.dexkit)
}

tasks.register<JavaExec>("ktlintCheck") {
    group = "verification"
    description = "Check Kotlin code style"
    classpath = ktlintCli
    mainClass.set("com.pinterest.ktlint.Main")
    args("src/**/*.kt")
}

tasks.register<JavaExec>("ktlintFormat") {
    group = "verification"
    description = "Fix Kotlin code style violations"
    classpath = ktlintCli
    mainClass.set("com.pinterest.ktlint.Main")
    args("-F", "src/**/*.kt")
}

val checkHookImports by tasks.registering {
    group = "verification"
    description = "Fail when hook code imports the app UI or Compose or the app imports hook code"
    val sourceDir = layout.projectDirectory.dir("src/main/kotlin/eu/hxreborn/pixelinjector")
    inputs.dir(sourceDir)
    doLast {
        val hookBans = listOf("import eu.hxreborn.pixelinjector.ui.", "import androidx.compose.", "import com.topjohnwu.")
        val appBans = listOf("import eu.hxreborn.pixelinjector.xposed.")
        val offenders =
            sourceDir.asFileTree
                .matching { include("**/*.kt") }
                .flatMap { file ->
                    val bans = if ("/xposed/" in file.path) hookBans else appBans
                    file
                        .readLines()
                        .withIndex()
                        .filter { (_, line) -> bans.any { line.startsWith(it) } }
                        .map { (index, line) -> "${file.name}:${index + 1}: $line" }
                }
        check(offenders.isEmpty()) { "layer import violation:\n" + offenders.joinToString("\n") }
    }
}

tasks.named("preBuild").configure { dependsOn(checkHookImports) }
tasks.named("check").configure { dependsOn("ktlintCheck") }
