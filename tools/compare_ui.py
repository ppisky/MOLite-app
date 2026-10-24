"""Compare unscaled ADB screenshots and save a reproducible pixel report.

Example: python tools/compare_ui.py original.png cmp.png --out build/ui-comparison/chat
Python dependencies: Pillow, numpy. No device settings or application data are changed.
"""

import argparse
import hashlib
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reference", type=Path)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--ignore-top", type=int, default=72, help="Status-bar rows, in physical pixels")
    parser.add_argument("--crop", type=int, nargs=4, metavar=("LEFT", "TOP", "RIGHT", "BOTTOM"))
    args = parser.parse_args()
    reference = Image.open(args.reference).convert("RGB")
    candidate = Image.open(args.candidate).convert("RGB")
    if reference.size != candidate.size:
        raise SystemExit(f"Screenshot sizes differ: {reference.size} vs {candidate.size}. Do not resize for pixel comparison.")
    box = args.crop or (0, args.ignore_top, *reference.size)
    if not (0 <= box[0] < box[2] <= reference.width and 0 <= box[1] < box[3] <= reference.height):
        raise SystemExit(f"Comparison rectangle is outside the screenshot: {box}")
    a = np.asarray(reference.crop(box)).astype(np.int16)
    b = np.asarray(candidate.crop(box)).astype(np.int16)
    difference = np.abs(a - b)
    same = np.all(difference == 0, axis=2)
    report = {
        "reference": str(args.reference.resolve()),
        "candidate": str(args.candidate.resolve()),
        "reference_sha256": hashlib.sha256(args.reference.read_bytes()).hexdigest(),
        "candidate_sha256": hashlib.sha256(args.candidate.read_bytes()).hexdigest(),
        "screen_size": reference.size,
        "compared_rectangle": box,
        "compared_pixels": int(same.size),
        "different_pixels": int((~same).sum()),
        "exact_match_percent": round(float(same.mean() * 100), 6),
        "mean_absolute_channel_error": round(float(difference.mean()), 6),
    }
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.with_suffix(".json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    delta = np.zeros_like(a, dtype=np.uint8)
    delta[~same] = (235, 60, 65)
    Image.fromarray(delta).save(args.out.with_name(args.out.name + "-diff.png"))
    width, height = reference.size
    panel = Image.new("RGB", (width * 2, height + 40), "#eeeeee")
    panel.paste(reference, (0, 40))
    panel.paste(candidate, (width, 40))
    draw = ImageDraw.Draw(panel)
    draw.text((12, 12), "RikkaHub 3.1.0 / original", fill="black")
    draw.text((width + 12, 12), f"CMP / exact pixels {report['exact_match_percent']}% in specified region", fill="black")
    panel.save(args.out.with_name(args.out.name + "-side-by-side.png"))
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
