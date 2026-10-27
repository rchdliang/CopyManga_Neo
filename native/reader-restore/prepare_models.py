#!/usr/bin/env python3
"""Build a pinned Khronos compiler when a host compiler has not been provided."""
from pathlib import Path
import argparse, hashlib, os, shutil, subprocess, urllib.request, zipfile
REV="1062752a891c95b2bfeed9e356562d88f9df84ac"
SHA="02645874a5146a8050b7a16ebf10b6e33609ac59917f532d14229a02df384a8c"
def compiler(root):
    override=os.environ.get("RESTORE_GLSLANG")
    if override: return Path(override)
    tools=root/"build/reader-vulkan-tools"
    binary=tools/"glslang-build"/("StandAlone/Release/glslang.exe" if os.name=="nt" else "StandAlone/glslang")
    if binary.is_file(): return binary
    tools.mkdir(parents=True,exist_ok=True)
    archive=tools/"glslang.zip"
    if not archive.exists() or hashlib.sha256(archive.read_bytes()).hexdigest()!=SHA:
        with urllib.request.urlopen(f"https://codeload.github.com/KhronosGroup/glslang/zip/{REV}",timeout=90) as response:
            archive.write_bytes(response.read(32*1024*1024))
    if hashlib.sha256(archive.read_bytes()).hexdigest()!=SHA: raise RuntimeError("glslang archive checksum mismatch")
    source=tools/f"glslang-{REV}"
    if not (source/"CMakeLists.txt").exists():
        with zipfile.ZipFile(archive) as z:
            for name in z.namelist():
                if not (tools/name).resolve().is_relative_to(tools.resolve()): raise ValueError("Unsafe archive path")
            z.extractall(tools)
    args=["cmake","-S",str(source),"-B",str(tools/"glslang-build"),"-DENABLE_OPT=OFF","-DENABLE_HLSL=OFF","-DGLSLANG_TESTS=OFF","-DENABLE_GLSLANG_BINARIES=ON"]
    if os.name=="nt": args += ["-G","Visual Studio 17 2022","-A","x64"]
    else: args += ["-DCMAKE_BUILD_TYPE=Release"]
    subprocess.run(args,check=True)
    subprocess.run(["cmake","--build",str(tools/"glslang-build"),"--config","Release","--target","glslang-standalone","-j","2"],check=True)
    return binary
def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--root",type=Path,required=True)
    parser.add_argument("--output",type=Path,required=True)
    args=parser.parse_args()
    root=args.root.resolve()
    executable=compiler(root)
    version=subprocess.check_output([str(executable),"--version"],text=True)
    import re
    wanted=re.search(r'^glslang\s*=\s*"([^"]+)"',(root/"gradle/libs.versions.toml").read_text(),re.M).group(1)
    if wanted not in version: raise RuntimeError("glslang version must match the version catalog")
    subprocess.run([os.sys.executable,str(root/"native/reader-restore/compile_models.py"),"--models",str(root/"native/reader-restore/models"),"--output",str(args.output),"--compiler",str(executable)],check=True)
    shutil.copyfile(root/"native/reader-restore/vendor/vulkan/LICENSE.md",args.output/"reader-restore/LICENSE-Vulkan-Headers.txt")
    shutil.copyfile(root/"native/reader-restore/vendor/vulkan/NOTICE.txt",args.output/"reader-restore/NOTICE-Vulkan-Headers.txt")
    for license in (root/"native/reader-restore/vendor/vulkan/LICENSES").glob("*.txt"):
        shutil.copyfile(license,args.output/"reader-restore"/("LICENSE-Vulkan-"+license.name))
if __name__=="__main__":main()
