#!/usr/bin/env python3
"""Numerical reference for the unmodified Anime4K graphs, including tile boundaries."""
from pathlib import Path
import argparse,json,re,subprocess
import numpy as np
def reference(source, rgba):
    original=rgba.astype(np.float32)/255
    tensors={"MAIN":original}
    parts=re.split(r"(?m)^//!DESC[^\n]*",source)[1:]
    for i,part in enumerate(parts):
        saved=re.findall(r"(?m)^//!SAVE (\w+)\s*$",part)
        target=saved[0] if saved else "MAIN"
        aliases={}
        for line in part.splitlines():
            match=re.match(r"#define (go_\d+|g_\d+)(?:\(|\s)",line)
            if not match:continue
            name=re.search(r"(\w+)_tex(?:Off)?\(",line).group(1)
            value=tensors[name]
            compact=line.replace(" ","")
            if "max(-" in compact:value=np.maximum(-value,0)
            elif "max(" in compact:value=np.maximum(value,0)
            aliases[match.group(1)]=np.pad(value,((1,1),(1,1),(0,0)),mode="edge")
        result=np.zeros_like(original)
        terms=re.findall(r"mat4\(([^)]+)\)\s*\*\s*(go_\d+|g_\d+)(?:\(\s*(-?[\d.]+),\s*(-?[\d.]+)\))?",part)
        assert terms
        h,w=rgba.shape[:2]
        for coefficients,alias,dx,dy in terms:
            matrix=np.asarray([float(v) for v in coefficients.split(",")],dtype=np.float32).reshape((4,4),order="F")
            x,y=int(float(dx or 0))+1,int(float(dy or 0))+1
            values=aliases[alias][y:y+h,x:x+w]
            result += values @ matrix.T
        bias=re.findall(r"result\s*\+=\s*vec4\(([^)]+)\)",part)
        assert len(bias)==1
        result += np.asarray([float(v) for v in bias[0].split(",")],dtype=np.float32)
        if i==len(parts)-1:result+=original
        else:result=result.astype(np.float16).astype(np.float32)
        tensors[target]=result
    return np.rint(np.clip(result,0,1)*255).astype(np.uint8)
def main():
    ap=argparse.ArgumentParser();ap.add_argument("--runner",type=Path,required=True);ap.add_argument("--root",type=Path,default=Path("."));ap.add_argument("--allow-software",action="store_true");ap.add_argument("--linux",action="store_true")
    args=ap.parse_args();root=args.root.resolve();out=root/"build/reader-restore/reference-tests"/("linux" if args.linux else "windows");out.mkdir(parents=True,exist_ok=True)
    h,w=35,273
    y,x=np.mgrid[:h,:w]
    rgba=np.stack(((x*13+y*7)%256,(x*3+y*17)%256,((x//3+y//2)%2)*200+25,np.full_like(x,255)),axis=-1).astype(np.uint8)
    source=out/"input.rgba";source.write_bytes(rgba.tobytes());records=[]
    for mode,middle in (("restore",""),("soft","Soft_")):
        for level in ("S","M","L","VL","UL"):
            key=f"{mode}_{level.lower()}"
            model=root/"build/reader-restore/generated-resources/reader-restore"/f"{key}.akv"
            target=out/f"{key}.rgba"
            command=[str(args.runner.resolve()),str(model),str(source),str(w),str(h),str(target)]
            if args.allow_software:command.append("--allow-software")
            if args.linux:
                def linux_path(value):
                    value=value.replace("\\", "/")
                    return "/mnt/"+value[0].lower()+value[2:] if re.match(r"^[A-Za-z]:/",value) else value
                command=["wsl","-d","Ubuntu","--","env","VK_DRIVER_FILES=/usr/share/vulkan/icd.d/lvp_icd.json"]+[linux_path(v) for v in command]
            process=subprocess.run(command,text=True,capture_output=True,check=True)
            actual=np.frombuffer(target.read_bytes(),dtype=np.uint8).reshape(h,w,4)
            expected=reference((root/f"native/reader-restore/models/Anime4K_Restore_CNN_{middle}{level}.glsl").read_text(),rgba)
            delta=np.abs(actual.astype(np.int16)-expected.astype(np.int16))
            record={"model":key,"max":int(delta.max()),"mean":float(delta.mean()),"tileBoundaryMax":int(delta[:,254:259].max()),"run":process.stdout.strip()}
            records.append(record);print(json.dumps(record),flush=True)
            assert delta.max()<=8 and delta.mean()<.5,record
    (out/"report.json").write_text(json.dumps(records,indent=2))
if __name__=="__main__":main()
