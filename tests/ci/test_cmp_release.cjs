const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const release = require('../../.github/scripts/cmp-release.cjs');

const source = 'COPYMANGA_VERSION_BASE = 1.2.3\nCOPYMANGA_VERSION_HOTFIX =\nCOPYMANGA_VERSION_CODE = 40\n';
const context = {
  eventName: 'workflow_dispatch', ref: 'refs/heads/CMP', sha: '1a2b3c4' + 'a'.repeat(33),
  repo: {owner: 'example', repo: 'example'},
};
const meta = release.metadata(source, context, 234);
const sourceBuild = {
  runId: 100, runAttempt: 1, workflowId: 10,
  url: 'https://github.com/example/example/actions/runs/100',
  androidArtifactId: 201, windowsArtifactId: 202,
};
const git = (command, args) => {
  assert.equal(command, 'git');
  switch (args.join(' ')) {
    case 'rev-parse --verify HEAD': return context.sha + '\n';
    case 'rev-parse --is-shallow-repository': return 'false\n';
    case 'rev-list --count HEAD': return '234\n';
    default: throw new Error('Unexpected git command: ' + args.join(' '));
  }
};

test('automatic builds only watch CMP branch pushes, not tag or PR events', () => {
  const workflow = fs.readFileSync(path.join(__dirname, '../../.github/workflows/cmp-release.yml'), 'utf8');
  const triggers = workflow.match(/^on:\r?\n([\s\S]*?)(?=^\S)/m)?.[1];
  assert.ok(triggers, 'workflow triggers must be present');
  assert.match(triggers, /^  push:\r?\n    branches: \[CMP\]$/m);
  assert.match(triggers, /^  workflow_dispatch:$/m);
  assert.doesNotMatch(triggers, /^\s+(?:tags|tags-ignore|pull_request|release|paths|paths-ignore):/m);
  assert.match(workflow, /^  group: .*github\.sha.*$/m);
  assert.match(workflow, /^  cancel-in-progress: false$/m);
  for (const platform of ['android', 'windows']) {
    assert.match(workflow, new RegExp('^  ' + platform + ':\\r?\\n    if: github.event_name == \'push\'\\r?\\n    needs: prepare$', 'm'));
  }
});

test('push builds cannot validate release destinations or write release drafts', () => {
  const workflow = fs.readFileSync(path.join(__dirname, '../../.github/workflows/cmp-release.yml'), 'utf8');
  assert.match(workflow, /- name: Validate version and destination\r?\n        id: source\r?\n        if: github\.event_name == 'workflow_dispatch'/);
  assert.match(workflow, /^  draft:\r?\n    if: github\.event_name == 'workflow_dispatch'\r?\n    needs: prepare$/m);
  const draft = workflow.slice(workflow.indexOf('\n  draft:'));
  assert.doesNotMatch(draft, /gradlew|setup-java|secrets\./);
  assert.match(draft, /actions: read/);
  assert.equal((draft.match(/github-token: \$\{\{ github.token \}\}/g) || []).length, 2);
  assert.equal((draft.match(/run-id: \$\{\{ needs.prepare.outputs.source_run_id \}\}/g) || []).length, 2);
  assert.equal((draft.match(/digest-mismatch: error/g) || []).length, 2);
  for (const platform of ['android', 'windows']) {
    assert.ok(draft.includes('artifact-ids: ${{ needs.prepare.outputs.' + platform + '_artifact_id }}'));
  }
});

function fixture(t) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'cmp-release-test-'));
  t.after(() => fs.rmSync(root, {recursive: true, force: true}));
  fs.writeFileSync(path.join(root, 'version.properties'), source);
  const directory = path.join(root, 'release-assets');
  fs.mkdirSync(directory);
  for (const name of release.binaries(meta)) fs.writeFileSync(path.join(directory, name), 'fixture-' + name);
  for (const name of release.binaries(meta).filter(name => /\.(zip|exe)$/.test(name))) {
    const hash = crypto.createHash('sha256').update(fs.readFileSync(path.join(directory, name))).digest('hex');
    fs.writeFileSync(path.join(directory, name + '.sha256'), hash + '  ' + name + '\r\n');
  }
  return {root, directory};
}

function buildApi() {
  const run = {
    id: 100, run_attempt: 1, workflow_id: 10, event: 'push', head_branch: 'CMP', head_sha: meta.commit,
    status: 'completed', conclusion: 'success', repository: {full_name: 'example/example'},
    head_repository: {full_name: 'example/example'},
  };
  const artifacts = ['android', 'windows'].map((platform, index) => ({
    id: 201 + index, name: 'cmp-release-' + platform + '-100', expired: false,
    expires_at: '2999-01-01T00:00:00Z', size_in_bytes: 123,
    workflow_run: {id: 100, head_sha: meta.commit},
  }));
  const jobs = ['android', 'windows'].map(name => ({name, status: 'completed', conclusion: 'success'}));
  const state = {runs: [run], artifacts: {100: artifacts}, jobs: {100: jobs}, requests: []};
  const actions = {
    getWorkflow: async args => {
      assert.equal(args.workflow_id, 'cmp-release.yml');
      return {data: {id: 10}};
    },
    getWorkflowRun: async args => ({data: state.runs.find(item => item.id === args.run_id)}),
    listWorkflowRuns: 'runs', listJobsForWorkflowRun: 'jobs', listWorkflowRunArtifacts: 'build-artifacts',
  };
  const paginate = async (method, args) => {
    state.requests.push({method, args});
    if (method === 'runs') return [...state.runs];
    if (method === 'jobs') return state.jobs[args.run_id] || [];
    if (method === 'build-artifacts') return state.artifacts[args.run_id] || [];
    throw new Error('Unexpected endpoint: ' + method);
  };
  return {state, github: {rest: {actions}, paginate}};
}

