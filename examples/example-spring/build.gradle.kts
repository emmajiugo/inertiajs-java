plugins {
    id("org.springframework.boot") version "4.0.4"
    id("io.spring.dependency-management") version "1.1.7"
}

dependencies {
    implementation(project(":inertiajs-spring"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("io.github.emmajiugo:javalidator-spring:1.0.0")
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
    workingDir = projectDir
    commandLine(
        "sh", "-c",
        """
        cleanup() {
            echo ""
            echo "Shutting down..."
            kill ${'$'}VITE_PID ${'$'}BOOT_PID 2>/dev/null
            wait ${'$'}VITE_PID ${'$'}BOOT_PID 2>/dev/null
        }
        trap cleanup EXIT INT TERM

        # Kill any leftover processes on our ports
        lsof -ti:8080 | xargs kill -9 2>/dev/null || true
        sleep 1

        # Start Spring Boot in the background
        ${rootDir}/gradlew :examples:example-spring:bootRun --args='--spring.profiles.active=dev' &
        BOOT_PID=${'$'}!
        echo "⏳ Waiting for Spring Boot on port 8080..."
        for i in ${'$'}(seq 1 60); do
            if curl -s -o /dev/null http://localhost:8080 2>/dev/null; then
                break
            fi
            if ! kill -0 ${'$'}BOOT_PID 2>/dev/null; then
                echo "❌ Spring Boot failed to start"
                exit 1
            fi
            sleep 1
        done
        if ! curl -s -o /dev/null http://localhost:8080 2>/dev/null; then
            echo "❌ Spring Boot did not start within 60 seconds"
            exit 1
        fi
        echo "✓ Spring Boot started on http://localhost:8080"

        # Start Vite dev server
        (cd "${projectDir}/frontend" && npx vite --port 5173 --strictPort) &
        VITE_PID=${'$'}!
        sleep 1
        echo "✓ Vite dev server started on http://localhost:5173"
        echo ""
        echo "🚀 Open http://localhost:5173"
        echo ""

        # Wait for Spring Boot (main process)
        wait ${'$'}BOOT_PID
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
