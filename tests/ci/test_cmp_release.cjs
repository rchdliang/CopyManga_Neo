const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const release = require('../../.github/scripts/cmp-release.cjs');

const source = 'COPYMANGA_VERSION_BASE = 1.2.3\nCOPYMANGA_VERSION_HOTFIX =\nCOPYMANGA_VERSION_CODE = 40\n';
const context = {
  eventName: 'workflow_dispatch', ref: 'refs/heads/CMP', sha: 'a'.repeat(40),
  repo: {owner: 'example', repo: 'example'},
};
const meta = release.metadata(source, context);

function fixture(t) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'cmp-release-test-'));
  t.after(() => fs.rmSync(root, {recursive: true, force: true}));
  fs.writeFileSync(path.join(root, 'version.properties'), source);
  const directory = path.join(root, 'release-assets');
  fs.mkdirSync(directory);
  for (const name of release.binaries(meta)) fs.writeFileSync(path.join(directory, name), 'fixture-' + name);
  const installer = release.binaries(meta).at(-1);
  const hash = crypto.createHash('sha256').update(fs.readFileSync(path.join(directory, installer))).digest('hex');
  fs.writeFileSync(path.join(directory, installer + '.sha256'), hash + '  ' + installer + '\r\n');
  return {root, directory};
}

function api(existing = null, tagSha = null) {
  const state = {release: existing, assets: [], writes: [], tagSha};
  const repos = {
    getCommit: async () => {
      if (!state.tagSha) throw Object.assign(new Error('missing'), {status: 404});
      return {data: {sha: state.tagSha}};
    },
    listReleases: 'releases',
    listReleaseAssets: 'assets',
    createRelease: async args => {
      state.writes.push(['create', args]);
      state.release = {...args, id: 1, html_url: 'https://example.invalid/draft'};
      return {data: state.release};
    },
    getRelease: async () => ({data: state.release}),
    deleteReleaseAsset: async args => {
      state.writes.push(['delete', args]);
      state.assets = state.assets.filter(asset => asset.id !== args.asset_id);
    },
    uploadReleaseAsset: async args => {
      state.writes.push(['upload', args.name]);
      state.assets.push({id: state.writes.length, name: args.name});
    },
  };
  return {
    state, github: {rest: {repos, git: {getRef: repos.getCommit}}, paginate: async method =>
      method === 'releases' ? (state.release ? [state.release] : []) : state.assets},
  };
}
function core() {
  const summary = {
    addHeading() { return this; }, addLink() { return this; },
    addRaw() { return this; }, async write() {},
  };
  return {info() {}, summary};
}

test('version and optional hotfix come only from version.properties', () => {
  assert.equal(meta.tag, 'cmp-v1.2.3');
  assert.equal(release.metadata(source.replace('HOTFIX =', 'HOTFIX = 2'), context).version, '1.2.3.2');
  assert.throws(() => release.metadata(source.replace('40', '0'), context), /version/);
  assert.throws(() => release.metadata(source + 'COPYMANGA_VERSION_CODE=41\n', context), /Invalid/);
  assert.throws(() => release.metadata(source.replace('1.2.3', '1.2.3; echo secret'), context), /version/);
});

test('only manual CMP runs are eligible', () => {
  assert.throws(() => release.metadata(source, {...context, ref: 'refs/heads/Android'}), /CMP/);
  assert.throws(() => release.metadata(source, {...context, eventName: 'push'}), /manually/);
});

test('published releases, conflicting tags/drafts and API errors block writes', async () => {
  const cases = [
    api({draft: false, tag_name: meta.tag, target_commitish: meta.commit}),
    api({draft: true, tag_name: meta.tag, target_commitish: 'b'.repeat(40)}),
    api(null, 'b'.repeat(40)),
  ];
  for (const mock of cases) {
    await assert.rejects(release.destination(mock.github, context, meta));
    assert.equal(mock.state.writes.length, 0);
  }
  const mock = api();
  mock.github.rest.git.getRef = async () => { throw Object.assign(new Error('forbidden'), {status: 403}); };
  await assert.rejects(release.destination(mock.github, context, meta), /forbidden/);
});

test('missing platform, unexpected files and corrupt installer checksum are rejected', t => {
  const {directory} = fixture(t);
  const android = path.join(directory, release.binaries(meta)[0]);
  fs.unlinkSync(android);
  assert.throws(() => release.prepareAssets(directory, meta), /exactly/);
  fs.writeFileSync(android, 'restored');
  fs.writeFileSync(path.join(directory, 'private.jks'), 'must never upload');
  assert.throws(() => release.prepareAssets(directory, meta), /exactly/);
  fs.unlinkSync(path.join(directory, 'private.jks'));
  fs.appendFileSync(path.join(directory, release.binaries(meta).at(-1)), 'corrupt');
  assert.throws(() => release.prepareAssets(directory, meta), /checksum mismatch/);
});

test('successful run creates only a draft with complete assets and commit manifest', async t => {
  const {root, directory} = fixture(t);
  const mock = api();
  await release.publish({github: mock.github, context, core: core(), root});
  const request = mock.state.writes[0][1];
  assert.equal(request.draft, true);
  assert.equal(request.prerelease, false);
  assert.equal(request.make_latest, 'false');
  assert.equal(request.target_commitish, context.sha);
  assert.equal(mock.state.assets.length, 11);
  const manifest = JSON.parse(fs.readFileSync(path.join(directory, 'release-manifest.json'), 'utf8'));
  assert.equal(manifest.commit, context.sha);
  assert.deepEqual(manifest.platforms, ['android', 'windows-x64']);
  for (const [name, hash] of Object.entries(manifest.sha256)) {
    assert.equal(fs.readFileSync(path.join(directory, name + '.sha256'), 'utf8'), hash + '  ' + name + '\n');
  }
});

test('retry resumes a matching draft without creating or publishing another release', async t => {
  const {root} = fixture(t);
  const mock = api({
    id: 7, draft: true, tag_name: meta.tag, target_commitish: meta.commit,
    body: 'Maintainer edited notes', html_url: 'https://example.invalid/draft',
  }, meta.commit);
  mock.state.assets.push({id: 99, name: release.binaries(meta)[0]});
  await release.publish({github: mock.github, context, core: core(), root});
  assert.equal(mock.state.writes.filter(([kind]) => kind === 'create').length, 0);
  assert.equal(mock.state.writes.filter(([kind]) => kind === 'delete').length, 1);
  assert.equal(mock.state.release.body, 'Maintainer edited notes');
  assert.equal(mock.state.release.draft, true);
});

test('publishing a draft during upload stops further writes', async t => {
  const {root} = fixture(t);
  const mock = api();
  const upload = mock.github.rest.repos.uploadReleaseAsset;
  mock.github.rest.repos.uploadReleaseAsset = async args => {
    await upload(args);
    mock.state.release.draft = false;
  };
  await assert.rejects(release.publish({github: mock.github, context, core: core(), root}), /already published/);
  assert.equal(mock.state.writes.filter(([kind]) => kind === 'upload').length, 1);
});