function api(existing = null, tagSha = null) {
  const build = buildApi();
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
    state, build: build.state, github: {
      rest: {repos, git: {getRef: repos.getCommit}, actions: build.github.rest.actions},
      paginate: async (method, args) => method === 'releases' ? (state.release ? [state.release] : []) :
        method === 'assets' ? state.assets : build.github.paginate(method, args),
    },
  };
}
function core() {
  const summary = {
    addHeading() { return this; }, addLink() { return this; },
    addRaw() { return this; }, async write() {},
  };
  return {info() {}, setOutput() {}, summary};
}

test('version and optional hotfix come only from version.properties', () => {
  assert.equal(meta.tag, 'v1.2.3');
  assert.equal(release.metadata(source.replace('HOTFIX =', 'HOTFIX = 2'), context, 234).version, '1.2.3.2');
  assert.throws(() => release.metadata(source.replace('40', '0'), context), /version/);
  assert.throws(() => release.metadata(source + 'COPYMANGA_VERSION_CODE=41\n', context), /Invalid/);
  assert.throws(() => release.metadata(source.replace('1.2.3', '1.2.3; echo secret'), context), /version/);
});

test('only manual CMP runs are eligible', () => {
  assert.throws(() => release.metadata(source, {...context, ref: 'refs/heads/Android'}), /CMP/);
  assert.throws(() => release.metadata(source, {...context, eventName: 'push'}), /manually/);
});

test('asset names include Git identity and both Windows package types', () => {
  assert.deepEqual(release.binaries(meta), [
    'CopyMangaNeo_android_1.2.3-1a2b3c4+234_armeabi-v7a.apk',
    'CopyMangaNeo_android_1.2.3-1a2b3c4+234_arm64-v8a.apk',
    'CopyMangaNeo_android_1.2.3-1a2b3c4+234_x86_64.apk',
    'CopyMangaNeo_android_1.2.3-1a2b3c4+234_universal.apk',
    'CopyMangaNeo_windows_1.2.3+234_x64_portable.zip',
    'CopyMangaNeo_windows_1.2.3+234_x64_setup.exe',
  ]);
  for (const value of [undefined, 0, -1, 2.5, NaN]) {
    assert.throws(() => release.metadata(source, context, value), /commit count/);
  }
});

test('release metadata requires matching checkout and complete history', t => {
  const {root} = fixture(t);
  assert.deepEqual(release.readMetadata(root, context, git), meta);
  assert.throws(() => release.readMetadata(root, context, () => 'b'.repeat(40)), /Checkout/);
  assert.throws(() => release.readMetadata(root, context, (command, args) =>
    args.includes('--is-shallow-repository') ? 'true' : git(command, args)), /Full Git history/);
});

test('missing or corrupted portable ZIP blocks release publication', t => {
  const {directory} = fixture(t);
  const name = release.binaries(meta).find(name => name.endsWith('.zip'));
  const file = path.join(directory, name);
  const original = fs.readFileSync(file);
  fs.unlinkSync(file);
  assert.throws(() => release.prepareAssets(directory, meta), /exactly/);
  fs.writeFileSync(file, Buffer.concat([original, Buffer.from('corrupt')]));
  assert.throws(() => release.prepareAssets(directory, meta), /checksum mismatch/);
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
  const originalBytes = new Map(release.binaries(meta).map(name => [name, fs.readFileSync(path.join(directory, name))]));
  await release.publish({github: mock.github, context, core: core(), sourceBuild, root, git});
  const request = mock.state.writes[0][1];
  assert.equal(request.draft, true);
  assert.equal(request.prerelease, false);
  assert.equal(request.make_latest, 'false');
  assert.equal(request.target_commitish, context.sha);
  assert.equal(mock.state.assets.length, 13);
  const manifest = JSON.parse(fs.readFileSync(path.join(directory, 'release-manifest.json'), 'utf8'));
  assert.equal(manifest.commit, context.sha);
  assert.equal(manifest.commitCount, 234);
  assert.equal(manifest.versionCode, 40);
  assert.deepEqual(manifest.sourceBuild, sourceBuild);
  for (const [name, bytes] of originalBytes) assert.deepEqual(fs.readFileSync(path.join(directory, name)), bytes);
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
  await release.publish({github: mock.github, context, core: core(), sourceBuild, root, git});
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
  await assert.rejects(release.publish({github: mock.github, context, core: core(), sourceBuild, root, git}), /already published/);
  assert.equal(mock.state.writes.filter(([kind]) => kind === 'upload').length, 1);
});

