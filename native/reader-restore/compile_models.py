#!/usr/bin/env python3
"""Compile the pinned Anime4K MPV graphs to Vulkan compute packs. No weight changes."""
from pathlib import Path
import argparse, hashlib, json, re, struct, subprocess, shutil

LEVELS = ("S", "M", "L", "VL", "UL")
def stages(source):
    parts = re.split(r"(?m)^//!DESC[^\n]*", source)[1:]
    names, radii, result = {"MAIN": 0}, {"MAIN": 0}, []
    for index, part in enumerate(parts):
        inputs = re.findall(r"(?m)^//!BIND (\w+)\s*$", part)
        saved = re.findall(r"(?m)^//!SAVE (\w+)\s*$", part)
        target = saved[0] if saved else "MAIN"
        if not inputs or len(inputs) > 16 or any(n not in names for n in inputs):
            raise ValueError("Unsupported graph inputs")
        body = "\n".join(l for l in part.splitlines() if not l.startswith("//!"))
        if "#include" in body or body.count("vec4 hook()") != 1:
            raise ValueError("Unexpected shader body")
        offsets = re.findall(r"go_\d+\(\s*(-?[\d.]+)\s*,\s*(-?[\d.]+)\s*\)", body)
        radius = max((max(abs(float(x)), abs(float(y))) for x, y in offsets), default=0)
        halo = max(radii[n] for n in inputs) + int(radius)
        final = index == len(parts) - 1
        if (target == "MAIN") != final:
            raise ValueError("Only the final pass may replace MAIN")
        header = ["#version 450", "layout(local_size_x=8, local_size_y=8) in;",
                  "layout(push_constant) uniform Bounds { ivec2 extent; ivec2 origin; ivec2 full; } pc;",
                  "ivec2 pixel;",
                  "ivec2 bound(ivec2 p) { return clamp(clamp(p + pc.origin, ivec2(0), pc.full - 1) - pc.origin, ivec2(0), pc.extent - 1); }"]
        for binding, name in enumerate(inputs):
            header += [
                f"layout(set=0,binding={binding}) uniform sampler2D tex{binding};",
                f"#define {name}_pos ((vec2(pixel) + 0.5) / vec2(pc.extent))",
                f"#define {name}_pt (1.0 / vec2(pc.extent))",
                f"#define {name}_size vec2(pc.extent)",
                f"vec4 {name}_texOff(vec2 d) {{ return texelFetch(tex{binding}, bound(pixel + ivec2(d)), 0); }}",
                f"vec4 {name}_tex(vec2 p) {{ return texelFetch(tex{binding}, bound(ivec2(p * vec2(pc.extent))), 0); }}",
            ]
        fmt = "rgba8" if final else "rgba16f"
        header += [f"layout({fmt},set=0,binding={len(inputs)}) uniform writeonly image2D target;"]
        ending = "vec4 value=hook(); imageStore(target,pixel,vec4(clamp(value.rgb,0.0,1.0),1.0));" if final else "imageStore(target,pixel,hook());"
        compiled = "\n".join(header) + "\n" + body + "\nvoid main(){ pixel=ivec2(gl_GlobalInvocationID.xy); if(any(greaterThanEqual(pixel,pc.extent)))return; " + ending + " }\n"
        result.append((index + 1, [names[n] for n in inputs], compiled))
        names[target], radii[target] = index + 1, halo
    if not result or radii["MAIN"] > 32:
        raise ValueError("Unexpected graph radius")
    return result, radii["MAIN"]

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--models",type=Path,required=True)
    parser.add_argument("--output",type=Path,required=True)
    parser.add_argument("--compiler",type=Path,required=True)
    args=parser.parse_args()
    output=args.output/"reader-restore"
    output.mkdir(parents=True,exist_ok=True)
    work=args.output.parent/"shader-sources"
    work.mkdir(parents=True,exist_ok=True)
    provenance={"upstream":"https://github.com/bloc97/Anime4K","commit":"7684e9586f8dcc738af08a1cdceb024cc184f426","models":{}}
    version=subprocess.check_output([str(args.compiler),"--version"],text=True)
    upstream=json.loads((args.models/"UPSTREAM.json").read_text(encoding="utf-8"))
    for mode, middle in (("restore",""),("soft","Soft_")):
        for level in LEVELS:
            path=args.models/f"Anime4K_Restore_CNN_{middle}{level}.glsl"
            text=path.read_text(encoding="utf-8")
            digest=hashlib.sha256(text.encode()).hexdigest()
            if digest != upstream["files"].get(path.name): raise ValueError("Unreviewed model change: "+path.name)
            key=f"{mode}_{level.lower()}"
            graph, halo=stages(text)
            output_file=output/f"{key}.akv"
            stamp=work/f"{key}.json"
            fingerprint={"source":digest,"generator":hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),"compiler":version}
            if not output_file.exists() or not stamp.exists() or json.loads(stamp.read_text()) != fingerprint:
                data=bytearray(struct.pack("<4sIIII",b"AKV1",1,halo,len(graph),len(graph)+1))
                for number,(target,inputs,shader) in enumerate(graph):
                    glsl=work/f"{key}_{number}.comp"
                    spv=glsl.with_suffix(".spv")
                    glsl.write_text(shader,encoding="utf-8")
                    subprocess.run([str(args.compiler),"-V","--target-env","vulkan1.0","-S","comp","-o",str(spv),str(glsl)],check=True,stdout=subprocess.DEVNULL)
                    code=spv.read_bytes()
                    data += struct.pack("<II",target,len(inputs))
                    data += struct.pack("<"+"I"*len(inputs),*inputs)
                    data += struct.pack("<I",len(code))+code
                output_file.write_bytes(data)
                stamp.write_text(json.dumps(fingerprint),encoding="utf-8")
            provenance["models"][key]={"sourceSha256":digest,"packSha256":hashlib.sha256(output_file.read_bytes()).hexdigest(),"passes":len(graph),"halo":halo}
    shutil.copyfile(args.models/"LICENSE.txt",output/"LICENSE.txt")
    shutil.copyfile(args.models/"NOTICE.txt",output/"NOTICE.txt")
    (output/"PROVENANCE.json").write_text(json.dumps(provenance,indent=2)+"\n",encoding="utf-8")
    print("Vulkan packs:", ", ".join(provenance["models"]))
if __name__ == "__main__": main()
