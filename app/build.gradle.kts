import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.yuguang.lumo"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.yuguang.lumo"
        minSdk = 24
        targetSdk = 37
        versionCode = 4
        versionName = "1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
            signingConfig = signingConfigs.findByName("release")
        }
    }

    /**
     * Release 签名。
     *
     * 材料只从本机 `local.properties` 或环境变量读，不进版本库；两者都没有时不注册
     * signingConfig，release 照旧能构建（未签名包，可用于体积与混淆验证），
     * 不会因为缺密钥让整条构建链失败。
     */
    val signingProps = Properties()
    val localProps = rootProject.file("local.properties")
    if (localProps.isFile) FileInputStream(localProps).use { signingProps.load(it) }
    fun signingValue(key: String): String? =
        (signingProps.getProperty(key) ?: System.getenv(key))?.takeIf { it.isNotBlank() }
    val storePath = signingValue("RELEASE_STORE_FILE")
    val store = storePath?.let { rootProject.file(it) }?.takeIf { it.isFile }
    if (store != null) {
        signingConfigs.create("release") {
            storeFile = store
            storePassword = signingValue("RELEASE_STORE_PASSWORD")
            keyAlias = signingValue("RELEASE_KEY_ALIAS")
            keyPassword = signingValue("RELEASE_KEY_PASSWORD")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        // 版本号展示用 BuildConfig.VERSION_NAME，避免设置页写死的字符串再次过期。
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    // Room 的 schema 输出目录，配合 exportSchema = true 做迁移校验。
    arg("room.schemaLocation", "$projectDir/schemas")
}

// MigrationTestHelper 从 assets 读取导出的 schema；测试变体也要声明这份目录。
android {
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    ksp(libs.kotlin.metadata.jvm)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.security.crypto)
    implementation(libs.coil.compose)

    // Compose 工具链只在调试包用：Preview 与 Layout Inspector 需要它，
    // release 里带上只是白增体积（原先在 toml 里声明了却没接线）。
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // 迁移测试用 MigrationTestHelper 校验 schema 与数据，而不是手写建库 SQL。
    androidTestImplementation(libs.androidx.room.testing)
}