test('manual preparation pins a successful push run and both immutable artifact IDs', async t => {
  const {root} = fixture(t);
  const mock = api();
  const outputs = {};
  await release.prepare({github: mock.github, context, core: {...core(), setOutput: (key, value) => outputs[key] = value}, root, git});
  assert.deepEqual(outputs.source_build, sourceBuild);
  assert.equal(outputs.source_run_id, sourceBuild.runId);
  assert.equal(outputs.android_artifact_id, sourceBuild.androidArtifactId);
  assert.equal(outputs.windows_artifact_id, sourceBuild.windowsArtifactId);
  assert.equal(mock.state.writes.length, 0);
  assert.deepEqual(mock.build.requests[0].args, {
    ...context.repo, workflow_id: 10, branch: 'CMP', event: 'push', head_sha: meta.commit, status: 'success', per_page: 100,
  });
});

test('selection rejects other commits, workflows, branches, repositories and non-successful push runs', async () => {
  for (const change of [
    {head_sha: 'b'.repeat(40)}, {workflow_id: 99}, {head_branch: 'Android'},
    {repository: {full_name: 'other/repo'}}, {head_repository: {full_name: 'other/repo'}},
    {event: 'workflow_dispatch'}, {event: 'pull_request'}, {status: 'in_progress'},
    {conclusion: 'failure'}, {conclusion: 'cancelled'},
  ]) {
    const mock = buildApi();
    Object.assign(mock.state.runs[0], change);
    await assert.rejects(release.findBuild(mock.github, context, meta), /No successful CMP push build/);
  }
});

test('both platform jobs must actually succeed', async () => {
  for (const jobs of [[], [{name: 'android', status: 'completed', conclusion: 'success'}],
    ['android', 'windows'].map(name => ({name, status: 'completed', conclusion: 'skipped'}))]) {
    const mock = buildApi();
    mock.state.jobs[100] = jobs;
    await assert.rejects(release.findBuild(mock.github, context, meta), /No successful/);
  }
});

test('missing, expired, duplicated, empty or mismatched artifacts cannot be reused', async () => {
  for (const mutate of [
    artifacts => artifacts.pop(),
    artifacts => artifacts.push({...artifacts[0]}),
    artifacts => artifacts[0].expired = true,
    artifacts => artifacts[0].expires_at = '2000-01-01T00:00:00Z',
    artifacts => artifacts[0].expires_at = 'invalid',
    artifacts => artifacts[0].size_in_bytes = 0,
    artifacts => artifacts[0].id = 0,
    artifacts => artifacts[0].workflow_run.head_sha = 'b'.repeat(40),
    artifacts => artifacts[0].workflow_run.id = 999,
    artifacts => artifacts[0].name = 'cmp-release-android-999',
  ]) {
    const mock = buildApi();
    mutate(mock.state.artifacts[100]);
    await assert.rejects(release.findBuild(mock.github, context, meta), /No successful/);
  }
});

test('newest eligible run wins, but incomplete newer runs cannot mix platform artifacts', async () => {
  const mock = buildApi();
  mock.state.runs.push({...mock.state.runs[0], id: 101});
  mock.state.jobs[101] = mock.state.jobs[100];
  mock.state.artifacts[101] = mock.state.artifacts[100].map(artifact => ({
    ...artifact, id: artifact.id + 100, name: artifact.name.replace('-100', '-101'),
    workflow_run: {...artifact.workflow_run, id: 101},
  }));
  assert.equal((await release.findBuild(mock.github, context, meta)).runId, 101);
  mock.state.artifacts[101].pop();
  assert.deepEqual(await release.findBuild(mock.github, context, meta), sourceBuild);
  mock.state.artifacts[100].shift();
  await assert.rejects(release.findBuild(mock.github, context, meta), /No successful/);
});

test('no successful build or API failure stops preparation without publishing', async t => {
  const {root} = fixture(t);
  const mock = api();
  mock.build.runs = [];
  await assert.rejects(release.prepare({github: mock.github, context, core: core(), root, git}), /No rebuild was started/);
  assert.equal(mock.state.writes.length, 0);
  mock.github.rest.actions.getWorkflow = async () => { throw new Error('forbidden'); };
  await assert.rejects(release.findBuild(mock.github, context, meta), /forbidden/);
});

test('publication rechecks pinned run, attempt and artifact identity before any release writes', async t => {
  for (const change of [
    mock => mock.build.runs[0].run_attempt++,
    mock => mock.build.runs[0].conclusion = 'failure',
    mock => mock.build.artifacts[100][0].id++,
    mock => mock.build.artifacts[100][0].expired = true,
  ]) {
    const {root} = fixture(t);
    const mock = api();
    change(mock);
    await assert.rejects(release.publish({github: mock.github, context, core: core(), sourceBuild, root, git}), /Source build/);
    assert.equal(mock.state.writes.length, 0);
  }
  await assert.rejects(release.verifyBuild(buildApi().github, context, meta, null), /pinned source build/);
});
