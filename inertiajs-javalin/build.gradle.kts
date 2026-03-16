dependencies {
    api(project(":inertiajs-core"))

    compileOnly("io.javalin:javalin:7.1.0")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("io.javalin:javalin:7.1.0")
}
