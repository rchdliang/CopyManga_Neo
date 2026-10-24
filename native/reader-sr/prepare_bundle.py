"""Prepare pinned SR resources for ordinary Gradle/IDE/CI builds; never install tools.
Runtime only: models are downloaded on explicit consent by the application.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

MODELS = {'scale2.0x_model.bin': 'd82ed30207fd07e1c16726a9ca838ce44e6e25a105b0278c7ca681516ee0e18e',
          'scale2.0x_model.param': 'ccc3b2f1c9fad829ecdda40cac8dc853fcddf14917170efba888b256fbf9074a'}
RUNTIMES = {'windows-x64': 'c9217aed6a09051db34b9c888f3cddf7aa5b23da87cb90b88e153b33db145491',
            'android-arm64': '3f21c7d23e96ed89a4330b0eecf83463d23f9494cb04c3e8555fd4e6222567f1'}

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def validate(bundle, platform):
    resource = bundle / 'resources/reader-sr'
    manifest = dict(line.split('=', 1) for line in (resource/platform/'manifest.properties').read_text().splitlines() if '=' in line)
    assert manifest['protocol'] == '1' and manifest['platform'] == platform, 'Wrong SR protocol/platform'
    assert manifest['runtime'] == 'waifu2x-20250915-ncnn-c4193aad-cmp-sr1', 'Unpinned SR engine'
    assert (manifest['tile'],manifest['noise'],manifest['scale']) == ('32','-1','2')
    assert manifest['modelSha256'] == MODELS['scale2.0x_model.bin']
    assert manifest['paramSha256'] == MODELS['scale2.0x_model.param']
    binary = bundle/'jniLibs/arm64-v8a/libcopymanga_sr.so' if platform == 'android-arm64' else resource/platform/'copymanga-sr.exe'
    actual = digest(binary)
    assert actual == manifest['runtimeSha256'], 'SR runtime checksum mismatch'
    # A locally source-built alternative has a receipt produced only after pinned build.py succeeds.
    if actual != RUNTIMES[platform]:
        receipt = json.loads((bundle/(platform+'-source-build.json')).read_text())
        assert receipt == {'runtimeSha256':actual,'source':'a86cfb043e6482b5f08778a114df9b5faf6e73b7','ncnn':'c4193aadbbb56582aa87b1850dd3d98fb8fd936d'}, 'Missing pinned source-build receipt'
    assert (resource/'NOTICE.txt').stat().st_size > 100, 'Missing SR licenses'
    licenses = ['NOTICE.txt', 'NOTICE.toolchain.txt'] if platform == 'android-arm64' else ['LLVM-LICENSE.txt','COPYING','COPYING.LIB','COPYING.MinGW-w64','COPYING.MinGW-w64-runtime']
    # Windows bundle includes upstream LLVM plus all shipped MinGW license files.
    if platform == 'windows-x64':
        assert (resource/platform/'LLVM-LICENSE.txt').is_file()
        assert any((resource/platform).glob('COPYING*'))
    else:
        for name in licenses: assert (resource/platform/name).is_file(), 'Missing platform license: '+name
    return actual

def verify_import(bundle, lock):
    base=bundle.resolve()
    for line in lock.read_text().splitlines():
        expected, rel=line.split('  ',1)
        if rel.startswith('resources/reader-sr/models/'): continue
        path=(bundle/rel).resolve()
        assert path.is_relative_to(base), 'Unsafe bundle entry'
        assert digest(path)==expected, 'Imported bundle checksum mismatch: '+rel

def prepare(root, destination, platform):
    manifest=destination/'resources/reader-sr'/platform/'manifest.properties'
    if manifest.exists():
        validate(destination,platform)
        if (destination/"SHA256SUMS").is_file(): verify_import(destination,root/"bundle-v1.sha256")
        print('SR validated cached '+platform)
        return
    # Existing repository source-build output or reviewed imported bundle can seed the stable cache.
    for candidate in [root/'build/bundle',root/'build/imported/cmp-reader-sr-bundle']:
        if candidate.resolve()==destination.resolve(): continue
        if not (candidate/'resources/reader-sr'/platform/'manifest.properties').exists(): continue
        validate(candidate,platform)
        if (candidate/'SHA256SUMS').is_file(): verify_import(candidate,root/'bundle-v1.sha256')
        destination.mkdir(parents=True,exist_ok=True)
        # Copy only validated data, preserve receipts/licenses. Called serially by Gradle dependencies.
        shutil.copytree(candidate,destination,dirs_exist_ok=True,ignore=shutil.ignore_patterns('models'))
        validate(destination,platform)
        print('SR prepared and validated '+platform)
        return
    # Fresh checkout: build from fixed official sources using already-installed tools.
    # No private Library IDs, authenticated URLs, toolchain downloads or runtime downloads.
    cmake=shutil.which('cmake')
    if not cmake: raise RuntimeError('SR bundle missing: install/prepare CMake and the documented native toolchain, or import the reviewed bundle into native/reader-sr/build/bundle. Ordinary supported builds require SR assets; refusing an incomplete package.')
    command=[sys.executable,str(root/'build.py'),'--platform',platform,'--cmake',cmake]
    if platform=='android-arm64':
        ndk=os.environ.get('ANDROID_NDK_HOME') or os.environ.get('ANDROID_NDK_ROOT')
        if not ndk: raise RuntimeError('SR bundle missing: ANDROID_NDK_HOME must point to the documented installed NDK. No tools were installed and no incomplete APK will be produced.')
        command+=['--ndk',ndk]
    else:
        llvm=os.environ.get('LLVM_MINGW_ROOT')
        if not llvm: raise RuntimeError('SR bundle missing: LLVM_MINGW_ROOT must point to the documented installed llvm-mingw toolchain. No tools were installed and no incomplete Windows package will be produced.')
        command+=['--toolchain',str(root/'mingw-toolchain.cmake')]
    subprocess.run(command,check=True)
    candidate=root/'build/bundle'
    data=json.loads((root/'build'/platform/'bundle-build.json').read_text())
    receipt={'runtimeSha256':data['runtimeSha256'],'source':'a86cfb043e6482b5f08778a114df9b5faf6e73b7','ncnn':'c4193aadbbb56582aa87b1850dd3d98fb8fd936d'}
    (candidate/(platform+'-source-build.json')).write_text(json.dumps(receipt))
    validate(candidate,platform)
    destination.mkdir(parents=True,exist_ok=True)
    shutil.copytree(candidate,destination,dirs_exist_ok=True,ignore=shutil.ignore_patterns('models'))
    validate(destination,platform)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--destination',type=Path,required=True)
    parser.add_argument('--platform',choices=list(RUNTIMES),required=True)
    args=parser.parse_args()
    try: prepare(Path(__file__).resolve().parent,args.destination,args.platform)
    except (AssertionError, OSError, ValueError, KeyError, RuntimeError, subprocess.CalledProcessError) as error:
        print('SR preparation failed: '+str(error),file=sys.stderr)
        return 1
    return 0
if __name__=='__main__': sys.exit(main())
