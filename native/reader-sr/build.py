#!/usr/bin/env python3
"""Explicit offline-capable build. Fetches official pinned sources only when --source is absent.
Never invoked implicitly by Gradle; no device deployment, CI or uploads.
"""
import argparse
import hashlib
import json
import shutil
import subprocess
from pathlib import Path

BASE = 'a86cfb043e6482b5f08778a114df9b5faf6e73b7'
FIXED = '3b82de7877bd840475d78fbab24b9bb7dfee781d'
NCNN = 'c4193aadbbb56582aa87b1850dd3d98fb8fd936d'
PINS = {'scale2.0x_model.bin': 'd82ed30207fd07e1c16726a9ca838ce44e6e25a105b0278c7ca681516ee0e18e',
        'scale2.0x_model.param': 'ccc3b2f1c9fad829ecdda40cac8dc853fcddf14917170efba888b256fbf9074a'}


def command(args, cwd=None):
    subprocess.run([str(v) for v in args], cwd=cwd, check=True)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--platform', required=True, choices=['linux-x64', 'windows-x64', 'windows-arm64', 'android-arm64', 'android-x64'])
    parser.add_argument('--source', type=Path)
    parser.add_argument('--cmake', default='cmake')
    parser.add_argument('--ndk', type=Path)
    parser.add_argument('--toolchain', type=Path)
    parser.add_argument('--jobs', type=int, default=3)
    args = parser.parse_args()
    root = Path(__file__).resolve().parent
    source = args.source.resolve() if args.source else root/'build/upstream'
    if not args.source and not source.exists():
        command(['git', 'clone', '--depth', '1', '--branch', '20250915', 'https://github.com/nihui/waifu2x-ncnn-vulkan.git', source])
        command(['git', 'submodule', 'update', '--init', '--depth', '1', 'src/ncnn'], source)
        command(['git', 'submodule', 'update', '--init', '--depth', '1', 'glslang'], source/'src/ncnn')
        command(['git', 'apply', root/'waifu2x-cpu.patch'], source)
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=source, text=True).strip()
    assert head in [BASE, FIXED], 'unexpected upstream source revision'
    assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=source/'src/ncnn', text=True).strip() == NCNN
    assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=source/'src/ncnn/glslang', text=True).strip() == '8cd77a808d0bffa442ae9462d5e3a8141892ba5a'
    command(['git', 'diff', '--exit-code', 'HEAD', '--', '.', ':(exclude)glslang'], source/'src/ncnn')
    command(['git', 'diff', '--exit-code', 'HEAD'], source/'src/ncnn/glslang')
    # Verify the exact patched code against the base + committed patch, even when using a local checkout.
    import tempfile, os
    with tempfile.TemporaryDirectory() as td:
        env = dict(os.environ, GIT_INDEX_FILE=str(Path(td)/'index'))
        subprocess.run(['git', 'read-tree', BASE], cwd=source, env=env, check=True)
        subprocess.run(['git', 'apply', '--cached', str(root/'waifu2x-cpu.patch')], cwd=source, env=env, check=True)
        for name in ['src/main.cpp', 'src/waifu2x.cpp', 'src/deps_ncnn.cmake']:
            expected = subprocess.check_output(['git', 'show', ':'+name], cwd=source, env=env)
            assert (source/name).read_bytes() == expected, 'unreviewed upstream modification: '+name
    output = root/'build'/args.platform
    configure = [args.cmake, '-S', root, '-B', output, '-DCMAKE_BUILD_TYPE=Release', '-DWAIFU2X_SOURCE='+str(source)]
    if args.platform.startswith('android-'):
        assert args.ndk and (args.ndk/'build/cmake/android.toolchain.cmake').exists(), '--ndk is required'
        configure += ['-DCMAKE_TOOLCHAIN_FILE='+str(args.ndk/'build/cmake/android.toolchain.cmake'),
                      '-DANDROID_ABI='+('arm64-v8a' if args.platform=='android-arm64' else 'x86_64'),
                      '-DANDROID_PLATFORM=android-31', '-DANDROID_STL=c++_static', '-DANDROID_CPP_FEATURES=exceptions']
    elif args.toolchain:
        configure += ['-DCMAKE_TOOLCHAIN_FILE='+str(args.toolchain.resolve())]
    command(configure)
    command([args.cmake, '--build', output, '--parallel', str(args.jobs)])
    bundle = root/'build/bundle'
    resource = bundle/'resources/reader-sr'/args.platform
    resource.mkdir(parents=True, exist_ok=True)
    if args.platform.startswith('android-'):
        abi = 'arm64-v8a' if args.platform=='android-arm64' else 'x86_64'
        binary = output/'libcopymanga_sr.so'
        destination = bundle/'jniLibs'/abi/binary.name
    else:
        binary = output/('copymanga-sr.exe' if args.platform.startswith('windows') else 'copymanga-sr')
        destination = resource/binary.name
    data = binary.read_bytes()
    import struct
    if args.platform.startswith('windows'):
        assert data[:2] == b'MZ', 'expected Windows PE executable'
        pe = struct.unpack_from('<I', data, 0x3c)[0]
        assert data[pe:pe+4] == b'PE\0\0'
        machine = struct.unpack_from('<H', data, pe+4)[0]
        assert machine == (0xaa64 if args.platform.endswith('arm64') else 0x8664), 'wrong Windows architecture'
    else:
        assert data[:6] == b'\x7fELF\x02\x01', 'expected 64-bit little-endian ELF'
        machine = struct.unpack_from('<H', data, 18)[0]
        assert machine == (183 if args.platform.endswith('arm64') else 62), 'wrong ELF architecture'
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(binary, destination)
    # Strip only the built artifact, never a system executable. CMake knows the target toolchain.
    cache = (output/'CMakeCache.txt').read_text()
    strip = next((line.split('=',1)[1] for line in cache.splitlines() if line.startswith('CMAKE_STRIP:FILEPATH=')), None)
    if strip: command([strip, '--strip-unneeded', destination])
    model_dir = bundle/'resources/reader-sr/models'; model_dir.mkdir(parents=True, exist_ok=True)
    for name, expected in PINS.items():
        model = source/'models/models-cunet'/name
        assert digest(model) == expected, 'model verification failed'
        shutil.copyfile(model, model_dir/name)
    manifest = {'protocol':'1', 'platform':args.platform, 'runtime':'waifu2x-20250915-ncnn-c4193aad-cmp-sr1',
                'runtimeSha256':digest(destination), 'modelSha256':PINS['scale2.0x_model.bin'],
                'paramSha256':PINS['scale2.0x_model.param'], 'tile':'32', 'noise':'-1', 'scale':'2'}
    (resource/'manifest.properties').write_text(''.join(k+'='+v+'\n' for k,v in manifest.items()))
    notices = ['CopyManga Neo reader SR: pinned cunet 2x, local inference only.\n',
        'waifu2x-ncnn-vulkan (includes the model files in its repository):\n'+(source/'LICENSE').read_text(),
        '\nncnn:\n'+(source/'src/ncnn/LICENSE.txt').read_text()]
    glslang = source/'src/ncnn/glslang'
    for name in ['LICENSE.txt', 'LICENSE', 'LICENSES/Apache-2.0.txt']:
        if (glslang/name).exists(): notices.append('\nglslang '+name+':\n'+(glslang/name).read_text())
    (bundle/'resources/reader-sr/NOTICE.txt').write_text('\n'.join(notices))
    # Include the notices shipped with statically linked platform C++ runtimes.
    if args.platform.startswith('android'):
        for name in ['NOTICE', 'NOTICE.toolchain']:
            shutil.copyfile(args.ndk/name, resource/(name+'.txt'))
    if args.platform.startswith('windows'):
        llvm = Path(os.environ['LLVM_MINGW_ROOT'])
        shutil.copyfile(llvm/'LICENSE.TXT', resource/'LLVM-LICENSE.txt')
        triple = 'aarch64-w64-mingw32' if args.platform.endswith('arm64') else 'x86_64-w64-mingw32'
        for license in (llvm/triple/'share/mingw32').glob('COPYING*'):
            shutil.copyfile(license, resource/license.name)
    (output/'bundle-build.json').write_text(json.dumps(dict(manifest, bytes=destination.stat().st_size, source=head), indent=2)+'\n')
    print('Bundle prepared:', bundle)


if __name__ == '__main__':
    main()
