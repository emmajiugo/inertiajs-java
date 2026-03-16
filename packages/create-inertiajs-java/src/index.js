#!/usr/bin/env node

import prompts from 'prompts'
import kleur from 'kleur'
const { bold, cyan, green, red, dim, yellow } = kleur
import fs from 'node:fs'
import path from 'node:path'

function parseArgs() {
  const args = process.argv.slice(2)
  const parsed = {}
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--backend' && args[i + 1]) parsed.backend = args[++i]
    else if (args[i] === '--frontend' && args[i + 1]) parsed.frontend = args[++i]
  }
  return parsed
}

async function main() {
  const cliArgs = parseArgs()
  const projectDir = process.cwd()

  console.log()
  console.log(bold(cyan('  create-inertiajs-java')))
  console.log(dim('  Add Inertia.js frontend to your Java project\n'))

  // Check we're in a project root (has build.gradle.kts, pom.xml, or src/)
  const hasBuildFile = fs.existsSync(path.join(projectDir, 'build.gradle.kts'))
    || fs.existsSync(path.join(projectDir, 'build.gradle'))
    || fs.existsSync(path.join(projectDir, 'pom.xml'))
    || fs.existsSync(path.join(projectDir, 'src'))

  if (!hasBuildFile) {
    console.log(yellow('  ⚠ No build.gradle.kts, pom.xml, or src/ found in current directory.'))
    const { proceed } = await prompts({
      type: 'confirm', name: 'proceed', message: 'Continue anyway?', initial: false,
    })
    if (!proceed) { console.log(red('\n  Cancelled.\n')); process.exit(1) }
  }

  // Check if frontend/ already exists
  if (fs.existsSync(path.join(projectDir, 'frontend'))) {
    console.log(red('  ✗ frontend/ directory already exists. Remove it first or run from a different directory.\n'))
    process.exit(1)
  }

  const questions = []
  if (!cliArgs.frontend) questions.push({
    type: 'select', name: 'frontend', message: 'Frontend framework:',
    choices: [
      { title: 'Vue', value: 'vue' },
      { title: 'React', value: 'react' },
      { title: 'Svelte', value: 'svelte' },
    ],
  })
  if (!cliArgs.backend) questions.push({
    type: 'select', name: 'backend', message: 'Backend framework (for dependency instructions):',
    choices: [
      { title: 'Spring Boot', value: 'spring' },
      { title: 'Javalin', value: 'javalin' },
    ],
  })

  const response = questions.length > 0
    ? await prompts(questions, { onCancel: () => { console.log(red('\n  Cancelled.\n')); process.exit(1) } })
    : {}

  const frontend = cliArgs.frontend || response.frontend
  const backend = cliArgs.backend || response.backend

  console.log()

  // 1. Scaffold frontend/
  scaffoldFrontend(projectDir, frontend)

  // 2. Scaffold templates
  scaffoldTemplates(projectDir)

  // 3. Print next steps
  await printNextSteps(backend, frontend)
}

function scaffoldFrontend(projectDir, frontend) {
  const frontendDir = path.join(projectDir, 'frontend')
  fs.mkdirSync(path.join(frontendDir, 'src/pages'), { recursive: true })

  // package.json
  const deps = {
    vue:    { dep: '"vue": "^3.5.0", "@inertiajs/vue3": "^2.0.0"', devDep: '"@vitejs/plugin-vue": "^6.0.0"' },
    react:  { dep: '"react": "^19.0.0", "react-dom": "^19.0.0", "@inertiajs/react": "^2.0.0"', devDep: '"@vitejs/plugin-react": "^4.3.0", "@types/react": "^19.0.0", "@types/react-dom": "^19.0.0"' },
    svelte: { dep: '"@inertiajs/svelte": "^2.0.0"', devDep: '"@sveltejs/vite-plugin-svelte": "^5.0.0", "svelte": "^5.0.0"' },
  }

  const projectName = path.basename(projectDir)

  write(path.join(frontendDir, 'package.json'), `{
  "name": "${projectName}-frontend",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build"
  },
  "dependencies": {
    ${deps[frontend].dep}
  },
  "devDependencies": {
    ${deps[frontend].devDep},
    "vite": "^8.0.0",
    "typescript": "^5.7.0",
    "@types/node": "^22.0.0"
  }
}
`)

  // vite.config.ts
  const plugins = {
    vue:    { import: "import vue from '@vitejs/plugin-vue'", use: 'vue()' },
    react:  { import: "import react from '@vitejs/plugin-react'", use: 'react()' },
    svelte: { import: "import { svelte } from '@sveltejs/vite-plugin-svelte'", use: 'svelte()' },
  }

  write(path.join(frontendDir, 'vite.config.ts'), `import { defineConfig } from 'vite'
${plugins[frontend].import}

const backendUrl = process.env.BACKEND_URL || 'http://localhost:8080'

export default defineConfig({
  plugins: [${plugins[frontend].use}],
  server: {
    port: 5173,
    proxy: {
      '/': {
        target: backendUrl,
        changeOrigin: true,
        bypass(req: { url?: string }) {
          if (req.url?.startsWith('/src') || req.url?.startsWith('/@') || req.url?.startsWith('/node_modules')) {
            return req.url
          }
        },
      },
    },
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
    rolldownOptions: {
      input: 'src/app.ts',
      output: {
        entryFileNames: 'assets/app.js',
        chunkFileNames: 'assets/[name].js',
        assetFileNames: 'assets/[name][extname]',
      },
    },
  },
})
`)

  // tsconfig.json
  const tsconfig = {
    compilerOptions: {
      target: 'ES2020', module: 'ESNext', moduleResolution: 'bundler',
      strict: true, resolveJsonModule: true, isolatedModules: true,
      esModuleInterop: true, types: ['node'],
      lib: ['ES2020', 'DOM', 'DOM.Iterable'], skipLibCheck: true, noEmit: true,
    },
    include: ['src/**/*.ts', 'vite.config.ts'],
  }
  if (frontend === 'vue') tsconfig.include.push('src/**/*.vue')
  if (frontend === 'react') { tsconfig.compilerOptions.jsx = 'react-jsx'; tsconfig.include.push('src/**/*.tsx') }
  write(path.join(frontendDir, 'tsconfig.json'), JSON.stringify(tsconfig, null, 2) + '\n')

  // vite-env.d.ts
  write(path.join(frontendDir, 'src/vite-env.d.ts'), '/// <reference types="vite/client" />\n')

  // App bootstrap + sample page
  if (frontend === 'vue') scaffoldVue(frontendDir)
  else if (frontend === 'react') scaffoldReact(frontendDir)
  else if (frontend === 'svelte') scaffoldSvelte(frontendDir)

  log('frontend/')
}

