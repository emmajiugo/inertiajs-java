plugins {
    id("org.springframework.boot") version "4.0.4"
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
    description = "Starts Spring Boot, then Vite dev server"
    group = "application"
    dependsOn("npmInstall")
    workingDir = file("frontend")
    commandLine(
        "sh", "-c",
        """
        cd ${projectDir} && ${rootDir}/gradlew :examples:example-spring:bootRun --args='--spring.profiles.active=dev' &
        BOOT_PID=${'$'}!
        echo "⏳ Waiting for Spring Boot on port 8080..."
        while ! curl -s http://localhost:8080 > /dev/null 2>&1; do sleep 0.5; done
        echo "✓ Spring Boot started on http://localhost:8080"
        cd ${projectDir}/frontend && npx vite &
        VITE_PID=${'$'}!
        sleep 1
        echo "✓ Vite dev server started on http://localhost:5173"
        echo ""
        echo "🚀 Open http://localhost:5173"
        echo ""
        wait ${'$'}BOOT_PID
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
