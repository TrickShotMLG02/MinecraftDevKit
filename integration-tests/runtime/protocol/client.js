const fs = require('node:fs');
const path = require('node:path');
const mineflayer = require('mineflayer');

const host = process.env.MDK_PROTOCOL_HOST || '127.0.0.1';
const port = Number(process.env.MDK_PROTOCOL_PORT || 25611);
const username = process.env.MDK_PROTOCOL_USERNAME || 'MdkCompProbe';
const output = process.env.MDK_PROTOCOL_REPORT;
const probeStatePath = process.env.MDK_PROTOCOL_STATE;
const timeoutMs = Number(process.env.MDK_PROTOCOL_TIMEOUT_MS || 2500);
const profiles = (process.env.MDK_PROTOCOL_PROFILES || 'none,operator,inspect,modify,region,region-admin,admin,all,broad-deny').split(',');
const queries = [
  '/devkittest ', '/devkittest i', '/devkittest m',
  '/devkittest admin ', '/devkittest region ', '/devkittest region i',
  '/devkittest inspect ', '/devkittest modify ',
  '/devkittest region info ', '/devkittest region delete ',
  '/dkt ', '/dktest region ', '/cloudonlyprobe ', '/cloudonlyprobe admin ', '/cloudonlyprobe region '
];
const report = {
  client: { kind: 'offline protocol test client', authenticated: false, host, port, username, protocol: '1.21.11' },
  profiles: [],
  errors: []
};
let nextTransactionId = 1;

const bot = mineflayer.createBot({ host, port, username, version: '1.21.11', auth: 'offline' });
let commandTrees = 0;
let currentTree = null;
let lastCompletionEventIndex = 0;
let lastControlCommandAt = 0;
let loginResolve;
let loginReject;
const login = new Promise((resolve, reject) => { loginResolve = resolve; loginReject = reject; });
const messageWaiters = [];
bot._client.on('declare_commands', packet => {
  commandTrees++;
  currentTree = packet;
});
bot.on('login', () => loginResolve());
bot.on('messagestr', message => {
  for (let i = messageWaiters.length - 1; i >= 0; i--) {
    if (messageWaiters[i].pattern.test(message)) {
      const waiter = messageWaiters.splice(i, 1)[0];
      clearTimeout(waiter.timer);
      waiter.resolve(message);
    }
  }
});
bot.on('kicked', reason => { report.errors.push(`kicked: ${JSON.stringify(reason)}`); loginReject(new Error('Client kicked before login')); });
bot.on('error', error => { report.errors.push(`client: ${error.message}`); });

function waitMessage(pattern, ms = 10000) {
  return new Promise((resolve, reject) => {
    const waiter = { pattern, resolve, timer: setTimeout(() => {
      const index = messageWaiters.indexOf(waiter);
      if (index >= 0) messageWaiters.splice(index, 1);
      reject(new Error(`Timed out waiting for server message ${pattern}`));
    }, ms) };
    messageWaiters.push(waiter);
  });
}

async function sendControlCommand(command) {
  // Keep the command traffic below Minecraft's per-player anti-spam threshold. Each
  // caller also waits for the command's explicit server response before proceeding.
  const waitMs = Math.max(0, 1100 - (Date.now() - lastControlCommandAt));
  if (waitMs) await new Promise(resolve => setTimeout(resolve, waitMs));
  lastControlCommandAt = Date.now();
  bot.chat(command);
}

function treeSnapshot(packet) {
  if (!packet) return [];
  const literal = node => node.extraNodeData?.name ?? node.name ?? '';
  const result = [];
  const visit = (index, prefix) => {
    const node = packet.nodes[index];
    const name = literal(node);
    const pathParts = name ? [...prefix, name] : prefix;
    if (pathParts[0] === 'devkittest' || pathParts[0] === 'dkt' || pathParts[0] === 'dktest'
      || pathParts[0] === 'cloudonlyprobe') {
      result.push({ path: pathParts.join(' '), flags: node.flags ?? null,
        executable: typeof node.flags === 'number' ? !!(node.flags & 4) : null,
        index, children: node.children || [], extraNodeData: node.extraNodeData ?? null,
        nodeKeys: Object.keys(node).sort() });
    }
    for (const child of node.children || []) visit(child, pathParts);
  };
  visit(packet.rootIndex, []);
  return result;
}

function rawCompletionTree(packet) {
  if (!packet) return null;
  const interesting = new Set(['devkittest', 'dkt', 'dktest', 'cloudonlyprobe']);
  const selected = new Set();
  const inspect = index => {
    if (selected.has(index)) return;
    selected.add(index);
    for (const child of packet.nodes[index]?.children || []) inspect(child);
  };
  for (const node of packet.nodes) {
    const name = node.extraNodeData?.name ?? node.name;
    if (interesting.has(name)) {
      const index = packet.nodes.indexOf(node);
      inspect(index);
    }
  }
  return { rootIndex: packet.rootIndex, nodes: [...selected].sort((a, b) => a - b).map(index => ({
    index, ...(packet.nodes[index] || {})
  })) };
}