function scaffoldVue(dir) {
  write(path.join(dir, 'src/app.ts'), `import { createApp, h, type DefineComponent } from 'vue'
import { createInertiaApp } from '@inertiajs/vue3'

createInertiaApp({
  resolve: (name: string) => {
    const pages = import.meta.glob<DefineComponent>('./pages/**/*.vue', { eager: true })
    return pages[\`./pages/\${name}.vue\`]
  },
  setup({ el, App, props, plugin }) {
    createApp({ render: () => h(App, props) })
      .use(plugin)
      .mount(el)
  },
})
`)

  write(path.join(dir, 'src/pages/Home.vue'), `<script setup lang="ts">
</script>

<template>
  <div style="max-width: 600px; margin: 40px auto; font-family: sans-serif;">
    <h1>Welcome to Inertia.js + Java</h1>
    <p>Edit <code>frontend/src/pages/Home.vue</code> to get started.</p>
  </div>
</template>
`)
}

function scaffoldReact(dir) {
  write(path.join(dir, 'src/app.tsx'), `import { createInertiaApp } from '@inertiajs/react'
import { createRoot } from 'react-dom/client'

createInertiaApp({
  resolve: (name: string) => {
    const pages = import.meta.glob('./pages/**/*.tsx', { eager: true }) as Record<string, { default: React.ComponentType }>
    return pages[\`./pages/\${name}.tsx\`]
  },
  setup({ el, App, props }) {
    createRoot(el).render(<App {...props} />)
  },
})
`)

  write(path.join(dir, 'src/pages/Home.tsx'), `export default function Home() {
  return (
    <div style={{ maxWidth: 600, margin: '40px auto', fontFamily: 'sans-serif' }}>
      <h1>Welcome to Inertia.js + Java</h1>
      <p>Edit <code>frontend/src/pages/Home.tsx</code> to get started.</p>
    </div>
  )
}
`)

  // Update vite.config to use app.tsx
  const viteConfig = fs.readFileSync(path.join(dir, 'vite.config.ts'), 'utf-8')
  write(path.join(dir, 'vite.config.ts'), viteConfig.replace('src/app.ts', 'src/app.tsx'))
}

function scaffoldSvelte(dir) {
  write(path.join(dir, 'src/app.ts'), `import { createInertiaApp } from '@inertiajs/svelte'

createInertiaApp({
  resolve: (name: string) => {
    const pages = import.meta.glob('./pages/**/*.svelte', { eager: true }) as Record<string, { default: any }>
    return pages[\`./pages/\${name}.svelte\`]
  },
  setup({ el, App }) {
    new App({ target: el })
  },
})
`)

  write(path.join(dir, 'src/pages/Home.svelte'), `<div style="max-width: 600px; margin: 40px auto; font-family: sans-serif;">
  <h1>Welcome to Inertia.js + Java</h1>
  <p>Edit <code>frontend/src/pages/Home.svelte</code> to get started.</p>
</div>
`)
}

