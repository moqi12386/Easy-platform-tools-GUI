pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://jitpack.io")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://jitpack.io")
        google()
        mavenCentral()
        // 金标联盟 SharedViewKit（一镜到底无缝动画），目前只有 HTTP 仓库
        maven("http://nexus.itgsa.com:5566/repository/sharedViewKit/") {
            isAllowInsecureProtocol = true
            content { includeGroup("gsai.sdk") }
        }
    }
}
rootProject.name = "EasyPT"
include(":app")
