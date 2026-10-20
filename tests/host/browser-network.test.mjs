import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, writeFile, rm, readFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import vm from 'node:vm';
import http from 'node:http';
import { createWasmServer, imageTarget, imageMime, MAX_IMAGE_BYTES } from '../../.github/scripts/serve-wasm.mjs';

test('octet-stream image content is identified by bytes, while HTML and SVG are rejected', () => {
  assert.equal(imageMime(Buffer.from([255, 216, 255, 217])), 'image/jpeg');
  assert.equal(imageMime(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])), 'image/png');
  assert.equal(imageMime(Buffer.from('<html>upstream error</html>')), null);
  assert.equal(imageMime(Buffer.from('<svg onload="alert(1)"></svg>')), null);
});

test('only exact public image CDN targets are accepted', () => {
  assert.equal(imageTarget('https://sy.mangafunb.fun/y/test/cover.jpg').hostname, 'sy.mangafunb.fun');
  for (const url of [
    'http://sy.mangafunb.fun/test.jpg', 'https://sy.mangafunb.fun:444/test.jpg',
    'https://sy.mangafunb.fun.evil.test/test.jpg', 'https://evil.mangafunb.fun/test.jpg',
    'https://user:pass@sy.mangafunb.fun/test.jpg', 'https://127.0.0.1/test.jpg',
    'https://api.copy4000.com/api/v3/member', 'file:///etc/passwd',
  ]) assert.throws(() => imageTarget(url));
});

test('same-origin server serves image bytes and rejects other origins, methods, and paths', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'cmp-wasm-test-'));
  await writeFile(path.join(root, 'index.html'), '<html><head></head><body></body></html>');
  const seen = [];
  const server = await createWasmServer(root, async (url, signal) => {
    seen.push(url);
    assert.equal(signal.aborted, false);
    return { type: 'image/jpeg', body: Buffer.from([255, 216, 255, 217]) };
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = 'http://127.0.0.1:' + server.address().port;
  const target = 'https://sy.mangafunb.fun/y/test/cover.jpg';
  const route = base + '/__copymanga_image?url=' + encodeURIComponent(target);
  try {
    const page = await fetch(base);
    assert.match(await page.text(), /imageProxy:true/);
    const image = await fetch(route);
    assert.equal(image.status, 200);
    assert.equal(image.headers.get('content-type'), 'image/jpeg');
    assert.equal(image.headers.get('access-control-allow-origin'), null);
    assert.deepEqual([...new Uint8Array(await image.arrayBuffer())], [255, 216, 255, 217]);
    assert.equal((await fetch(route, { headers: { Origin: 'https://other.test' } })).status, 403);
    const foreignHostStatus = await new Promise((resolve, reject) => {
      http.get(route, { headers: { Host: 'evil.test' } }, response => {
        response.resume(); resolve(response.statusCode);
      }).on('error', reject);
    });
    assert.equal(foreignHostStatus, 403);
    assert.equal((await fetch(route, { method: 'POST' })).status, 405);
    assert.equal((await fetch(base + '/__copymanga_image?url=http://127.0.0.1/')).status, 400);
    assert.equal((await fetch(base + '/..%2foutside.txt')).status, 404);
    assert.deepEqual(seen, [target]);
  } finally {
    server.closeAllConnections();
    await new Promise(resolve => server.close(resolve));
    await rm(root, { recursive: true });
  }
});

test('image mapping is opt-in and never reroutes APIs or arbitrary hosts', async () => {
  const source = await readFile(new URL('../../shared/src/wasmJsMain/kotlin/com/rchdliang/copymanga_neo/multiplatform/platform/BrowserHttpClient.kt', import.meta.url), 'utf8');
  const script = source.match(/@JsFun\("""(address => \{[\s\S]*?)"""\)/)[1];
  const context = vm.createContext({ URL, location: { origin: 'http://localhost:8081' } });
  const rewrite = vm.runInContext('(' + script + ')', context);
  const image = 'https://sy.mangafunb.fun/y/test/cover.jpg';
  assert.equal(rewrite(image), '');
  context.copyMangaNeoConfig = { imageProxy: true };
  assert.equal(rewrite(image), 'http://localhost:8081/__copymanga_image?url=' + encodeURIComponent(image));
  assert.equal(rewrite('https://api.copy4000.com/api/v3/comic/test'), '');
  assert.equal(rewrite('https://evil.mangafunb.fun/image.jpg'), '');
  assert.equal(rewrite('blob:http://localhost:8081/test'), '');
});

test('oversized or non-image payloads cannot be served as images', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'cmp-wasm-test-'));
  await writeFile(path.join(root, 'index.html'), '<head></head>');
  let result = { type: 'text/html', body: Buffer.from('not an image') };
  const server = await createWasmServer(root, async () => result);
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const url = 'http://127.0.0.1:' + server.address().port + '/__copymanga_image?url=https://sy.mangafunb.fun/test.jpg';
  try {
    assert.equal((await fetch(url)).status, 502);
    result = { type: 'image/jpeg', body: Buffer.alloc(MAX_IMAGE_BYTES + 1) };
    assert.equal((await fetch(url)).status, 502);
  } finally {
    server.closeAllConnections();
    await new Promise(resolve => server.close(resolve));
    await rm(root, { recursive: true });
  }
});
