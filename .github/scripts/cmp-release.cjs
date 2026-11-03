// CMP 发布只创建草稿；同版本只能恢复同一提交的草稿，禁止改写已公开版本。
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const {execFileSync} = require('node:child_process');

function metadata(source, context, commitCount) {
  if (context.eventName !== 'workflow_dispatch' || context.ref !== 'refs/heads/CMP') {
    throw new Error('Run this workflow manually on the CMP branch.');
  }
  if (!/^[0-9a-f]{40}$/.test(context.sha)) throw new Error('Invalid release commit.');
  const values = {};
  for (const line of source.replace(/^\uFEFF/, '').split(/\r?\n/)) {
    if (!line.trim() || /^\s*[#!]/.test(line)) continue;
    const match = line.match(/^\s*(COPYMANGA_VERSION_(?:BASE|HOTFIX|CODE))\s*=\s*(.*?)\s*$/);
    if (!match || Object.hasOwn(values, match[1])) throw new Error('Invalid version.properties.');
    values[match[1]] = match[2];
  }
  const base = values.COPYMANGA_VERSION_BASE;
  const hotfix = values.COPYMANGA_VERSION_HOTFIX;
  const code = values.COPYMANGA_VERSION_CODE;
  if (!/^\d+\.\d+\.\d+$/.test(base || '') || hotfix === undefined ||
      !/^\d*$/.test(hotfix) || !/^\d+$/.test(code || '') ||
      Number(code) < 1 || Number(code) > 2100000000) {
    throw new Error('Invalid release version or version code.');
  }
  const version = base + (hotfix ? '.' + hotfix : '');
  if (!Number.isSafeInteger(commitCount) || commitCount < 1) throw new Error('Invalid Git commit count.');
  const shortCommit = context.sha.slice(0, 7);
  return {version, versionCode: Number(code), tag: 'v' + version, commit: context.sha,
    commitCount, shortCommit, buildVersion: version + '+' + commitCount,
    androidArtifactVersion: version + '-' + shortCommit + '+' + commitCount};
}

function readMetadata(root, context, git = execFileSync) {
  const options = {cwd: root, encoding: 'utf8'};
  if (git('git', ['rev-parse', '--verify', 'HEAD'], options).trim() !== context.sha) {
    throw new Error('Checkout does not match the release commit.');
  }
  if (git('git', ['rev-parse', '--is-shallow-repository'], options).trim() !== 'false') {
    throw new Error('Full Git history is required for artifact naming.');
  }
  const commitCount = Number(git('git', ['rev-list', '--count', 'HEAD'], options).trim());
  return metadata(fs.readFileSync(path.join(root, 'version.properties'), 'utf8'), context, commitCount);
}

function assertDraft(release, meta) {
  if (!release.draft) throw new Error('This version is already published; increase version.properties.');
  if (release.target_commitish !== meta.commit) {
    throw new Error('Existing draft belongs to another commit; use a new version or resolve it manually.');
  }
}

async function destination(github, context, meta) {
  // 先查标签引用（不存在为 404），再解析轻量及附注标签对应的提交。
  let tag;
  try {
    tag = await github.rest.git.getRef({...context.repo, ref: 'tags/' + meta.tag});
  } catch (error) {
    if (error.status !== 404) throw error;
  }
  if (tag) {
    const {data} = await github.rest.repos.getCommit({...context.repo, ref: 'tags/' + meta.tag});
    if (data.sha !== meta.commit) throw new Error('Release tag points to another commit.');
  }
  const releases = await github.paginate(github.rest.repos.listReleases, {...context.repo, per_page: 100});
  const matching = releases.filter(item => item.tag_name === meta.tag);
  if (matching.length > 1) throw new Error('Multiple releases use this tag; resolve them manually.');
  if (matching[0]) assertDraft(matching[0], meta);
  return matching[0];
}

// 只接受本仓库、同一工作流、同一提交的成功 push 构建，禁止跨提交或混用运行产物。
async function inspectBuild(github, context, meta, workflowId, run) {
  const repository = context.repo.owner + '/' + context.repo.repo;
  if (run.workflow_id !== workflowId || run.event !== 'push' || run.head_branch !== 'CMP' ||
      run.head_sha !== meta.commit || run.status !== 'completed' || run.conclusion !== 'success' ||
      run.repository?.full_name !== repository || run.head_repository?.full_name !== repository) return null;
  const params = {...context.repo, run_id: run.id, per_page: 100};
  const jobs = await github.paginate(github.rest.actions.listJobsForWorkflowRun, {...params, filter: 'latest'});
  if (!['android', 'windows'].every(name =>
    jobs.some(job => job.name === name && job.status === 'completed' && job.conclusion === 'success'))) return null;
  const artifacts = await github.paginate(github.rest.actions.listWorkflowRunArtifacts, params);
  const selected = ['android', 'windows'].map(platform => {
    const matches = artifacts.filter(artifact => artifact.name === 'cmp-release-' + platform + '-' + run.id);
    if (matches.length !== 1) return null;
    const artifact = matches[0];
    return Number.isSafeInteger(artifact.id) && artifact.id > 0 && artifact.expired === false &&
      Date.parse(artifact.expires_at) > Date.now() && artifact.size_in_bytes > 0 &&
      artifact.workflow_run?.id === run.id && artifact.workflow_run?.head_sha === meta.commit ? artifact : null;
  });
  if (selected.some(artifact => !artifact)) return null;
  return {
    runId: run.id, runAttempt: run.run_attempt, workflowId,
    url: 'https://github.com/' + repository + '/actions/runs/' + run.id,
    androidArtifactId: selected[0].id, windowsArtifactId: selected[1].id,
  };
}

async function findBuild(github, context, meta) {
  const {data: workflow} = await github.rest.actions.getWorkflow({...context.repo, workflow_id: 'cmp-release.yml'});
  const runs = await github.paginate(github.rest.actions.listWorkflowRuns, {
    ...context.repo, workflow_id: workflow.id, branch: 'CMP', event: 'push',
    head_sha: meta.commit, status: 'success', per_page: 100,
  });
  for (const run of runs.sort((a, b) => b.id - a.id)) {
    const build = await inspectBuild(github, context, meta, workflow.id, run);
    if (build) return build;
  }
  throw new Error('No successful CMP push build with complete, unexpired Android and Windows artifacts for ' +
    meta.commit + '. Wait for the automatic build or rerun it, then run this release workflow again. No rebuild was started.');
}

async function verifyBuild(github, context, meta, sourceBuild) {
  if (!sourceBuild || !Number.isSafeInteger(sourceBuild.runId) || sourceBuild.runId < 1) {
    throw new Error('A pinned source build is required.');
  }
  const {data: workflow} = await github.rest.actions.getWorkflow({...context.repo, workflow_id: 'cmp-release.yml'});
  const {data: run} = await github.rest.actions.getWorkflowRun({...context.repo, run_id: sourceBuild.runId});
  const current = await inspectBuild(github, context, meta, workflow.id, run);
  if (!current || Object.keys(current).some(key => current[key] !== sourceBuild[key])) {
    throw new Error('Source build or artifacts changed or expired; run the release workflow again.');
  }
  return current;
}

function binaries(meta) {
  return [
    ...['armeabi-v7a', 'arm64-v8a', 'x86_64', 'universal'].map(
      abi => 'CopyMangaNeo_android_' + meta.androidArtifactVersion + '_' + abi + '.apk'),
    'CopyMangaNeo_windows_' + meta.buildVersion + '_x64_portable.zip',
    'CopyMangaNeo_windows_' + meta.buildVersion + '_x64_setup.exe',
  ];
}

function prepareAssets(directory, meta, sourceBuild) {
  const names = binaries(meta);
  const windows = names.filter(name => name.endsWith('.exe') || name.endsWith('.zip'));
  const expected = [...names, ...windows.map(name => name + '.sha256')].sort();
  const actual = fs.readdirSync(directory).sort();
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error('Release assets must contain exactly four release APKs, the Windows installer/portable ZIP and their checksums.');
  }
  const hashes = {};
  for (const name of names) {
    const file = path.join(directory, name);
    if (!fs.lstatSync(file).isFile() || fs.statSync(file).size === 0) throw new Error('Empty or invalid asset: ' + name);
    hashes[name] = crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
  }
  for (const name of windows) {
    const checksum = fs.readFileSync(path.join(directory, name + '.sha256'), 'utf8').trim();
    if (checksum !== hashes[name] + '  ' + name) throw new Error('Windows artifact checksum mismatch: ' + name);
  }
  for (const name of names) {
    fs.writeFileSync(path.join(directory, name + '.sha256'), hashes[name] + '  ' + name + '\n');
  }
  fs.writeFileSync(path.join(directory, 'release-manifest.json'), JSON.stringify({
    ...meta, sourceBuild, platforms: ['android', 'windows-x64'], sha256: hashes,
  }, null, 2) + '\n');
  return [...names.flatMap(name => [name, name + '.sha256']), 'release-manifest.json'];
}

async function prepare({github, context, core, root = process.cwd(), git = execFileSync}) {
  const meta = readMetadata(root, context, git);
  await destination(github, context, meta);
  const sourceBuild = await findBuild(github, context, meta);
  core.setOutput('source_build', sourceBuild);
  core.setOutput('source_run_id', sourceBuild.runId);
  core.setOutput('android_artifact_id', sourceBuild.androidArtifactId);
  core.setOutput('windows_artifact_id', sourceBuild.windowsArtifactId);
  core.info('Reuse automatic build: ' + sourceBuild.url);
  core.info('CMP ' + meta.version + ' (' + meta.versionCode + '), commit ' + meta.commit);
  return meta;
}

async function publish({github, context, core, sourceBuild, root = process.cwd(), git = execFileSync}) {
  const meta = readMetadata(root, context, git);
  let release = await destination(github, context, meta);
  sourceBuild = await verifyBuild(github, context, meta, sourceBuild);
  const directory = path.join(root, 'release-assets');
  const files = prepareAssets(directory, meta, sourceBuild);
  if (!release) {
    const {data} = await github.rest.repos.createRelease({
      ...context.repo,
      tag_name: meta.tag,
      target_commitish: meta.commit,
      name: 'CopyManga Neo CMP v' + meta.version,
      draft: true,
      prerelease: false,
      make_latest: 'false',
      body: [
        '本次提供 Android 和 Windows x64。',
        '',
        '- Android：正式签名 Release APK；普通 ARM64 手机选择 arm64-v8a，不确定时选择 universal。',
        '- Windows：x64 安装程序及免安装 ZIP，无需另装 Java；ZIP 解压后运行 CopyMangaNeo.exe，保留同目录运行库和资源。',
        '- 免安装 ZIP 仍使用当前用户的数据目录，与安装版共用资料及单实例锁。',
        '- 每个安装包附带 SHA-256 校验文件；release-manifest.json 记录构建提交和文件校验值。',
        '',
        '版本：' + meta.buildVersion + '；平台升级编号：' + meta.versionCode,
        '源码提交：' + meta.commit,
        '复用自动构建：' + sourceBuild.url,
        '',
        '发布前请补充更新说明，并用本页附件完成 Android 覆盖安装、数据保留及 Windows 安装、启动、升级验收。',
        '构建通过不代表设备验收完成。本工作流不会自动公开发布。',
      ].join('\n'),
    });
    release = data;
  }
  assertDraft(release, meta);
  const params = {...context.repo, release_id: release.id};
  const existing = await github.paginate(github.rest.repos.listReleaseAssets, {...params, per_page: 100});
  if (existing.some(asset => !files.includes(asset.name))) {
    throw new Error('Draft contains unexpected assets; review it manually before retrying.');
  }
  // 每次写附件前重新检查草稿状态；失败后可重跑同一提交，不覆盖人工编辑的说明。
  for (const name of files) {
    const {data: current} = await github.rest.repos.getRelease(params);
    assertDraft(current, meta);
    const previous = existing.find(asset => asset.name === name);
    if (previous) await github.rest.repos.deleteReleaseAsset({...context.repo, asset_id: previous.id});
    const data = fs.readFileSync(path.join(directory, name));
    await github.rest.repos.uploadReleaseAsset({
      ...params, name, data,
      headers: {'content-type': 'application/octet-stream', 'content-length': data.length},
    });
  }
  const {data: finalRelease} = await github.rest.repos.getRelease(params);
  assertDraft(finalRelease, meta);
  const uploaded = await github.paginate(github.rest.repos.listReleaseAssets, {...params, per_page: 100});
  if (JSON.stringify(uploaded.map(asset => asset.name).sort()) !== JSON.stringify([...files].sort())) {
    throw new Error('Draft asset list is incomplete; rerun the draft job.');
  }
  await core.summary.addHeading('CMP Release 草稿')
    .addLink(meta.tag, finalRelease.html_url)
    .addRaw('\n\n复用自动构建：' + sourceBuild.url)
    .addRaw('\n\n仅创建草稿；请验收附件、补充更新说明后手动发布。\n').write();
}

module.exports = {metadata, readMetadata, assertDraft, destination, findBuild, verifyBuild, binaries, prepareAssets, prepare, publish};
