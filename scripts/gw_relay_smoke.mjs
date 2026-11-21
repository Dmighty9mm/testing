#!/usr/bin/env node
/**
 * Plugin-message relay smoke test: browser client <-> gateway <-> backend.
 *
 * Speaks the browser client's side of the gateway protocol
 * (docs/PROTOCOL.md, protocol 775 - same packets Connection.java sends)
 * against a PlaceholderServer started with `--backend host:port`, whose
 * backend is McProtocolStub (vanilla 26.1.2 login + configuration + play).
 * Asserts the full side channel server APIs such as Apollo ride on:
 *
 *   - a config-phase plugin message sent before the backend has even
 *     finished logging in is queued, relayed, echoed by the backend and
 *     delivered to the client once it is in PLAY (play 0x14);
 *   - backend config-phase messages (minecraft:brand, stub:config) and the
 *     backend's report of how the bridge answered configuration
 *     (stub:status: known packs echoed, ping answered, keepalive answered);
 *   - play 0x10 minecraft:register reaches the backend intact
 *     (stub:registered echo), binary lunar:apollo and JSON apollo:json
 *     payloads round-trip byte-for-byte, including one above the backend's
 *     compression threshold;
 *   - slash commands go to the backend (ServerboundChatCommandPacket) and
 *     its system chat comes back;
 *   - an oversized client plugin message is refused by the gateway.
 *
 * Usage: node scripts/gw_relay_smoke.mjs [gatewayPort]
 *   (start McProtocolStub and `PlaceholderServer --backend 127.0.0.1:<stubPort>`
 *    first; tests/e2e/plugin-relay.spec.ts does that from the shadowJar)
 */

const PORT = Number(process.argv[2] || 8081);
const TIMEOUT_MS = 20_000;
let pass = 0;
let fail = 0;
function check(name, cond, extra = '') {
  if (cond) { pass++; console.log(`  ok  ${name}`); }
  else { fail++; console.log(`FAIL  ${name} ${extra}`); }
}

class Buf {
  constructor(bytes) { this.b = bytes ? Array.from(bytes) : []; this.r = 0; }
  varint(v) {
    while ((v & ~0x7f) !== 0) { this.b.push((v & 0x7f) | 0x80); v >>>= 7; }
    this.b.push(v);
    return this;
  }
  utf(s) { return this.varint(new TextEncoder().encode(s).length).raw(new TextEncoder().encode(s)); }
  raw(bytes) { for (const x of bytes) this.b.push(x & 255); return this; }
  short(v) { this.b.push((v >> 8) & 255, v & 255); return this; }
  long(v) {
    let bi = BigInt.asUintN(64, BigInt(v));
    for (let i = 7; i >= 0; i--) this.b.push(Number((bi >> BigInt(8 * i)) & 255n));
    return this;
  }
  bytes() { return Uint8Array.from(this.b); }
  readVarInt() {
    let v = 0, shift = 0;
    while (true) {
      const b = this.b[this.r++];
      v |= (b & 0x7f) << shift;
      if (!(b & 0x80)) return v;
      shift += 7;
      if (shift >= 32) throw new Error('varint too wide');
    }
  }
  readUtf() {
    const len = this.readVarInt();
    const s = new TextDecoder().decode(Uint8Array.from(this.b.slice(this.r, this.r + len)));
    this.r += len;
    return s;
  }
  readLong() {
    let v = 0n;
    for (let i = 0; i < 8; i++) v = (v << 8n) | BigInt(this.b[this.r++]);
    return v;
  }
  rest() { return Uint8Array.from(this.b.slice(this.r)); }
}

const enc = (s) => new TextEncoder().encode(s);
const dec = (b) => new TextDecoder().decode(b);
const sameBytes = (a, b) => a.length === b.length && a.every((x, i) => x === b[i]);

const ws = new WebSocket(`ws://127.0.0.1:${PORT}/`);
ws.binaryType = 'arraybuffer';

let state = 'login';
let closed = false;
const plugin = [];   // {channel, data}
const chat = [];
const waiters = [];

function send(buf) { ws.send(buf.bytes()); }
function settle() {
  for (let i = waiters.length - 1; i >= 0; i--) {
    if (waiters[i].test()) { waiters[i].resolve(true); waiters.splice(i, 1); }
  }
}
function waitFor(test, label) {
  if (test()) return Promise.resolve(true);
  return new Promise((resolve) => {
    const w = { test, resolve };
    waiters.push(w);
    setTimeout(() => {
      const i = waiters.indexOf(w);
      if (i >= 0) { waiters.splice(i, 1); console.log(`  .. timed out waiting for ${label}`); resolve(false); }
    }, TIMEOUT_MS);
  });
}
const findPlugin = (channel, pred = () => true) => plugin.find((m) => m.channel === channel && pred(m));

