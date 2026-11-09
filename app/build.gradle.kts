plugins {
    alias(libs.plugins.android.application)
}


android {
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    namespace = "com.dev.tsukihimeandroid"
    compileSdk = 36
    ndkVersion = "28.2.13676358" 
    
    // disable linter
    lint {
        checkReleaseBuilds = false
    }
        

    defaultConfig {
        applicationId = "com.dev.tsukihimeandroid.dev"
        minSdk = 31 
        targetSdk = 36  
        versionCode = 6
        versionName = "0.3.0-dev"
        ndk { abiFilters += listOf("arm64-v8a") }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++20"
            }
        }
        
        vectorDrawables { 
            useSupportLibrary = true
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17 
        targetCompatibility = JavaVersion.VERSION_17 
    }

    buildTypes {
        debug {
            // Debug builds use the local Android debug key.
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        viewBinding = true
        
    }
    packaging {
        resources {
            resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
            resources.excludes.add("META-INF/kotlinx_coroutines_core.version")

            // The part below is only needed for compose builds.
            // This packaging block is required to solve interdependency conflicts.
            // They arise only when using local maven repo, so I suppose online repos have some way of solving such issues.

            // Caused by: com.android.builder.merge.DuplicateRelativeFileException: 4 files found with path 'commonMain/default/linkdata/module' from inputs:
            // - AndroidIDE\libs_source\gradle\localMvnRepository\androidx\collection\collection\1.4.2\collection-1.4.2.jar
            // - AndroidIDE\libs_source\gradle\localMvnRepository\androidx\lifecycle\lifecycle-common\2.8.7\lifecycle-common-2.8.7.jar
            // - AndroidIDE\libs_source\gradle\localMvnRepository\androidx\annotation\annotation\1.8.1\annotation-1.8.1.jar
            // - AndroidIDE\libs_source\gradle\localMvnRepository\org\jetbrains\kotlinx\kotlinx-coroutines-core\1.7.3\kotlinx-coroutines-core-1.7.3.jar
            // And some others.
            resources.pickFirsts.add("nonJvmMain/default/linkdata/package_androidx/0_androidx.knm")
            resources.pickFirsts.add("nonJvmMain/default/linkdata/root_package/0_.knm")
            resources.pickFirsts.add("nonJvmMain/default/linkdata/module")

            resources.pickFirsts.add("nativeMain/default/linkdata/root_package/0_.knm")
            resources.pickFirsts.add("nativeMain/default/linkdata/module")

            resources.pickFirsts.add("commonMain/default/linkdata/root_package/0_.knm")
            resources.pickFirsts.add("commonMain/default/linkdata/module")
            resources.pickFirsts.add("commonMain/default/linkdata/package_androidx/0_androidx.knm")

            resources.pickFirsts.add("META-INF/kotlin-project-structure-metadata.json")

            resources.merges.add("commonMain/default/manifest")
            resources.merges.add("nonJvmMain/default/manifest")
            resources.merges.add("nativeMain/default/manifest")
        }
    }
    
    configurations.all {
        resolutionStrategy {
            // Force the use of Kotlin stdlib 1.9.22 for all modules
            force("org.jetbrains.kotlin:kotlin-stdlib:2.4.21")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.4.21")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.4.21")
    
            // Force specific AndroidX versions to avoid conflicts
            force("androidx.collection:collection:1.4.2")
            force("androidx.annotation:annotation:1.8.1")
            force("androidx.core:core-ktx:1.8.0")
            force("androidx.lifecycle:lifecycle-runtime-ktx:2.3.1")
            force("androidx.collection:collection-ktx:1.4.2")
        }
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:deprecation")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget("17"))
    }
}
 


dependencies {


    implementation(libs.androidx.appcompat)
    implementation("androidx.annotation:annotation:1.8.1")
    // GameActivity provides a SurfaceView host so Kotlin touch controls can safely overlay the Rust renderer.
    implementation("androidx.games:games-activity:4.4.0")
    implementation(libs.androidx.constraintlayout)
    implementation(libs.android.material)
    implementation(libs.androidx.startup.runtime)
    implementation(libs.androidx.interpolator)
    testImplementation(libs.junit)
}
