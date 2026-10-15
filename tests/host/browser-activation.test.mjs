// Synthetic JS adapter tests. No browser, application runtime, network, or Gradle.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { webcrypto } from 'node:crypto';
const source = fs.readFileSync(new URL('../../shared/src/wasmJsMain/kotlin/com/rchdliang/copymanga_neo/multiplatform/platform/BrowserPlatformHost.kt', import.meta.url), 'utf8');
const script = source.match(/@JsFun\("""(\(operation, json, success, failure\) => \{[\s\S]*?)"""\)\s*private external fun browserOperation/)[1];
const cancelScript = source.match(/@JsFun\("""(id=>\{const pending=[\s\S]*?)"""\)\s*private external fun cancelBrowserOperation/)[1];
const tick = () => new Promise(resolve => setImmediate(resolve));
function fixture() {
  let active = false;
  const document = { activeElement: null, addEventListener() {}, removeEventListener() {} };
  class Element {
    constructor(tag) { this.tag = tag; this.children = []; this.style = {}; this.isConnected = true; this.disabled = false; }
    setAttribute() {}
    append(...nodes) { for (const node of nodes) { this.children.push(node); node.parent = this; } }
    appendChild(node) { this.append(node); }
    remove() { this.isConnected = false; if (this.parent) this.parent.children = this.parent.children.filter(node => node !== this); }
    focus() { document.activeElement = this; }
    addEventListener() {}
  }
  document.body = new Element('body');
  document.createElement = tag => new Element(tag);
  const stored = new Map([['copymanga-notification-requested', '1']]);
  const context = vm.createContext({document, crypto: webcrypto, DOMException, Blob, File, Map, Promise,
    navigator: {}, localStorage: {getItem: k => stored.get(k), setItem: (k,v) => stored.set(k,v), removeItem: k => stored.delete(k)},
    Notification: {permission: 'default'}, console});
  const operation = vm.runInContext(`(${script})`, context);
  const cancel = vm.runInContext(`(${cancelScript})`, context);
  const find = (node, text) => node.tag === 'button' && node.textContent === text ? node : node.children.map(child => find(child, text)).find(Boolean);
  const button = text => find(document.body, text);
  const click = text => { const target = button(text); assert.ok(target, `missing button ${text}`); assert.equal(target.disabled, false); active = true; try { target.onclick(); } finally { active = false; } };
  const begin = (name,args={}) => {const calls=[];const id=operation(name,JSON.stringify(args),value=>calls.push({value}),error=>calls.push({error}));return {id,calls};};
  return {context, document, stored, begin, cancel, click, button, assertActive: () => assert.equal(active,true)};
}
test('prepared link shares only from fresh click and retry retains payload', async () => {
  const f=fixture(); const seen=[];
  f.context.navigator.share = data => {f.assertActive();seen.push(data.text);return seen.length===1?Promise.reject(new Error('Try again')):Promise.resolve();};
  const operation=f.begin('shareText',{title:'Book',text:'Prepared URL'});
  assert.equal(seen.length,0);
  f.click('继续');await tick();assert.ok(f.button('重试'));
  f.click('重试');await tick();assert.deepEqual(seen,['Prepared URL','Prepared URL']);
  assert.equal(operation.calls[0].value,'');assert.equal(f.document.body.children.length,0);
});
test('image bytes and File exist before click; share has no asynchronous preparation after click', async () => {
  const f=fixture();let resolveFetch;let calls=0;
  f.context.fetch=()=>new Promise(resolve=>resolveFetch=resolve);
  f.context.navigator.canShare=()=>true;
  f.context.navigator.share=data=>{f.assertActive();assert.equal(data.files[0].name,'page.png');calls++;return Promise.resolve();};
  const operation=f.begin('shareImage',{reference:'https://example.invalid/page',name:'page.png',mime:'image/png'});
  await tick();assert.equal(f.button('继续'),undefined);
  resolveFetch({ok:true,blob:async()=>new Blob(['image'])});await tick();
  assert.equal(calls,0);f.click('继续');await tick();assert.equal(calls,1);assert.equal(operation.calls[0].value,'');
});
test('save picker opens synchronously on click, and cancelled gate never opens picker', async () => {
  const f=fixture();let calls=0;
  f.context.showSaveFilePicker=()=>{f.assertActive();calls++;return Promise.resolve({name:'chosen'});};
  const cancelled=f.begin('save',{name:'file.json'});f.click('取消');await tick();
  assert.equal(calls,0);assert.equal(cancelled.calls[0].value,null);
  const saved=f.begin('save',{name:'file.json'});assert.equal(calls,0);f.click('继续');await tick();
  assert.equal(calls,1);assert.match(saved.calls[0].value,/^web-file:/);
});
test('default notification result is retryable and legacy requested flag is healed', async () => {
  const f=fixture();let calls=0;
  f.context.Notification.requestPermission=()=>{f.assertActive();calls++;return Promise.resolve(calls===1?'default':'granted');};
  const operation=f.begin('notifications');assert.equal(f.stored.has('copymanga-notification-requested'),false);
  f.click('继续');await tick();assert.ok(f.button('重试'));assert.equal(operation.calls.length,0);
  f.click('重试');await tick();assert.equal(f.stored.get('copymanga-notification-requested'),'granted');assert.equal(operation.calls[0].value,'');
});
test('coroutine disposal removes gate without native action or completion callback', async () => {
  const f=fixture();let calls=0;f.context.navigator.share=()=>{calls++;return Promise.resolve();};
  const operation=f.begin('shareText',{text:'Ready'});f.cancel(operation.id);await tick();
  assert.equal(f.document.body.children.length,0);assert.equal(calls,0);assert.deepEqual(operation.calls,[]);
});
test('Escape cancels gate and is prevented from reaching app navigation', async () => {
  const f=fixture();f.context.navigator.share=()=>Promise.resolve();const operation=f.begin('shareText',{text:'Ready'});
  let prevented=false,stopped=false;f.document.body.children[0].onkeydown({key:'Escape',preventDefault(){prevented=true;},stopPropagation(){stopped=true;}});
  await tick();assert.ok(prevented&&stopped);assert.equal(operation.calls[0].value,null);
});
test('disposed gate cannot be triggered through a stale button and restores focus/inert state', async () => {
  const f=fixture();let calls=0;const previous=f.document.createElement('input');f.document.body.append(previous);previous.focus();
  f.context.navigator.share=()=>{calls++;return Promise.resolve();};
  const operation=f.begin('shareText',{text:'Ready'});const stale=f.button('继续');assert.equal(previous.inert,true);
  f.cancel(operation.id);await tick();stale.onclick();await tick();
  assert.equal(calls,0);assert.equal(previous.inert,undefined);assert.equal(f.document.activeElement,previous);
});
test('browser Back dismisses activation gate without dispatching app back', () => {
  const entry = fs.readFileSync(new URL('../../shared/src/wasmJsMain/kotlin/com/rchdliang/copymanga_neo/multiplatform/app/Main.kt', import.meta.url), 'utf8');
  const backScript = entry.match(/@JsFun\("""(\(back, exit\) => \{[\s\S]*?)"""\)\s*private external fun installBrowserBack/)[1];
  const listeners=new Map();let appBack=0,cancelled=0,pushes=0;
  const history={state:null,length:2,pushState(state){this.state=state;pushes++;},replaceState(state){this.state=state;},go(){throw Error('Should not leave');}};
  const context=vm.createContext({crypto:webcrypto,history,location:{href:'https://example.invalid/'},window:{addEventListener(name,fn){listeners.set(name,fn);},removeEventListener(name){listeners.delete(name);}},__copyMangaPlatform:{activationGate(){cancelled++;}}});
  const install=vm.runInContext(`(${backScript})`,context);install(()=>{appBack++;return true;},()=>{throw Error('Should not exit');});
  listeners.get('popstate')({state:null});
  assert.equal(cancelled,1);assert.equal(appBack,0);assert.equal(pushes,2);
});
test('notification settings can retry after cancellation without automatic native prompt', async () => {
  const f=fixture();let calls=0;
  f.context.Notification.requestPermission=()=>{f.assertActive();calls++;return Promise.resolve('granted');};
  const cancelled=f.begin('notificationSettings');f.click('取消');await tick();
  assert.equal(cancelled.calls[0].value,'');assert.equal(calls,0);
  const retry=f.begin('notificationSettings');assert.equal(calls,0);f.click('继续');await tick();
  assert.equal(calls,1);assert.equal(retry.calls[0].value,'');
});
test('notification settings explains granted and blocked permissions without pretending to open settings', async () => {
  for (const [permission, guidance] of [['granted','通知权限已开启'],['denied','通知权限已被阻止']]) {
    const f=fixture();f.context.Notification.permission=permission;
    f.context.Notification.requestPermission=()=>{throw Error('Must not prompt');};
    const operation=f.begin('notificationSettings');await tick();
    assert.ok(operation.calls[0].error.includes(guidance));
    assert.ok(operation.calls[0].error.includes('浏览器地址栏的网站权限设置'));
    assert.equal(f.button('继续'),undefined);
  }
});
test('unsupported notification API reports capability failure', async () => {
  const f=fixture();delete f.context.Notification;
  const operation=f.begin('notificationSettings');await tick();
  assert.equal(operation.calls[0].error,'此浏览器不支持下载通知');
});
test('clipboard copy and read require fresh click; errors never render sensitive payload', async () => {
  const f=fixture();const secret='secret-token-123';let calls=0;
  f.context.navigator.clipboard={writeText(text){f.assertActive();assert.equal(text,secret);calls++;return calls===1?Promise.reject(Error(secret)):Promise.resolve();},readText(){f.assertActive();return Promise.resolve(secret);}};
  const copy=f.begin('copy',{text:secret});assert.equal(calls,0);f.click('继续');await tick();
  const allText=node=>[node.textContent||'',...node.children.map(allText)].join(' ');
  assert.equal(allText(f.document.body).includes(secret),false);
  f.click('重试');await tick();assert.equal(copy.calls[0].value,'');
  const read=f.begin('readClipboard');f.click('继续');await tick();assert.equal(read.calls[0].value,secret);
});
function effects(f) {
  const beginScript=source.match(/@JsFun\("""(\(kind, failure, requestFullscreen, cancelOperation\) => \{[\s\S]*?)"""\)/)[1];
  const endScript=source.match(/@JsFun\("""(\(id\) => \{const state=globalThis.__copyMangaPlatform[\s\S]*?)"""\)/)[1];
  const begin=vm.runInContext(`(${beginScript})`,f.context),end=vm.runInContext(`(${endScript})`,f.context);
  return {begin:()=>begin('fullscreen',message=>{throw Error(message);},lease=>f.begin('fullscreen',{lease}).id,f.cancel),end};
}
test('fullscreen waits for activation and disposing before click cancels it', async () => {
  const f=fixture();let calls=0;f.document.documentElement={requestFullscreen(){f.assertActive();calls++;return Promise.resolve();}};
  const e=effects(f),lease=e.begin();const stale=f.button('继续');assert.equal(calls,0);
  e.end(lease);await tick();stale.onclick();assert.equal(calls,0);assert.equal(f.document.body.children.length,0);
});
test('fullscreen failure retains retry and lease cleanup exits only its owned element', async () => {
  const f=fixture();let calls=0,exits=0;
  f.document.documentElement={requestFullscreen(){f.assertActive();calls++;if(calls===1)return Promise.reject(Error('retry'));f.document.fullscreenElement=this;return Promise.resolve();}};
  f.document.exitFullscreen=()=>{exits++;f.document.fullscreenElement=null;return Promise.resolve();};
  const e=effects(f),first=e.begin();f.click('继续');await tick();f.click('重试');await tick();
  const second=e.begin();e.end(first);assert.equal(exits,0);e.end(second);assert.equal(exits,1);
});
test('late fullscreen completion after disposal cleans up but does not exit a newer owner', async () => {
  const f=fixture();let resolve,exits=0;
  f.document.documentElement={requestFullscreen(){f.assertActive();return new Promise(r=>resolve=()=>{f.document.fullscreenElement=this;r();});}};
  f.document.exitFullscreen=()=>{exits++;return Promise.resolve();};
  const e=effects(f),first=e.begin();f.click('继续');e.end(first);resolve();await tick();assert.equal(exits,1);
  f.document.fullscreenElement=null;
  const old=e.begin();f.click('继续');const newest=e.begin();e.end(old);resolve();await tick();assert.equal(exits,1);
  e.end(newest);
});
test('cancelled native sharing does not copy through a fallback', async () => {
  const f=fixture();let copies=0;f.context.navigator.clipboard={writeText(){copies++;}};
  f.context.navigator.share=()=>Promise.reject(new DOMException('cancelled','AbortError'));
  const operation=f.begin('shareText',{text:'prepared'});f.click('继续');await tick();assert.equal(copies,0);assert.equal(operation.calls[0].value,null);
});
test('fullscreen cleanup leaves a foreign fullscreen element alone', async () => {
  const f=fixture();let exits=0;
  f.document.documentElement={requestFullscreen(){f.assertActive();f.document.fullscreenElement=this;return Promise.resolve();}};
  f.document.exitFullscreen=()=>{exits++;return Promise.resolve();};
  const e=effects(f),lease=e.begin();f.click('继续');await tick();
  f.document.fullscreenElement={foreign:true};e.end(lease);assert.equal(exits,0);
});
test('cancel before copy produces a nullable cancellation outcome and never writes', async () => {
  const f=fixture();let writes=0;
  f.context.navigator.clipboard={writeText(){writes++;return Promise.resolve();}};
  const operation=f.begin('copy',{text:'secret'});f.click('取消');await tick();
  assert.equal(writes,0);assert.deepEqual(operation.calls,[{value:null}]);
});
test('clipboard read cancellation returns null without reading or an error', async () => {
  const f=fixture();let reads=0;
  f.context.navigator.clipboard={readText(){reads++;return Promise.resolve('secret');}};
  const operation=f.begin('readClipboard');f.click('取消');await tick();
  assert.equal(reads,0);assert.deepEqual(operation.calls,[{value:null}]);
});
test('native save picker cancellation returns null rather than an error', async () => {
  const f=fixture();f.context.showSaveFilePicker=()=>Promise.reject(new DOMException('cancelled','AbortError'));
  const operation=f.begin('save',{name:'image.png'});f.click('继续');await tick();
  assert.deepEqual(operation.calls,[{value:null}]);
});
test('prepared image share dismissal remains cancellation without native share or copy', async () => {
  const f=fixture();let shares=0,copies=0;
  f.context.fetch=async()=>({ok:true,blob:async()=>new Blob(['image'])});
  f.context.navigator.canShare=()=>true;
  f.context.navigator.share=()=>{shares++;return Promise.resolve();};
  f.context.navigator.clipboard={writeText(){copies++;}};
  const operation=f.begin('shareImage',{reference:'https://example.invalid/page',name:'page.png',mime:'image/png'});
  await tick();f.click('取消');await tick();
  assert.deepEqual(operation.calls,[{value:null}]);assert.equal(shares,0);assert.equal(copies,0);
});

