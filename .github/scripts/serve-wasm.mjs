// 本地 WASM 静态站点与受限公开图片转发；不接收或转发账号凭据。
import http from 'node:http';
import https from 'node:https';
import dns from 'node:dns';
import { readFile, realpath, stat } from 'node:fs/promises';
import { createReadStream } from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

export const MAX_IMAGE_BYTES = 16 * 1024 * 1024;

/** CDN 常把 JPEG 标为 binary/octet-stream，以文件签名判断，避免将 HTML 当图片转发。 */
export function imageMime(bytes) {
  if (bytes.length >= 4 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) return 'image/jpeg';
  if (bytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]))) return 'image/png';
  const head = bytes.subarray(0, 12).toString('ascii');
  if (head.startsWith('GIF87a') || head.startsWith('GIF89a')) return 'image/gif';
  if (head.startsWith('RIFF') && head.slice(8, 12) === 'WEBP') return 'image/webp';
  if (head.slice(4, 8) === 'ftyp' && /avif|avis/.test(bytes.subarray(8, 32).toString('ascii'))) return 'image/avif';
  return null;
}

export function imageTarget(address) {
  const url = new URL(address);
  if (url.protocol !== 'https:' || url.username || url.password || url.port || url.hash ||
      !/^s[a-z0-9]\.mangafunb\.fun$/.test(url.hostname)) {
    throw new Error('Unsupported image address');
  }
  return url;
}

function publicIpv4(address) {
  const parts = address.split('.').map(Number);
  if (parts.length !== 4 || parts.some(n => !Number.isInteger(n) || n < 0 || n > 255)) return false;
  const [a, b] = parts;
  return !(a === 0 || a === 10 || a === 127 || a >= 224 ||
    (a === 169 && b === 254) || (a === 172 && b >= 16 && b <= 31) ||
    (a === 192 && (b === 168 || b === 0)) || (a === 100 && b >= 64 && b <= 127) ||
    (a === 198 && (b === 18 || b === 19)));
}

export function loadPublicImage(address, signal) {
  const url = imageTarget(address);
  return new Promise((resolve, reject) => {
    const request = https.get(url, {
      signal,
      // 固定本次连接所用的公网地址；不允许 DNS 将转发目标指向本机或内网。
      lookup(hostname, options, callback) {
        dns.lookup(hostname, { family: 4 }, (error, address) => {
          if (error) return callback(error);
          if (!publicIpv4(address)) return callback(new Error('Non-public image address'));
          callback(null, options.all ? [{ address, family: 4 }] : address, 4);
        });
      },
      headers: { Accept: 'image/avif,image/webp,image/png,image/jpeg,image/gif', 'User-Agent': 'CopyManga-Neo-Wasm' },
    }, response => {
      // 不跟随重定向，不代理 HTML/SVG 或未知类型。
      if (response.statusCode !== 200 ||
          Number(response.headers['content-length']) > MAX_IMAGE_BYTES) {
        response.destroy();
        reject(new Error('Image upstream rejected'));
        return;
      }
      const chunks = [];
      let size = 0;
      response.on('data', chunk => {
        size += chunk.length;
        if (size > MAX_IMAGE_BYTES) response.destroy(new Error('Image exceeds size limit'));
        else chunks.push(chunk);
      });
      response.on('error', reject);
      response.on('end', () => {
        const body = Buffer.concat(chunks);
        const type = imageMime(body);
        if (!type) reject(new Error('Unsupported image content'));
        else resolve({ type, body });
      });
    });
    request.on('error', reject);
    request.setTimeout(15000, () => request.destroy(new Error('Image upstream timeout')));
  });
}

const MIME = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8', '.wasm': 'application/wasm',
  '.json': 'application/json', '.css': 'text/css', '.woff2': 'font/woff2',
  '.ttf': 'font/ttf', '.png': 'image/png', '.jpg': 'image/jpeg', '.webp': 'image/webp',
  '.svg': 'image/svg+xml', '.ico': 'image/x-icon',
};

export async function createWasmServer(directory, loadImage = loadPublicImage) {
  const root = await realpath(directory);
  await stat(path.join(root, 'index.html'));
  return http.createServer(async (request, response) => {
    const fail = (status, message) => {
      if (!response.headersSent) response.writeHead(status, { 'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'no-store' });
      response.end(message);
    };
    try {
      const port = request.socket.localPort;
      const allowedHosts = new Set(['localhost:' + port, '127.0.0.1:' + port]);
      if (!allowedHosts.has(request.headers.host)) return fail(403, 'Invalid host');
      const origin = 'http://' + request.headers.host;
      if (request.headers.origin && request.headers.origin !== origin) return fail(403, 'Invalid origin');
      if (request.headers['sec-fetch-site'] === 'cross-site') return fail(403, 'Cross-site request rejected');
      if (!['GET', 'HEAD'].includes(request.method)) return fail(405, 'GET or HEAD required');
      const url = new URL(request.url, origin);
      if (url.pathname === '/__copymanga_image') {
        if (request.method !== 'GET') return fail(405, 'GET required');
        let target;
        try { target = imageTarget(url.searchParams.get('url')); } catch { return fail(400, 'Unsupported image address'); }
        const abort = new AbortController();
        const timer = setTimeout(() => abort.abort(), 20000);
        response.on('close', () => abort.abort());
        try {
          const image = await loadImage(target.href, abort.signal);
          if (abort.signal.aborted) return;
          if (image.body.length > MAX_IMAGE_BYTES || !/^image\/(jpeg|png|webp|gif|avif)$/.test(image.type)) return fail(502, 'Invalid image');
          response.writeHead(200, {
            'Content-Type': image.type, 'Content-Length': image.body.length,
            'Cache-Control': 'public, max-age=3600', 'X-Content-Type-Options': 'nosniff',
            'Cross-Origin-Resource-Policy': 'same-origin',
          });
          response.end(image.body);
        } catch { if (!response.destroyed) fail(502, 'Image unavailable'); }
        finally { clearTimeout(timer); }
        return;
      }
      const decoded = decodeURIComponent(url.pathname);
      if (decoded.includes('\\') || decoded.includes('\0')) return fail(400, 'Invalid path');
      const candidate = path.resolve(root, '.' + (decoded === '/' ? '/index.html' : decoded));
      const file = await realpath(candidate);
      const relative = path.relative(root, file);
      if (relative.startsWith('..') || path.isAbsolute(relative) || !(await stat(file)).isFile()) return fail(404, 'Not found');
      response.setHeader('X-Content-Type-Options', 'nosniff');
      response.setHeader('Content-Type', MIME[path.extname(file)] || 'application/octet-stream');
      if (file === path.join(root, 'index.html')) {
        // 只在确实提供图片转发的宿主页启用，普通静态托管继续直连。
        const html = (await readFile(file, 'utf8')).replace('<head>', '<head><script>globalThis.copyMangaNeoConfig={...globalThis.copyMangaNeoConfig,imageProxy:true};</script>');
        response.setHeader('Cache-Control', 'no-store');
        response.end(request.method === 'HEAD' ? undefined : html);
      } else if (request.method === 'HEAD') response.end();
      else createReadStream(file).on('error', () => response.destroy()).pipe(response);
    } catch { fail(404, 'Not found'); }
  });
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  const directory = path.resolve(process.argv[2] || 'shared/build/dist/wasmJs/developmentExecutable');
  const port = Number(process.argv[3] || 8081);
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Invalid port');
  const server = await createWasmServer(directory);
  server.listen(port, '127.0.0.1', () => console.log('CopyManga Neo: http://localhost:' + port));
}
