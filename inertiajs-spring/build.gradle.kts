dependencies {
    api(project(":inertiajs-core"))

    compileOnly("org.springframework.boot:spring-boot-starter-web:4.0.4")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:4.0.4")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("org.springframework.boot:spring-boot-starter-test:4.0.4")
    testImplementation("org.springframework.boot:spring-boot-starter-web:4.0.4")
}
