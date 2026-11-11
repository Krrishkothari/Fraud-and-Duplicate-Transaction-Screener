// Starts the Spring Boot backend and the Vite frontend together, with each output line prefixed by its
// source. Installs the frontend dependencies first if they are missing or out of date. Ctrl+C stops both,
// and if either one exits, the other is stopped too.

import { spawn, spawnSync } from 'node:child_process'
import { existsSync, statSync } from 'node:fs'
import path from 'node:path'
import readline from 'node:readline'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const isWindows = process.platform === 'win32'
const npm = isWindows ? 'npm.cmd' : 'npm'

const services = [
  {
    name: 'backend',
    color: 36,
    cwd: path.join(root, 'backend'),
    command: isWindows ? 'mvnw.cmd' : './mvnw',
    args: ['spring-boot:run'],
  },
  {
    name: 'frontend',
    color: 35,
    cwd: path.join(root, 'frontend'),
    command: npm,
    args: ['run', 'dev'],
  },
]

installFrontendIfNeeded()

const children = services.map(start)
let stopping = false

process.on('SIGINT', () => stopAll(0))
process.on('SIGTERM', () => stopAll(0))

// npm writes node_modules/.package-lock.json on every install, so a lockfile newer than it means
// dependencies changed since the last install, for example after a git pull. npm ci installs exactly
// what the lockfile lists and never rewrites it.
function installFrontendIfNeeded() {
  const frontend = path.join(root, 'frontend')
  const installed = path.join(frontend, 'node_modules', '.package-lock.json')
  const lockfile = path.join(frontend, 'package-lock.json')
  if (existsSync(installed) && statSync(installed).mtimeMs >= statSync(lockfile).mtimeMs) {
    return
  }
  console.log('Installing frontend dependencies...')
  const result = spawnSync(npm, ['ci'], { cwd: frontend, stdio: 'inherit', shell: isWindows })
  if (result.status !== 0) {
    process.exit(result.status ?? 1)
  }
}

function start(service) {
  const child = spawn(service.command, service.args, {
    cwd: service.cwd,
    env: { ...process.env, FORCE_COLOR: '1' },
    // Its own process group on macOS and Linux, so stopping it also stops the JVM Maven forks.
    detached: !isWindows,
    shell: isWindows,
  })
  const label = `\x1b[${service.color}m[${service.name}]\x1b[0m `
  prefixLines(child.stdout, process.stdout, label)
  prefixLines(child.stderr, process.stderr, label)

  child.on('error', (error) => {
    console.error(`${label}could not start: ${error.message}`)
    stopAll(1)
  })
  child.on('exit', (code, signal) => {
    if (!stopping) {
      console.error(`${label}exited (${signal ?? `code ${code}`}), stopping the other service`)
      stopAll(code ?? 1)
    }
  })
  return child
}

function prefixLines(input, output, label) {
  readline.createInterface({ input }).on('line', (line) => output.write(label + line + '\n'))
}

function stopAll(exitCode) {
  if (stopping) {
    return
  }
  stopping = true
  for (const child of children) {
    if (child.exitCode === null && child.pid !== undefined) {
      stopTree(child)
    }
  }
  process.exitCode = exitCode
}

function stopTree(child) {
  try {
    if (isWindows) {
      spawnSync('taskkill', ['/pid', String(child.pid), '/T', '/F'], { stdio: 'ignore' })
    } else {
      process.kill(-child.pid, 'SIGTERM')
    }
  } catch {
    // The process already exited between the check and the kill.
  }
}
