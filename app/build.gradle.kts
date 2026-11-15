plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.openapi)
}

// Where the API lives. The default is the emulator's name for the computer it runs on, so a
// debug build talks to `docker compose up` with no setup. A real phone or a release build
// passes -Pdineos.apiUrl=https://...
val apiUrl = providers.gradleProperty("dineos.apiUrl").orElse("http://10.0.2.2:8000/")
val generatedApi = layout.buildDirectory.dir("generated/openapi")

android {
    namespace = "dev.dineos.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.dineos.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "API_URL", "\"${apiUrl.get()}\"")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets["main"].kotlin.directories.add(generatedApi.get().dir("src/main/kotlin").asFile.path)
}

// The API client is generated from the backend's OpenAPI spec (contract/openapi.json, copied
// from the DineOS repo by scripts/sync-contract.sh), so a renamed field breaks the build
// here instead of failing at runtime on a phone.
openApiGenerate {
    generatorName = "kotlin"
    // A file: URI, not a path: the spec parser reads "C:\..." as a URL scheme on Windows.
    inputSpec = layout.projectDirectory.file("../contract/openapi.json").asFile.toURI().toString()
    outputDir = generatedApi.get().asFile.path
    apiPackage = "dev.dineos.api"
    modelPackage = "dev.dineos.api.model"
    configOptions = mapOf(
        "library" to "jvm-retrofit2",
        "serializationLibrary" to "kotlinx_serialization",
        "useCoroutines" to "true",
        "dateLibrary" to "string",
    )
    // Only the endpoints the app uses; the rest of the API is the web dashboard's.
    // No ApiClient or adapters: the app builds its own Retrofit. CollectionFormats is the one
    // support file the generated interfaces import.
    globalProperties = mapOf("apis" to "Auth,Checklists", "models" to "", "supportingFiles" to "CollectionFormats.kt")
    generateApiTests = false
    generateModelTests = false
}
// Generate before anything compiles or processes annotations.
tasks.named("preBuild") { dependsOn("openApiGenerate") }

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime)
    implementation(libs.datastore)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockwebserver)
}
