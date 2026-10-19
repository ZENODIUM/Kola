import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { fileURLToPath } from 'node:url';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), 'dist');
const videosRoot = path.resolve(path.dirname(root), '..', 'Videos');
const clients = new Set();
let status = { online: false, device: 'Waiting for Android', fps: 0, motion: 0, motionArea: 0, stability: 1, status: 'No signal', insideZone: null, updatedAt: 0 };
let events = [];
let settings = { zone_exit: true, movement_burst: true, extended_quiet: true, camera_moved: true, view_blocked: true, unsafe_item: true };

const json = (res, code, value) => {
  res.writeHead(code, { 'content-type': 'application/json', 'access-control-allow-origin': '*' });
  res.end(JSON.stringify(value));
};
const broadcast = (type, data) => {
  const message = `event: ${type}\ndata: ${JSON.stringify(data)}\n\n`;
  clients.forEach(client => client.write(message));
};
const readBody = req => new Promise((resolve, reject) => {
  let value = '';
  req.on('data', chunk => { value += chunk; if (value.length > 64_000) reject(new Error('too large')); });
  req.on('end', () => { try { resolve(JSON.parse(value || '{}')); } catch (error) { reject(error); } });
  req.on('error', reject);
});

export const server = http.createServer(async (req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Headers', 'content-type');
  if (req.method === 'OPTIONS') { res.writeHead(204); return res.end(); }
  if (req.url === '/api/state' && req.method === 'GET') return json(res, 200, { status, events, settings });
  if (req.url === '/api/videos' && req.method === 'GET') {
    try {
      const videos = fs.readdirSync(videosRoot, { withFileTypes: true }).filter(entry => entry.isFile() && /\.(mp4|webm|mov|m4v)$/i.test(entry.name)).map(entry => { const file = path.join(videosRoot, entry.name); return { name: entry.name, url: `/videos/${encodeURIComponent(entry.name)}`, size: fs.statSync(file).size }; });
      return json(res, 200, videos);
    } catch { return json(res, 200, []); }
  }
  if (req.url === '/api/settings' && req.method === 'GET') return json(res, 200, settings);
  if (req.url === '/api/settings' && req.method === 'POST') {
    try {
      const incoming = await readBody(req);
      Object.keys(settings).forEach(key => { if (typeof incoming[key] === 'boolean') settings[key] = incoming[key]; });
      broadcast('settings', settings);
      return json(res, 200, settings);
    } catch { return json(res, 400, { error: 'Invalid settings payload' }); }
  }
  if (req.url === '/api/stream' && req.method === 'GET') {
    res.writeHead(200, { 'content-type': 'text/event-stream', 'cache-control': 'no-cache', connection: 'keep-alive' });
    res.write(`event: state\ndata: ${JSON.stringify({ status, events, settings })}\n\n`);
    clients.add(res);
    req.on('close', () => clients.delete(res));
    return;
  }
  if (req.url === '/api/status' && req.method === 'POST') {
    try { status = { ...status, ...(await readBody(req)), online: true, updatedAt: Date.now() }; broadcast('status', status); return json(res, 200, { ok: true }); }
    catch { return json(res, 400, { error: 'Invalid status payload' }); }
  }
  if (req.url === '/api/events' && req.method === 'POST') {
    try {
      const event = await readBody(req);
      if (!event.title || !event.kind) return json(res, 422, { error: 'title and kind are required' });
      const stored = { id: event.id || String(Date.now()), timestamp: event.timestamp || Date.now(), severity: 1, ...event };
      events = [stored, ...events].slice(0, 100);
      broadcast('incident', stored);
      return json(res, 201, stored);
    } catch { return json(res, 400, { error: 'Invalid event payload' }); }
  }
  if (req.url === '/api/events' && req.method === 'DELETE') { events = []; broadcast('state', { status, events }); return json(res, 200, { ok: true }); }

  if (req.method === 'GET' && req.url.startsWith('/videos/')) {
    const name = decodeURIComponent(req.url.slice('/videos/'.length).split('?')[0]);
    const file = path.resolve(videosRoot, name);
    if (!file.startsWith(videosRoot) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) return json(res, 404, { error: 'Video not found' });
    const size = fs.statSync(file).size; const range = req.headers.range; const type = /\.webm$/i.test(file) ? 'video/webm' : 'video/mp4';
    res.setHeader('content-type', type); res.setHeader('accept-ranges', 'bytes');
    if (range) { const match = /bytes=(\d*)-(\d*)/.exec(range); const start = Number(match?.[1] || 0); const end = Math.min(Number(match?.[2] || size - 1), size - 1); if (start <= end) { res.writeHead(206, { 'content-length': end - start + 1, 'content-range': `bytes ${start}-${end}/${size}` }); return fs.createReadStream(file, { start, end }).pipe(res); } }
    res.writeHead(200, { 'content-length': size }); return fs.createReadStream(file).pipe(res);
  }

  const target = req.url === '/' ? 'index.html' : req.url.slice(1).split('?')[0];
  const safe = path.normalize(target).replace(/^(\.\.[/\\])+/, '');
  const file = path.join(root, safe);
  if (!file.startsWith(root) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) return json(res, 404, { error: 'Not found' });
  const types = { '.html': 'text/html; charset=utf-8', '.css': 'text/css', '.js': 'text/javascript', '.svg': 'image/svg+xml' };
  res.writeHead(200, { 'content-type': types[path.extname(file)] || 'application/octet-stream', 'cache-control': 'no-cache' });
  fs.createReadStream(file).pipe(res);
});

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const port = Number(process.env.PORT || 8787);
  server.listen(port, '0.0.0.0', () => {
    const addresses = Object.values(os.networkInterfaces()).flat().filter(x => x?.family === 'IPv4' && !x.internal);
    console.log(`Kola dashboard: http://localhost:${port}`);
    addresses.forEach(x => console.log(`Android sync URL: http://${x.address}:${port}`));
  });
}
