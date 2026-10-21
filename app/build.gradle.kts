// ==========================================
// 插件配置块：声明本项目使用的 Gradle 插件
// ==========================================
plugins {
    // 应用 Android 应用插件，用于构建 Android 应用
    alias(libs.plugins.android.application)
    // 应用 Kotlin Compose 插件，用于支持 Jetpack Compose UI 框架
    alias(libs.plugins.kotlin.compose)
}

// ==========================================
// Android 配置块：配置 Android 项目的相关参数
// ==========================================
android {
    // 命名空间：用于 R 类和 BuildConfig 类的包名
    namespace = "com.example.hctool"
    
    // 编译 SDK 配置：指定编译时使用的 Android SDK 版本
    compileSdk {
        // 使用 release(37) 表示使用 API 级别 37 的发布版 SDK
        version = release(37)
    }

    // ==========================================
    // 默认配置块：为所有构建变体提供默认配置
    // ==========================================
    defaultConfig {
        // 应用 ID：Android 应用的唯一包名标识
        applicationId = "com.example.hctool"
        // 最低 SDK 版本：应用支持的最低 Android 版本（API 33 = Android 13）
        minSdk = 33
        // 目标 SDK 版本：应用针对的目标 Android 版本（API 37）
        targetSdk = 37
        // 版本号：用于应用商店和设备识别的版本代码，每次发布需递增
        versionCode = 1
        // 版本名称：面向用户显示的版本号字符串
        versionName = "1.0"

        // 测试运行器：指定用于运行仪器化测试的测试运行器
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ==========================================
    // 构建类型配置块：配置不同构建类型的行为
    // ==========================================
    buildTypes {
        // Release 构建类型配置：用于发布版本的优化设置
        release {
            // 优化配置：启用代码和资源优化
            optimization {
                // 启用优化：开启代码 shrinking 和优化
                enable = true
                // 包作用域：指定需要优化的包范围，保留这些库的代码
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    
    // ==========================================
    // 编译选项配置块：配置 Java/Kotlin 编译的兼容性
    // ==========================================
    compileOptions {
        // 源代码兼容性：指定源代码使用的 Java 版本为 11
        sourceCompatibility = JavaVersion.VERSION_11
        // 目标兼容性：指定生成的字节码兼容的 Java 版本为 11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    // ==========================================
    // 构建特性配置块：启用或禁用特定的构建功能
    // ==========================================
    buildFeatures {
        // 启用 Compose：开启 Jetpack Compose UI 框架支持
        compose = true
    }
}

// ==========================================
// 依赖配置块：声明项目所需的外部库
// ==========================================
dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    
    // 液态玻璃效果库
    implementation(libs.backdrop)
    
    // miuix 全套依赖
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.squircle)
    implementation(libs.miuix.nav)
    
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
