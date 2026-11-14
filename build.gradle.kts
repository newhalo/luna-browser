plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false
    // Kotlin 2 ships the Compose compiler as a plugin of its own, versioned with Kotlin.
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false
}
