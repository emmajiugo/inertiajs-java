plugins {
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

dependencies {
    implementation(project(":inertiajs-spring"))
    implementation("org.springframework.boot:spring-boot-starter-web")
}

// ── npm install ──────────────────────────────────────────────────────

tasks.register<Exec>("npmInstall") {
    description = "Install frontend npm dependencies"
    group = "frontend"
    workingDir = file("frontend")
    commandLine("npm", "install")
    inputs.file("frontend/package.json")
    outputs.dir("frontend/node_modules")
}

// ── Frontend production build ────────────────────────────────────────

tasks.register<Exec>("npmBuild") {
    description = "Build frontend assets for production"
    group = "frontend"
    dependsOn("npmInstall")
    workingDir = file("frontend")
    commandLine("npm", "run", "build")
}

// ── Dev mode: run both servers ───────────────────────────────────────

tasks.register<Exec>("dev") {
    description = "Starts Vite dev server and Spring Boot together"
    group = "application"
    dependsOn("npmInstall")
    workingDir = file("frontend")
    // Use npx concurrently to run both processes
    // Falls back to a shell command that runs both
    commandLine(
        "sh", "-c",
        """
        npm run dev &
        VITE_PID=${'$'}!
        sleep 2
        echo "✓ Vite dev server started on http://localhost:5173"
        echo "✓ Starting Spring Boot with dev profile..."
        cd ${projectDir} && ${rootDir}/gradlew :examples:example-spring:bootRun --args='--spring.profiles.active=dev'
        kill ${'$'}VITE_PID 2>/dev/null
        """.trimIndent()
    )
}

// ── Production build: frontend + bootJar ─────────────────────────────

tasks.register("buildProd") {
    description = "Builds frontend assets then packages the Spring Boot jar"
    group = "build"
    dependsOn("npmBuild")
    finalizedBy(tasks.named("bootJar"))
}