function scaffoldTemplates(projectDir) {
  const templatesDir = path.join(projectDir, 'src/main/resources/templates')
  fs.mkdirSync(templatesDir, { recursive: true })

  if (!fs.existsSync(path.join(templatesDir, 'app.html'))) {
    write(path.join(templatesDir, 'app.html'), `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>My App</title>
</head>
<body>
    @inertia
    <script type="module" src="/assets/app.js"></script>
</body>
</html>
`)
    log('src/main/resources/templates/app.html')
  } else {
    console.log(dim('  ⊘ templates/app.html already exists, skipping'))
  }

  if (!fs.existsSync(path.join(templatesDir, 'app-dev.html'))) {
    write(path.join(templatesDir, 'app-dev.html'), `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>My App</title>
</head>
<body>
    @inertia
    <script type="module" src="http://localhost:5173/src/app.ts"></script>
</body>
</html>
`)
    log('src/main/resources/templates/app-dev.html')
  } else {
    console.log(dim('  ⊘ templates/app-dev.html already exists, skipping'))
  }
}

async function waitForEnter(message) {
  await prompts({
    type: 'invisible',
    name: 'continue',
    message: dim(message || 'Press Enter to continue...'),
  })
}

async function printNextSteps(backend, frontend) {
  const dep = backend === 'spring' ? 'inertiajs-spring' : 'inertiajs-javalin'

  console.log()
  console.log(green('  ✓ Done!') + ' Follow these steps to finish setup:\n')

  // Step 1: Add dependency
  console.log(bold('  Step 1 of 4') + ' — Add the dependency to your project:\n')
  console.log(dim('     Gradle (build.gradle.kts):'))
  console.log(`     implementation("io.inertia:${dep}:0.1.0-SNAPSHOT")\n`)
  console.log(dim('     Maven (pom.xml):'))
  console.log(`     <dependency>`)
  console.log(`       <groupId>io.inertia</groupId>`)
  console.log(`       <artifactId>${dep}</artifactId>`)
  console.log(`       <version>0.1.0-SNAPSHOT</version>`)
  console.log(`     </dependency>\n`)

  await waitForEnter('Done? Press Enter for next step...')

  // Step 2: Configure
  if (backend === 'spring') {
    console.log(bold('\n  Step 2 of 4') + ' — Add to application.properties:\n')
    console.log(dim('     # application.properties'))
    console.log('     inertia.version=1.0.0')
    console.log('     inertia.template-path=templates/app.html\n')
    console.log(dim('     # application-dev.properties (create this file)'))
    console.log('     inertia.template-path=templates/app-dev.html\n')
  } else {
    console.log(bold('\n  Step 2 of 4') + ' — Set up InertiaEngine in your app:\n')
    console.log(`     var config = InertiaConfig.builder()`)
    console.log(`         .version("1.0.0")`)
    console.log(`         .templateResolver(new ClasspathTemplateResolver(`)
    console.log(`             "true".equals(System.getenv("DEV"))`)
    console.log(`                 ? "templates/app-dev.html"`)
    console.log(`                 : "templates/app.html"))`)
    console.log(`         .build();`)
    console.log(`     var engine = new InertiaEngine(config);`)
    console.log(`     var plugin = new InertiaPlugin(engine);`)
    console.log()
    console.log(dim('     Then inside Javalin.create(cfg -> { ... }):'))
    console.log(`     plugin.configure(cfg);\n`)
  }

  await waitForEnter('Done? Press Enter for next step...')

  // Step 3: Write a controller
  console.log(bold('\n  Step 3 of 4') + ' — Write your first controller:\n')
  if (backend === 'spring') {
    console.log(`     @Controller`)
    console.log(`     public class HomeController {`)
    console.log(``)
    console.log(`         @Autowired private Inertia inertia;`)
    console.log(``)
    console.log(`         @GetMapping("/")`)
    console.log(`         public void home(HttpServletRequest req,`)
    console.log(`                          HttpServletResponse res) throws IOException {`)
    console.log(`             inertia.render(req, res, "Home");`)
    console.log(`         }`)
    console.log(`     }\n`)
  } else {
    console.log(`     var inertia = plugin.inertia();`)
    console.log(`     cfg.routes.get("/", ctx -> inertia.render(ctx, "Home"));\n`)
  }

  await waitForEnter('Done? Press Enter for final step...')

  // Step 4: Run
  console.log(bold('\n  Step 4 of 4') + ' — Run your app:\n')
  if (backend === 'spring') {
    console.log(`     ${dim('# Terminal 1 — backend')}`)
    console.log(`     ./gradlew bootRun --args='--spring.profiles.active=dev'\n`)
    console.log(`     ${dim('# Terminal 2 — frontend')}`)
  } else {
    console.log(`     ${dim('# Terminal 1 — backend')}`)
    console.log(`     DEV=true ./gradlew run\n`)
    console.log(`     ${dim('# Terminal 2 — frontend')}`)
  }
  console.log(`     cd frontend && npm install && npm run dev\n`)
  console.log(`     Open ${cyan('http://localhost:5173')}\n`)
  console.log(green('  Happy coding!\n'))
}

function write(filePath, content) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true })
  fs.writeFileSync(filePath, content)
}

function log(what) {
  console.log(green('  ✓') + ` Created ${what}`)
}

main().catch(console.error)
