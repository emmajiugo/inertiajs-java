dependencies {
    api(project(":inertiajs-core"))

    compileOnly("org.springframework.boot:spring-boot-starter-web:3.4.1")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:3.4.1")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.4.1")
    testImplementation("org.springframework.boot:spring-boot-starter-web:3.4.1")
}
