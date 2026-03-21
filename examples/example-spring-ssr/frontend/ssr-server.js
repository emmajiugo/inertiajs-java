import http from 'node:http'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const isDev = process.env.NODE_ENV !== 'production'
const port = parseInt(process.env.SSR_PORT || '13714', 10)

let vite
let render

if (isDev) {
  // In dev mode, create a Vite server that loads the project's vite.config.ts
  // (which includes the Vue plugin needed to parse .vue files)
  const { createServer } = await import('vite')
  vite = await createServer({
    root: __dirname,
    server: { middlewareMode: true },
    appType: 'custom',
  })
} else {
  // In production, load the pre-built SSR bundle once
  const mod = await import(resolve(__dirname, 'dist/ssr/ssr.js'))
  render = mod.default
}

const server = http.createServer(async (req, res) => {
  if (req.method === 'POST' && req.url === '/render') {
    let body = ''
    req.on('data', (chunk) => { body += chunk })
    req.on('end', async () => {
      try {
        // In dev mode, load the module on each request for HMR
        if (isDev) {
          const mod = await vite.ssrLoadModule(resolve(__dirname, 'src/ssr.ts'))
          render = mod.default
        }

        const page = JSON.parse(body)
        const result = await render(page)
        const response = JSON.stringify({
          head: result.head || [],
          body: result.body || '',
        })
        res.writeHead(200, { 'Content-Type': 'application/json' })
        res.end(response)
      } catch (err) {
        console.error('SSR render error:', err)
        res.writeHead(500, { 'Content-Type': 'application/json' })
        res.end(JSON.stringify({ error: err.message }))
      }
    })
  } else {
    res.writeHead(404)
    res.end()
  }
})

server.listen(port, '127.0.0.1', () => {
  console.log(`Inertia SSR server running on http://127.0.0.1:${port}`)
})
