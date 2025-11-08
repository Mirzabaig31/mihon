buildscript {
    dependencies {
        classpath(libs.android.shortcut.gradle)
    }
}

plugins {
    alias(kotlinx.plugins.serialization) apply false
    alias(libs.plugins.aboutLibraries) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.moko) apply false
    alias(libs.plugins.sqldelight) apply false
}

// Force safe version of JDOM2 to fix XXE vulnerability
// See: https://github.com/advisories/GHSA-2363-cqg2-863c
subprojects {
    configurations.all {
        resolutionStrategy {
            force("org.jdom:jdom2:2.0.6.1")
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
