import { spawn, type ChildProcess } from 'node:child_process';
import { existsSync } from 'node:fs';
import net from 'node:net';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { expect, test } from './fixtures';

/**
 * Plugin-message side channel (the transport Apollo-style server APIs use):
 *
 *   wire-level client (scripts/gw_relay_smoke.mjs)
 *     - WS - PlaceholderServer --backend - TCP - McBridge - McProtocolStub
 *
 * The stub is a vanilla 26.1.2 backend (login, configuration with known
 * packs/keepalive/ping, play) that echoes every plugin message. The smoke
 * asserts config-phase queueing, minecraft:register, binary and
 * compressed-size payload round trips, backend commands, and the gateway's
 * oversized-payload refusal. Both processes run from the production
 * shadowJar.
 */

const GW_PORT = 28351;
const MC_PORT = 28352;
const BOOT_TIMEOUT = 30_000;

const REPO_ROOT = fileURLToPath(new URL('../..', import.meta.url));
const PLACEHOLDER_JAR = join(
  REPO_ROOT, 'gateway', 'PlaceholderServer', 'build', 'libs', 'PlaceholderServer-1.0.0.jar');
const RELAY_SCRIPT = join(REPO_ROOT, 'scripts', 'gw_relay_smoke.mjs');

const jarBuilt = existsSync(PLACEHOLDER_JAR);

const procLog: string[] = [];
let mcStub: ChildProcess | undefined;
let gateway: ChildProcess | undefined;

function logTail(): string {
  return procLog.join('').slice(-3_000);
}

async function waitForPort(port: number, timeoutMs: number, label: string): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const ok = await new Promise<boolean>((resolve) => {
      const socket = net.connect(port, '127.0.0.1');
      socket.once('connect', () => {
        socket.destroy();
        resolve(true);
      });
      socket.once('error', () => resolve(false));
      setTimeout(() => {
        socket.destroy();
        resolve(false);
      }, 1000);
    });
    if (ok) {
      return;
    }
    await new Promise((r) => setTimeout(r, 250));
  }
  throw new Error(`${label} did not open port ${port} within ${timeoutMs}ms\n${logTail()}`);
}

test.describe('plugin message relay', () => {
  test.skip(!jarBuilt, 'gateway jar is not built (run ./gradlew :PlaceholderServer:shadowJar first)');

  test.beforeAll(async () => {
    const stdio: ('pipe' | 'ignore')[] = ['ignore', 'pipe', 'pipe'];
    mcStub = spawn('java', [
      '-cp', PLACEHOLDER_JAR,
      'net.lax1dude.eaglercraft.v2_6.placeholder_server.mc.McProtocolStub',
      '--port', String(MC_PORT), '--interval-ms', '1000', '--verbose',
    ], { stdio });
    gateway = spawn('java', [
      '-jar', PLACEHOLDER_JAR,
      '--port', String(GW_PORT),
      '--backend', `127.0.0.1:${MC_PORT}`,
      '--verbose',
    ], { stdio });
    for (const [label, proc] of [['mc-stub', mcStub], ['gateway', gateway]] as const) {
      proc.stdout?.on('data', (d: Buffer) => procLog.push(`[${label}] ${d.toString()}`));
      proc.stderr?.on('data', (d: Buffer) => procLog.push(`[${label}] ${d.toString()}`));
      proc.on('exit', (code, signal) => procLog.push(`\n[${label}] exited ${code} ${signal}\n`));
    }
    await waitForPort(MC_PORT, BOOT_TIMEOUT, 'McProtocolStub');
    await waitForPort(GW_PORT, BOOT_TIMEOUT, 'PlaceholderServer --backend');
  });

  test.afterAll(async () => {
    for (const proc of [gateway, mcStub]) {
      if (proc && proc.exitCode === null && !proc.killed) {
        proc.kill('SIGTERM');
      }
    }
    await new Promise((r) => setTimeout(r, 500));
    for (const proc of [gateway, mcStub]) {
      if (proc && proc.exitCode === null && !proc.killed) {
        proc.kill('SIGKILL');
      }
    }
  });

  test('relays plugin messages client <-> gateway <-> backend', async () => {
    const child = spawn('node', [RELAY_SCRIPT, String(GW_PORT)], {
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    let out = '';
    child.stdout?.on('data', (d: Buffer) => { out += d.toString(); });
    child.stderr?.on('data', (d: Buffer) => { out += d.toString(); });
    const code = await new Promise<number>((resolve) => child.on('exit', (c) => resolve(c ?? 1)));
    if (code !== 0) {
      console.log(`process tail:\n${logTail()}`);
    }
    expect(code, `the relay smoke must pass:\n${out.slice(-3_000)}`).toBe(0);
    expect(out, 'every relay check must pass').toContain('10 passed, 0 failed');
  });
});
