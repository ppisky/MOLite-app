"""Convert the five upstream brand SVG assets to shared Compose vectors (no rasterization)."""
import argparse
from pathlib import Path
import xml.etree.ElementTree as ET
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.svgLib.path import parse_path


def number(value):
    value = str(value)
    return str(float(value.rstrip("%")) / (100 if value.endswith("%") else 1)) + "f"


def color(value):
    return "Color.Black" if value == "currentColor" else "Color(0xFF" + value.lstrip("#").upper() + ")"


def convert(assets, output):
    names = {"openai": "OpenAi", "gemini-color": "Gemini", "claude-color": "Claude", "deepseek-color": "DeepSeek", "qwen-color": "Qwen"}
    source = """package me.rerere.rikkahub.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.unit.dp

// Original brand SVG paths and gradients from RikkaHub assets, shared across targets.
"""
    for stem, name in names.items():
        root = ET.parse(assets / (stem + ".svg")).getroot()
        gradients = {p.attrib["id"]: p for p in root.iter() if p.tag.endswith("linearGradient")}
        source += f'\nval {name}BrandIcon by lazy {{\n    ImageVector.Builder("{name}", 24.dp, 24.dp, 24f, 24f).apply {{\n'
        for index, path in enumerate(p for p in root.iter() if p.tag.endswith("path")):
            # Expand SVG arcs to cubic curves before Compose parses the path.
            pen = SVGPathPen(None)
            parse_path(path.attrib["d"], pen)
            source += f'        val path{index} = PathParser().parsePathString("{pen.getCommands()}").toNodes()\n'
            fill = path.attrib.get("fill", root.attrib.get("fill", "currentColor"))
            if fill.startswith("url("):
                gradient = gradients[fill[5:-1]]
                stops = [number(s.attrib.get("offset", "0")) + " to " + color(s.attrib["stop-color"]) + ".copy(alpha = " + number(s.attrib.get("stop-opacity", "1")) + ")" for s in gradient]
                absolute = gradient.attrib.get("gradientUnits") == "userSpaceOnUse"
                if not absolute:
                    source += f"        val bounds = path{index}.toPath().getBounds()\n"

                def position(x, y):
                    if absolute:
                        return "Offset(" + number(gradient.attrib[x]) + ", " + number(gradient.attrib[y]) + ")"
                    return "Offset(bounds.left + bounds.width * " + number(gradient.attrib.get(x, "0%")) + ", bounds.top + bounds.height * " + number(gradient.attrib.get(y, "0%")) + ")"

                brush = "Brush.linearGradient(" + ", ".join(stops) + ", start = " + position("x1", "y1") + ", end = " + position("x2", "y2") + ")"
            else:
                brush = "SolidColor(" + color(fill) + ")"
            rule = "EvenOdd" if path.attrib.get("fill-rule", root.attrib.get("fill-rule")) == "evenodd" else "NonZero"
            source += f"        addPath(path{index}, fill = {brush}, pathFillType = PathFillType.{rule})\n"
        source += "    }.build()\n}\n"
    output.write_text(source, encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("assets", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    convert(args.assets, args.output)
