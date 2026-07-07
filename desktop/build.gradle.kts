import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation("aws.sdk.kotlin:s3:1.3.112")
    implementation("org.apache.pdfbox:pdfbox:2.0.31")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

compose.desktop {
    application {
        mainClass = "com.pdfvault.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.AppImage)
            packageName = "PdfVault"
            packageVersion = "1.0.0"
            // The packaged app ships a jlink-trimmed runtime; these aren't in Compose's default
            // module set. java.net.http = BackendApi's HttpClient (missing it crashes on launch
            // with NoClassDefFoundError); jdk.crypto.ec = TLS handshakes to AWS/API Gateway.
            modules("java.net.http", "jdk.crypto.ec")
        }
    }
}
