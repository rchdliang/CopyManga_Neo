#!/usr/bin/env python3
"""Synthetic-only evaluation. Never fetches manga, installs software, or modifies original samples.
Runs ONLY the pinned, verified official Linux waifu2x 20250915 executable when --waifu-root is given.
Pillow/numpy are lab dependencies, not app dependencies. Outputs are excluded from version control.
"""
import argparse
import hashlib
import json
import os
import subprocess
import time
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

PINS = {
    'waifu2x-ncnn-vulkan': 'c18f4ec21879a8b91263551ebaab0a5b5f45ad4c456d90cf626aaaf561b42280',
    'models-cunet/scale2.0x_model.bin': 'd82ed30207fd07e1c16726a9ca838ce44e6e25a105b0278c7ca681516ee0e18e',
    'models-cunet/scale2.0x_model.param': 'ccc3b2f1c9fad829ecdda40cac8dc853fcddf14917170efba888b256fbf9074a',
}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def sharpen(image, strength):
    """CPU reference for the platform shaders, NOT an AI model or substitute for GPU testing."""
    c = np.asarray(image.convert('RGB'), dtype=np.float32) / 255
    y = c @ np.array([.2126, .7152, .0722], dtype=np.float32)
    p = np.pad(y, 1, mode='edge')
    n, s, w, e = p[:-2, 1:-1], p[2:, 1:-1], p[1:-1, :-2], p[1:-1, 2:]
    lo, hi = np.minimum.reduce([y, n, s, w, e]), np.maximum.reduce([y, n, s, w, e])
    detail = y - (n + s + w + e) * .25
    gain = (1 - smoothstep(.10, .40, hi - lo)) * smoothstep(.002, .012, abs(detail))
    delta = np.clip(np.clip(strength, 0, .35) * gain * detail, -.03, .03)
    delta = np.clip(y + delta, lo, hi) - y
    out = np.rint(np.clip(c + delta[:, :, None], 0, 1) * 255).astype('uint8')
    # Render-effect transparent exterior preserves the four outer edge rows/columns.
    src = np.asarray(image.convert('RGB'))
    out[0] = src[0]; out[-1] = src[-1]; out[:, 0] = src[:, 0]; out[:, -1] = src[:, -1]
    return Image.fromarray(out)


def fixtures(font_path):
    size = (384, 256)
    text = Image.new('RGB', size, 'white')
    d = ImageDraw.Draw(text)
    for row, px in enumerate([16, 20, 24, 30, 36]):
        d.text((12, 8 + row * 46), '漫畫细线 永鬱 0123 Abg', font=ImageFont.truetype(str(font_path), px), fill='#252525')
    yield 'small-text', text
    tone = Image.new('RGB', size, 'white'); d = ImageDraw.Draw(tone)
    for band, step in enumerate([4, 6, 8, 10]):
        for y in range(4, 256, step):
            for x in range(band * 96 + 2, (band + 1) * 96 - 3, step):
                d.ellipse((x, y, x + 1, y + 1), fill='#303030')
    d.line((0, 0, 383, 255), fill='black', width=2)
    yield 'screentone', tone
    lines = Image.new('RGB', size, '#e8e8e8'); d = ImageDraw.Draw(lines)
    for x in range(0, 384, 17): d.line((x, 0, 383 - x, 255), fill='#454545', width=1)
    for r in range(15, 120, 15): d.ellipse((192-r, 128-r, 192+r, 128+r), outline='#202020', width=2)
    yield 'thin-lines', lines
    yy, xx = np.mgrid[:256, :384]
    rgb = np.stack([70 + xx * .4, 35 + yy * .7, 210 - xx * .3], axis=-1).clip(0, 255).astype('uint8')
    color = Image.fromarray(rgb); d = ImageDraw.Draw(color)
    d.ellipse((60, 30, 310, 240), fill='#e3b294', outline='#433358', width=3)
    d.arc((70, 40, 290, 250), 180, 340, fill='#2e3046', width=5)
    d.text((95, 110), '彩页 Color', font=ImageFont.truetype(str(font_path), 24), fill='#492a49')
    yield 'color-page', color


