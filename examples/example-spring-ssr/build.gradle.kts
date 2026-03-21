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

// ── SSR build ────────────────────────────────────────────────────────

tasks.register<Exec>("ssrBuild") {
    description = "Build SSR bundle"
    group = "frontend"
    dependsOn("npmInstall")
    workingDir = file("frontend")
    commandLine("npm", "run", "build:ssr")
}

// ── Dev mode: run Vite + SSR server + Spring Boot ────────────────────

tasks.register<Exec>("dev") {
    description = "Starts SSR server, Spring Boot, then Vite dev server"
    group = "application"
    dependsOn("npmInstall")
    workingDir = projectDir
    commandLine(
        "sh", "-c",
        """
        FRONTEND_DIR="${projectDir}/frontend"

        cleanup() {
            echo ""
            echo "Shutting down..."
            kill ${'$'}VITE_PID ${'$'}SSR_PID ${'$'}BOOT_PID 2>/dev/null
            wait ${'$'}VITE_PID ${'$'}SSR_PID ${'$'}BOOT_PID 2>/dev/null
        }
        trap cleanup EXIT INT TERM

        # Kill any leftover processes on our ports
        lsof -ti:8080 -ti:13714 | xargs kill -9 2>/dev/null || true
        sleep 1

        # 1. Start SSR server first (so Spring Boot can connect to it)
        node "${'$'}FRONTEND_DIR/ssr-server.js" &
        SSR_PID=${'$'}!
        sleep 1
        echo "✓ SSR server started on http://127.0.0.1:13714"

        # 2. Start Spring Boot in the background
        ${rootDir}/gradlew :examples:example-spring-ssr:bootRun --args='--spring.profiles.active=dev' &
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

        # 3. Start Vite dev server last (it proxies to Spring Boot)
        npx --prefix "${'$'}FRONTEND_DIR" vite --port 5173 --strictPort &
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

// ── Production build: frontend + SSR + bootJar ──────────────────────

tasks.register("buildProd") {
    description = "Builds frontend assets, SSR bundle, then packages the Spring Boot jar"
    group = "build"
    dependsOn("npmBuild", "ssrBuild")
    finalizedBy(tasks.named("bootJar"))
}
