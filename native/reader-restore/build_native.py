#!/usr/bin/env python3
"""Build the host JNI library; foreign packaging requires an explicit prebuilt library."""
from pathlib import Path
import argparse, hashlib, json, os, platform, shutil, subprocess
def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--root",type=Path,required=True)
    ap.add_argument("--target",required=True)
    ap.add_argument("--output",type=Path,required=True)
    ap.add_argument("--prebuilt",type=Path)
    args=ap.parse_args()
    root=args.root.resolve()
    os_name="windows" if os.name=="nt" else "linux" if platform.system()=="Linux" else "unsupported"
    arch="x64" if platform.machine().lower() in ("amd64","x86_64") else "arm64"
    name="copymanga_restore.dll" if args.target.startswith("windows-") else "libcopymanga_restore.so"
    if args.prebuilt:
        library=args.prebuilt.resolve()
    else:
        if args.target!=f"{os_name}-{arch}":
            raise RuntimeError("Build the native library on the target OS, or supply readerRestoreNativeLibrary explicitly")
        build=root/"build/reader-restore"/args.target
        command=["cmake","-S",str(root/"native/reader-restore"),"-B",str(build),"-DCMAKE_BUILD_TYPE=Release"]
        if os.name=="nt":command += ["-G","Visual Studio 17 2022","-A","x64" if arch=="x64" else "ARM64"]
        if os.environ.get("JAVA_HOME"):command += ["-DJAVA_HOME="+os.environ["JAVA_HOME"].replace("\\","/")]
        subprocess.run(command,check=True)
        subprocess.run(["cmake","--build",str(build),"--config","Release","--target","copymanga_restore","-j","2"],check=True)
        library=build/("Release/"+name if os.name=="nt" else name)
    raw=library.read_bytes()
    if not raw.startswith(b"MZ" if args.target.startswith("windows-") else b"\x7fELF"):
        raise RuntimeError("Native library format does not match target")
    destination=args.output/"reader-restore"/args.target
    destination.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(library,destination/name)
    (destination/"manifest.json").write_text(json.dumps({"target":args.target,"file":name,"sha256":hashlib.sha256(raw).hexdigest()},indent=2)+"\n")
if __name__=="__main__":main()