ws.onmessage = (ev) => {
  const buf = new Buf(new Uint8Array(ev.data));
  const id = buf.readVarInt();
  if (state === 'login') {
    if (id === 0x02) {
      state = 'config';
      // a config-phase plugin message, sent while the backend bridge is
      // most likely still logging in: it must be queued and relayed
      send(new Buf().varint(0x01).utf('test:early').raw(enc('early-config-message')));
    }
  } else if (state === 'config') {
    if (id === 0x00) {
      const channel = buf.readUtf();
      plugin.push({ channel, data: buf.rest(), phase: 'config' });
    } else if (id === 0x08) {
      send(new Buf().varint(0x05).varint(0)); // known packs: none
    } else if (id === 0x02) {
      state = 'play';
      send(new Buf().varint(0x01).utf('minecraft:brand').utf('relay-smoke'));
      send(new Buf().varint(0x02)); // ack finish
    }
  } else {
    if (id === 0x14) {
      const channel = buf.readUtf();
      plugin.push({ channel, data: buf.rest(), phase: 'play' });
    } else if (id === 0x4F) {
      chat.push(buf.readUtf());
    } else if (id === 0x2C) {
      send(new Buf().varint(0x11).long(buf.readLong()));
    } else if (id === 0x2A) {
      state = 'in-world';
    }
  }
  settle();
};
ws.onclose = () => { closed = true; settle(); };
ws.onerror = (e) => { console.log('  .. websocket error', e.message || ''); };

ws.onopen = async () => {
  // handshake -> LOGIN, then Login Hello (uuid, name, no properties)
  send(new Buf().varint(0x00).varint(775).utf('127.0.0.1').short(PORT).varint(2));
  send(new Buf().varint(0x00).long(0x1234n).long(0x5678n).utf('RelaySmoke').varint(0));

  check('entered the world', await waitFor(() => state === 'in-world', 'play login'));

  // backend configuration traffic, relayed and delivered in play
  check('backend minecraft:brand relayed',
    await waitFor(() => findPlugin('minecraft:brand', (m) => m.phase === 'play'), 'backend brand'));
  check('backend config-phase stub:config relayed',
    await waitFor(() => findPlugin('stub:config', (m) => dec(m.data) === 'hello-config'), 'stub:config'));
  const early = await waitFor(() => findPlugin('test:early'), 'early echo');
  check('config-phase client message queued, relayed and echoed',
    early && dec(findPlugin('test:early').data) === 'early-config-message');
  await waitFor(() => findPlugin('stub:status'), 'stub:status');
  const status = findPlugin('stub:status');
  const statusText = status ? dec(status.data) : '';
  check('bridge answered backend configuration (packs, ping, keepalive)',
    /packs=1/.test(statusText) && /pong=42/.test(statusText) && /keepalive=1/.test(statusText),
    statusText);

  // play: register + payload round trips
  const register = enc('lunar:apollo\0apollo:json');
  send(new Buf().varint(0x10).utf('minecraft:register').raw(register));
  check('minecraft:register reached the backend intact',
    await waitFor(() => findPlugin('stub:registered', (m) => sameBytes(m.data, register)), 'register echo'));

  const binary = Uint8Array.from({ length: 256 }, (_, i) => i);
  send(new Buf().varint(0x10).utf('lunar:apollo').raw(binary));
  check('binary lunar:apollo payload round-trips byte-for-byte',
    await waitFor(() => findPlugin('lunar:apollo', (m) => sameBytes(m.data, binary)), 'binary echo'));

  const json = enc(JSON.stringify({
    '@type': 'type.googleapis.com/lunarclient.apollo.player.v1.PlayerHandshakeMessage',
    pad: 'x'.repeat(4000),
  }));
  send(new Buf().varint(0x10).utf('apollo:json').raw(json));
  check('4 KB apollo:json payload round-trips (backend compression path)',
    await waitFor(() => findPlugin('apollo:json', (m) => sameBytes(m.data, json)), 'json echo'));

  // commands go to the backend
  send(new Buf().varint(0x07).utf('/apollo test'));
  check('slash command reached the backend and its chat came back',
    await waitFor(() => chat.some((c) => c.includes('ran command: /apollo test')), 'command echo'));

  // oversized plugin message: the gateway refuses it (closes the socket)
  send(new Buf().varint(0x10).utf('lunar:apollo').raw(new Uint8Array(40000)));
  check('oversized client plugin message is refused', await waitFor(() => closed, 'close'));

  console.log(`\n${pass} passed, ${fail} failed`);
  process.exit(fail > 0 ? 1 : 0);
};

setTimeout(() => {
  console.log(`\nglobal timeout: ${pass} passed, ${fail} failed`);
  process.exit(1);
}, 4 * TIMEOUT_MS);