function requestSuggestions(text) {
  const transactionId = nextTransactionId++;
  return new Promise(resolve => {
    let settled = false;
    const finish = (packet, error) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      bot._client.off('tab_complete', onReply);
      const matched = packet?.transactionId === transactionId;
      const mismatch = packet && !matched ? `Response transaction ${packet.transactionId} did not match request ${transactionId}` : null;
      let eventObservation = null;
      try {
        const state = JSON.parse(fs.readFileSync(probeStatePath, 'utf8'));
        const events = state.completionEvents || [];
        const candidates = events.slice(lastCompletionEventIndex).filter(event => event.sender === state.playerUuid && event.buffer === text);
        eventObservation = candidates.at(-1) || null;
        lastCompletionEventIndex = events.length;
      } catch { }
      const observedEmpty = !packet && eventObservation && eventObservation.suggestions.length === 0;
      resolve({ text, transactionId, status: error || mismatch ? (observedEmpty ? 'PASS' : 'NOT VERIFIED') : 'PASS',
        outcome: packet ? 'PACKET_RESPONSE' : observedEmpty ? 'EMPTY_COMPLETION_EVENT_NO_PACKET' : 'NO_OBSERVATION',
        error: error || mismatch, eventObservation,
        response: packet ? packet.matches.map(match => ({ match: match.match, tooltip: match.tooltip ?? null })) : null,
        responseRange: packet ? { start: packet.start, length: packet.length } : null,
        responseTransactionId: packet?.transactionId ?? null });
    };
    const onReply = packet => finish(packet, null);
    const timer = setTimeout(() => finish(null, `No tab_complete response within ${timeoutMs} ms`), timeoutMs);
    bot._client.on('tab_complete', onReply);
    bot._client.write('tab_complete', { transactionId, text });
  });
}

async function setProfile(profile) {
  const previousTreeCount = commandTrees;
  const acknowledgment = waitMessage(new RegExp(`MDK_COMPLETION_PROFILE .* ${profile.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(?: |$)`));
  await sendControlCommand(`/devkitcompletionprobe profile ${profile}`);
  const message = await acknowledgment;
  const deadline = Date.now() + 5000;
  while (commandTrees <= previousTreeCount && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 10));
  const resetAck = waitMessage(/MDK_COMPLETION_STATS_RESET/);
  await sendControlCommand('/devkitcompletionprobe reset');
  await resetAck;
  lastCompletionEventIndex = 0;
  return { message, treeRefreshObserved: commandTrees > previousTreeCount, commandTree: treeSnapshot(currentTree) };
}

async function readInstrumentation() {
  const response = waitMessage(/MDK_COMPLETION_STATS /);
  await sendControlCommand('/devkitcompletionprobe stats');
  return await response;
}

function executionProbe(profile) {
  switch (profile) {
    case 'inspect': return { command: '/devkittest inspect inspection-target', allowed: true };
    case 'modify': return { command: '/devkittest modify modify-target', allowed: true };
    case 'region': return { command: '/devkittest region info region-alpha', allowed: true };
    case 'region-admin': return { command: '/devkittest region delete restricted-region', allowed: true };
    case 'operator': case 'admin': case 'all': return { command: '/devkittest admin reset', allowed: true };
    case 'broad-deny': return { command: '/devkittest admin reset', allowed: false };
    default: return { command: '/devkittest modify modify-target', allowed: false };
  }
}

async function run() {
  let loginTimer;
  try {
    await Promise.race([login, new Promise((_, reject) => {
      loginTimer = setTimeout(() => reject(new Error('Client login timeout')), 20000);
    })]);
  } finally { clearTimeout(loginTimer); }
  const spawnDeadline = Date.now() + 10000;
  while (!bot.entity && Date.now() < spawnDeadline) await new Promise(resolve => setTimeout(resolve, 10));
  for (const profile of profiles) {
    const profileResult = await setProfile(profile);
    const responses = [];
    for (const query of queries) responses.push(await requestSuggestions(query));
    const instrumentation = await readInstrumentation();
    const execution = executionProbe(profile);
    await sendControlCommand(execution.command);
    const executionInstrumentation = await readInstrumentation();
    report.profiles.push({ profile, profileMessage: profileResult.message, treeRefreshObserved: profileResult.treeRefreshObserved,
      commandTree: profileResult.commandTree, commandTreeRaw: rawCompletionTree(currentTree), queries: responses, instrumentation,
      execution: { ...execution, instrumentation: executionInstrumentation } });
  }
}

run().catch(error => report.errors.push(error.stack || error.message)).finally(() => {
  if (output) {
    fs.mkdirSync(path.dirname(output), { recursive: true });
    fs.writeFileSync(output, JSON.stringify(report, null, 2));
  }
  console.log(JSON.stringify(report, null, 2));
  bot.quit();
});
