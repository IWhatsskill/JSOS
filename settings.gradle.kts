pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Keep the vendor repository isolated to the Rokid CXR artifacts.
        exclusiveContent {
            forRepository {
                maven {
                    name = "Rokid"
                    url = uri("https://maven.rokid.com/repository/maven-public/")
                }
            }
            filter {
                includeGroup("com.rokid.cxr")
            }
        }
    }
}

rootProject.name = "JSOS"

include(":phone-app")
include(":glasses-app")
include(":shared")
include(":watch-app")
