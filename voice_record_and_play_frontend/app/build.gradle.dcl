androidApplication {
    namespace = "org.example.app"

    dependencies {
        implementation("org.apache.commons:commons-text:1.11.0")
        implementation(project(":utilities"))

        // Android TV (Leanback) UI
        implementation("androidx.leanback:leanback:1.2.0-alpha04")

        // Persisted list UI + helpers
        implementation("androidx.recyclerview:recyclerview:1.3.2")
        implementation("androidx.core:core-ktx:1.13.1")
    }
}
