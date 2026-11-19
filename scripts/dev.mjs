// Starts the Spring Boot backend and the Vite frontend together, with each output line prefixed by its
// source. Installs the frontend dependencies first if they are missing or out of date, and frees the two
// ports from a server an earlier run left behind. Ctrl+C or closing the terminal stops both, and if either
// one exits, the other is stopped too.

import { spawn, spawnSync } from 'node:child_process'
import { existsSync, statSync } from 'node:fs'
import net from 'node:net'
import path from 'node:path'
import readline from 'node:readline'
import { setTimeout as sleep } from 'node:timers/promises'
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
    port: 8080,
  },
  {
    name: 'frontend',
    color: 35,
    cwd: path.join(root, 'frontend'),
    command: npm,
    args: ['run', 'dev'],
    // Vite would move to the next free port, but the backend only accepts requests from this one.
    port: 5173,
  },
]

installFrontendIfNeeded()
for (const service of services) {
  await freePort(service)
}

const children = services.map(start)
let stopping = false

process.on('SIGINT', () => stopAll(0))
process.on('SIGTERM', () => stopAll(0))
// Closing the terminal sends SIGHUP. The services run in their own sessions and never receive it, so
// without this they would outlive the terminal and keep their ports.
process.on('SIGHUP', () => stopAll(0))
process.on('exit', () => stopAll(process.exitCode ?? 0))

/**
 * Makes sure a service's port is free before it starts. A server from this project that an earlier
 * npm run dev left running after its terminal closed is stopped; anything else on the port is reported,
 * and the script exits instead of starting a service that would fail.
 */
async function freePort(service) {
  if (await portIsFree(service.port)) {
    return
  }
  const holder = isWindows ? null : portHolder(service.port)
  if (holder !== null && isLeftover(holder)) {
    console.log(
      `Stopping the ${service.name} an earlier npm run dev left running on port ${service.port} (PID ${holder.pid}).`,
    )
    stopGroup(holder.pgid)
    if (await waitUntilFree(service.port)) {
      return
    }
  }
  console.error(`Port ${service.port} is already in use, so the ${service.name} cannot start.`)
  if (holder !== null) {
    console.error(`  PID ${holder.pid}: ${holder.command.slice(0, 160)}`)
    console.error(`Stop it with: kill ${holder.pid}`)
  } else if (isWindows) {
    console.error(`Find it with: netstat -ano | findstr :${service.port}`)
  }
  process.exit(1)
}

/** Whether nothing on this machine accepts connections on the port, over IPv4 or IPv6. */
async function portIsFree(port) {
  const answers = await Promise.all(['127.0.0.1', '::1'].map((host) => acceptsConnection(host, port)))
  return !answers.includes(true)
}

function acceptsConnection(host, port) {
  return new Promise((resolve) => {
    const socket = net.connect({ host, port })
    socket.setTimeout(1000)
    socket.once('connect', () => {
      socket.destroy()
      resolve(true)
    })
    socket.once('timeout', () => {
      socket.destroy()
      resolve(false)
    })
    socket.once('error', () => resolve(false))
  })
}

async function waitUntilFree(port, timeoutMs = 15_000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    if (await portIsFree(port)) {
      return true
    }
    await sleep(250)
  }
  return false
}

/** The process listening on a port, with its parent, process group and command line, or null if unknown. */
function portHolder(port) {
  const lsof = spawnSync('lsof', ['-t', `-iTCP:${port}`, '-sTCP:LISTEN'], { encoding: 'utf8' })
  const pid = Number((lsof.stdout ?? '').trim().split('\n')[0])
  return Number.isInteger(pid) && pid > 0 ? describeProcess(pid) : null
}

function describeProcess(pid) {
  const ps = spawnSync('ps', ['-o', 'ppid=,pgid=,command=', '-p', String(pid)], { encoding: 'utf8' })
  const match = /^\s*(\d+)\s+(\d+)\s+(.*)$/s.exec((ps.stdout ?? '').trim())
  return match ? { pid, ppid: Number(match[1]), pgid: Number(match[2]), command: match[3] } : null
}

/**
 * Whether a process is one of this project's servers whose npm run dev is gone: its command runs from this
 * repository, and the leader of its process group, which this script started, has lost its parent. A server
 * started by hand in another terminal still has that terminal's shell as a parent, so it is left alone.
 */
function isLeftover(holder) {
  if (!holder.command.includes(root)) {
    return false
  }
  const leader = describeProcess(holder.pgid) ?? holder
  return leader.ppid === 1
}

function stopGroup(pgid) {
  try {
    process.kill(-pgid, 'SIGTERM')
  } catch {
    // The group already exited.
  }
}

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
      stopGroup(child.pid)
    }
  } catch {
    // The process already exited between the check and the kill.
  }
}
