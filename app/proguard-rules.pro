# AWS SDK for Kotlin relies on reflection-free serde, but keep model classes to be safe.
-keep class aws.sdk.kotlin.** { *; }
-keep class aws.smithy.kotlin.** { *; }
-dontwarn org.slf4j.**
