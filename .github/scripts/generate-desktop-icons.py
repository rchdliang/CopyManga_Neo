"""Render the desktop SVG to PNG and Windows ICO (requires resvg-py==0.5.0)."""

from pathlib import Path
import struct

import resvg_py


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "artwork/copymanga-neo-desktop.svg"
SIZES = (16, 20, 24, 28, 32, 36, 40, 48, 56, 64, 72, 80, 96, 128, 256)


def render(size: int) -> bytes:
    return resvg_py.svg_to_bytes(svg_path=str(SOURCE), width=size, height=size)


def main() -> None:
    (ROOT / "desktopApp/src/main/resources/copymanga-neo.png").write_bytes(render(512))
    sizes = SIZES
    images = [render(size) for size in sizes]
    resources = ROOT / "desktopApp/src/main/resources/icons"
    resources.mkdir(parents=True, exist_ok=True)
    for size, image in zip(sizes, images):
        (resources / f"copymanga-neo-{size}.png").write_bytes(image)
    offset = 6 + 16 * len(sizes)
    entries = []
    for size, image in zip(sizes, images):
        # ICO encodes a 256px dimension as zero. Each entry embeds a transparent PNG.
        entries.append(struct.pack("<BBBBHHII", size % 256, size % 256, 0, 0, 1, 32, len(image), offset))
        offset += len(image)
    icon = struct.pack("<HHH", 0, 1, len(sizes)) + b"".join(entries) + b"".join(images)
    (ROOT / "desktopApp/icons/copymanga-neo.ico").write_bytes(icon)


if __name__ == "__main__":
    main()
