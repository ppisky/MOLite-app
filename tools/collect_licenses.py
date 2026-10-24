"""Collect license texts from the pinned core's resolved Cargo sources."""
import json
import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
CORE = Path(os.environ.get("MOLITE_CORE_DIR", ROOT.parent / "MOLite-core"))
DEST = ROOT / "licenses"

def main():
    DEST.mkdir(exist_ok=True)
    shutil.copy2(CORE / "LICENSE", DEST / "MOLite-core-Apache-2.0.txt")
    shutil.copy2(CORE / "NOTICE", DEST / "MOLite-core-NOTICE.txt")
    shutil.copy2(ROOT / "FONT-LICENSE.txt", DEST / "JetBrains-Mono-OFL-1.1.txt")
    metadata = json.loads(subprocess.check_output(["cargo", "metadata", "--locked", "--offline", "--format-version", "1"], cwd=CORE))
    rows = ["# Pinned core dependency licenses", "", "Superset of resolved Cargo dependencies, including development and other-platform packages. See core Cargo.lock for exact checksums. Individual license expressions are metadata, not relicensing.", "", "| Package | Version | Declared license | Texts |", "|---|---|---|---|"]
    for package in sorted(metadata["packages"], key=lambda p: (p["name"], p["version"])):
        if package["name"] == "molite-core":
            continue
        source = Path(package["manifest_path"]).parent
        folder = DEST / "rust" / f"{package['name']}-{package['version']}"
        copied = []
        for path in source.rglob("*"):
            if not path.is_file() or path.stat().st_size > 1024 * 1024:
                continue
            name = path.name.upper()
            if name.startswith(("LICENSE", "COPYING", "NOTICE", "UNLICENSE")):
                relative = path.relative_to(source)
                target = folder / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(path, target)
                copied.append(target.relative_to(DEST).as_posix())
        link = f"[texts]({copied[0]})" if copied else f"[upstream]({package.get('repository') or 'https://crates.io/crates/' + package['name']})"
        rows.append(f"| {package['name']} | {package['version']} | {package.get('license') or 'See upstream'} | {link} |")
    (DEST / "RUST-DEPENDENCIES.md").write_text("\n".join(rows) + "\n", encoding="utf-8")
    # Main legal texts are available offline from the About screen.
    bundled = ROOT / "composeApp/src/commonMain/composeResources/files/licenses"
    bundled.mkdir(parents=True, exist_ok=True)
    for source, name in [(ROOT / "LICENSE", "AGPL-3.0.txt"), (DEST / "MOLite-core-Apache-2.0.txt", "Apache-2.0.txt"), (ROOT / "THIRD_PARTY_NOTICES.md", "THIRD_PARTY_NOTICES.txt"), (ROOT / "NOTICE", "NOTICE.txt"), (ROOT / "FONT-LICENSE.txt", "OFL-1.1.txt"), (DEST / "Hugeicons-MIT.txt", "Hugeicons-MIT.txt")]:
        shutil.copy2(source, bundled / name)
    shutil.copytree(DEST, bundled / "third-party", dirs_exist_ok=True)
    print(f"Collected licenses for {len(metadata['packages'])} resolved packages")

if __name__ == "__main__":
    main()
