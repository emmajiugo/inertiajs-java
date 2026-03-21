plugins {
    application
}

application {
    mainClass.set("io.github.emmajiugo.inertia.example.App")
}

dependencies {
    implementation(project(":inertiajs-javalin"))
    implementation("io.javalin:javalin:7.1.0")
    implementation("org.slf4j:slf4j-simple:2.0.16")
}