def run_waifu(root, source, output, tile):
    command = [str(root / 'waifu2x-ncnn-vulkan'), '-i', str(source), '-o', str(output),
               '-m', str(root / 'models-cunet'), '-s', '2', '-n', '-1', '-g', '-1',
               '-t', str(tile), '-j', '1:1:1', '-f', 'png']
    # wait4 reports per-child peak RSS on Linux; no auxiliary executable is installed.
    started = time.perf_counter()
    with output.with_suffix('.log').open('w') as log:
        proc = subprocess.Popen(command, stdout=log, stderr=log)
        try:
            while True:
                pid, status, usage = os.wait4(proc.pid, os.WNOHANG)
                if pid:
                    proc.returncode = os.waitstatus_to_exitcode(status)
                    break
                if time.perf_counter() - started > 180:
                    raise TimeoutError('waifu2x exceeded 180 seconds')
                time.sleep(.01)
        finally:
            if proc.returncode is None:
                proc.kill()
                _, status, _ = os.wait4(proc.pid, 0)
                proc.returncode = os.waitstatus_to_exitcode(status)
    if proc.returncode != 0:
        raise RuntimeError(f'waifu2x failed: exit={proc.returncode}; see {output.with_suffix(".log")}')
    return {'wall_seconds': time.perf_counter() - started,
            'cpu_seconds': usage.ru_utime + usage.ru_stime,
            'peak_rss_kib': usage.ru_maxrss, 'tile': tile, 'cpu_only': True}



def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--output', type=Path, required=True)
    ap.add_argument('--waifu-root', type=Path)
    ap.add_argument('--font', type=Path, default=Path('/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc'))
    args = ap.parse_args(); args.output.mkdir(parents=True, exist_ok=True)
    if args.waifu_root:
        args.waifu_root = args.waifu_root.resolve()
        for name, expected in PINS.items():
            if digest(args.waifu_root / name) != expected: raise ValueError(f'Unverified artifact: {name}')
    records = {'samples': [], 'pins': PINS, 'font_sha256': digest(args.font),
               'fixture_license': 'Original synthetic graphics generated by this project; no manga pages downloaded.',
               'waifu_status': 'requested' if args.waifu_root else 'not-run',
               'unmeasured': ['Android/iOS/Windows GPU', 'thermal', 'energy', 'Anime4K', 'Real-ESRGAN']}
    comparisons = []
    for name, reference in fixtures(args.font):
        reference.save(args.output / f'{name}-reference.png')
        source = reference.resize((192, 128), Image.Resampling.LANCZOS)
        source_path = args.output / f'{name}-source.png'; source.save(source_path)
        baseline = source.resize(reference.size, Image.Resampling.BICUBIC)
        methods = {'bicubic-control': baseline, 'light-sharpen-020-reference': sharpen(baseline, .20),
                   'light-sharpen-035-reference': sharpen(baseline, .35)}
        record = {'sample': name, 'source_sha256': digest(source_path), 'runs': {}}
        if args.waifu_root:
            try:
                for tile in [64, 128]:
                    out = args.output / f'{name}-waifu2x-cunet-n-1-t{tile}.png'
                    record['runs'][str(tile)] = run_waifu(args.waifu_root, source_path, out, tile)
                    methods[f'waifu2x-cunet-n-1-t{tile}'] = Image.open(out).convert('RGB')
                    assert methods[f'waifu2x-cunet-n-1-t{tile}'].size == reference.size
                a = np.asarray(methods['waifu2x-cunet-n-1-t64'], dtype=int)
                b = np.asarray(methods['waifu2x-cunet-n-1-t128'], dtype=int)
                delta = abs(a - b)
                seam = np.concatenate([delta[:, 126:130].ravel(), delta[126:130, :].ravel(), delta[:, 254:258].ravel()])
                record['tile_difference'] = {'mean': float(delta.mean()), 'max': int(delta.max()), 'boundary_mean': float(seam.mean())}
            except (RuntimeError, TimeoutError, OSError) as error:
                records['waifu_failure'] = str(error)
                records['waifu_status'] = 'failed'
                # Stop this backend after the first failure. Continue independent lightweight evaluation.
                args.waifu_root = None
        for method, img in methods.items():
            img.save(args.output / f'{name}-{method}.png')
        ref = np.asarray(reference, dtype=float)
        record['mae_vs_synthetic_reference'] = {m: float(abs(np.asarray(v, dtype=float) - ref).mean()) for m, v in methods.items()}
        records['samples'].append(record)
        row = Image.new('RGB', (384 * (len(methods) + 1), 280), 'white')
        for i, (label, img) in enumerate([('reference', reference)] + list(methods.items())):
            row.paste(img, (i * 384, 24)); ImageDraw.Draw(row).text((i * 384 + 5, 5), label, fill='black')
        comparisons.append(row)
        print(json.dumps(record), flush=True)
    contact = Image.new('RGB', (comparisons[0].width, 280 * len(comparisons)), 'white')
    for i, row in enumerate(comparisons): contact.paste(row, (0, 280 * i))
    contact.save(args.output / 'comparison.png')
    (args.output / 'metrics.json').write_text(json.dumps(records, indent=2, ensure_ascii=False))


if __name__ == '__main__': main()
