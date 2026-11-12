pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "OrchConsole"
include(":app")    // غلاف TWA الحالي (سيرفري المثبت): ما يتغيّر
include(":native") // سيرفري الأصلي (Kotlin + Compose): يتطور جنبه بحزمة تجريبية
