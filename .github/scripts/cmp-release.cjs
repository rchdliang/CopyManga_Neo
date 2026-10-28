// CMP 发布只创建草稿；同版本只能恢复同一提交的草稿，禁止改写已公开版本。
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

function metadata(source, context) {
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
  return {version, versionCode: Number(code), tag: 'cmp-v' + version, commit: context.sha};
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

function binaries(meta) {
  return [
    ...['armeabi-v7a', 'arm64-v8a', 'x86_64', 'universal'].map(
      abi => 'CopyManga_Neo-v' + meta.version + '-' + abi + '-release.apk'),
    'CopyMangaNeo-' + meta.version + '-windows-x64-setup.exe',
  ];
}

function prepareAssets(directory, meta) {
  const names = binaries(meta);
  const installer = names.at(-1);
  const expected = [...names, installer + '.sha256'].sort();
  const actual = fs.readdirSync(directory).sort();
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error('Release assets must contain exactly four release APKs and the Windows installer/checksum.');
  }
  const hashes = {};
  for (const name of names) {
    const file = path.join(directory, name);
    if (!fs.lstatSync(file).isFile() || fs.statSync(file).size === 0) throw new Error('Empty or invalid asset: ' + name);
    hashes[name] = crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
  }
  const checksum = fs.readFileSync(path.join(directory, installer + '.sha256'), 'utf8').trim();
  if (checksum !== hashes[installer] + '  ' + installer) throw new Error('Windows installer checksum mismatch.');
  for (const name of names) {
    fs.writeFileSync(path.join(directory, name + '.sha256'), hashes[name] + '  ' + name + '\n');
  }
  fs.writeFileSync(path.join(directory, 'release-manifest.json'), JSON.stringify({
    ...meta, platforms: ['android', 'windows-x64'], sha256: hashes,
  }, null, 2) + '\n');
  return [...names.flatMap(name => [name, name + '.sha256']), 'release-manifest.json'];
}

async function prepare({github, context, core, root = process.cwd()}) {
  const meta = metadata(fs.readFileSync(path.join(root, 'version.properties'), 'utf8'), context);
  await destination(github, context, meta);
  core.info('CMP ' + meta.version + ' (' + meta.versionCode + '), commit ' + meta.commit);
  return meta;
}

async function publish({github, context, core, root = process.cwd()}) {
  const meta = metadata(fs.readFileSync(path.join(root, 'version.properties'), 'utf8'), context);
  let release = await destination(github, context, meta);
  const directory = path.join(root, 'release-assets');
  const files = prepareAssets(directory, meta);
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
        '- Windows：x64 安装程序，无需另装 Java。',
        '- 每个安装包附带 SHA-256 校验文件；release-manifest.json 记录构建提交和文件校验值。',
        '',
        '版本：' + meta.version + '；构建号：' + meta.versionCode,
        '源码提交：' + meta.commit,
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
    .addRaw('\n\n仅创建草稿；请验收附件、补充更新说明后手动发布。\n').write();
}

module.exports = {metadata, assertDraft, destination, binaries, prepareAssets, prepare, publish};
