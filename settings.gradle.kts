pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    ivy {
      url = uri("https://github.com/k2-fsa/sherpa-onnx/releases/download")
      patternLayout {
        artifact("v[revision]/[module]-[revision].[ext]")
      }
      metadataSources {
        artifact()
      }
      content {
        includeModule("com.k2fsa.sherpa.onnx", "sherpa-onnx")
      }
    }
    ivy {
      url = uri("https://huggingface.co/csukuangfj/sherpa-onnx-libs/resolve/main/android/aar")
      patternLayout {
        artifact("[revision]/[module]-[revision].[ext]")
        artifact("[module]-[revision].[ext]")
      }
      metadataSources {
        artifact()
      }
      content {
        includeModule("com.k2fsa.sherpa.onnx", "sherpa-onnx")
      }
    }
  }
}

rootProject.name = "Pauline"

include(":app")