function browserBackFixture(handled = true) {
  const entry = fs.readFileSync(new URL('../../shared/src/wasmJsMain/kotlin/com/rchdliang/copymanga_neo/multiplatform/app/Main.kt', import.meta.url), 'utf8');
  const backScript = entry.match(/@JsFun\("""(\(back, exit\) => \{[\s\S]*?)"""\)\s*private external fun installBrowserBack/)[1];
  const listeners = new Map();
  let backs = 0, exits = 0;
  const history = {state:null, length:1, pushState(state){this.state=state;}, replaceState(state){this.state=state;}, go(){throw Error('Unexpected history traversal');}};
  const context = vm.createContext({crypto:webcrypto, history, location:{href:'https://example.invalid/'},
    window:{addEventListener(name,fn){listeners.set(name,fn);}, removeEventListener(name){listeners.delete(name);}}});
  vm.runInContext(`(${backScript})`, context)(() => {backs++;return handled;}, () => {exits++;});
  return {listeners, counts:() => ({backs,exits}), key(overrides={}) {
    const event = {key:'Escape', defaultPrevented:false, repeat:false, altKey:false, ctrlKey:false, metaKey:false, shiftKey:false,
      preventDefault(){this.defaultPrevented=true;}, ...overrides};
    listeners.get('keydown')(event);
    return event;
  }};
}
test('browser Escape dispatches once per physical press and ignores held repeats', () => {
  const f = browserBackFixture();
  assert.equal(f.key().defaultPrevented, true);
  for(let i=0;i<4;i++) assert.equal(f.key({repeat:true}).defaultPrevented, false);
  assert.deepEqual(f.counts(), {backs:1,exits:0});
  assert.equal(f.key().defaultPrevented, true);
  assert.deepEqual(f.counts(), {backs:2,exits:0});
});
test('popup-consumed Escape followed by repeats cannot pop an app route', () => {
  const f = browserBackFixture();
  f.key({defaultPrevented:true});
  for(let i=0;i<4;i++) f.key({repeat:true});
  assert.deepEqual(f.counts(), {backs:0,exits:0});
  f.key();
  assert.deepEqual(f.counts(), {backs:1,exits:0});
});
test('modified Escape and other keys remain available to browser shortcuts', () => {
  const f = browserBackFixture();
  for(const modifier of ['altKey','ctrlKey','metaKey','shiftKey']) {
    assert.equal(f.key({[modifier]:true}).defaultPrevented, false);
  }
  assert.equal(f.key({key:'Enter'}).defaultPrevented, false);
  assert.deepEqual(f.counts(), {backs:0,exits:0});
});
test('unhandled Escape does not invoke root flush; popstate retains exit ownership', () => {
  const f = browserBackFixture(false);
  assert.equal(f.key().defaultPrevented, false);
  assert.deepEqual(f.counts(), {backs:1,exits:0});
  f.listeners.get('popstate')({state:null});
  assert.deepEqual(f.counts(), {backs:2,exits:1});
});
