plugins {
    java
}

subprojects {
    apply(plugin = "java-library")

    group = rootProject.property("group") as String
    version = rootProject.property("version") as String

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    repositories {
        mavenCentral()
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
