"""Package verified local artifacts; does not sign, publish, or build them."""
import hashlib
from pathlib import Path
import shutil
import zipfile

root = Path(__file__).resolve().parents[1]
dest = root / "dist"
dest.mkdir(exist_ok=True)
apk = dest / "MOLite-0.1.0-preview-android.apk"
shutil.copy2(root / "androidApp/build/outputs/apk/debug/androidApp-debug.apk", apk)
image = root / "composeApp/build/compose/binaries/main/app/MOLite"
assert (image / "app/resources/native/molite_core.dll").exists()
archive = dest / "MOLite-0.1.0-preview-windows-x64.zip"
with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9, strict_timestamps=False) as output:
    for file in image.rglob("*"):
        if file.is_file():
            output.write(file, (Path("MOLite") / file.relative_to(image)).as_posix())
    for name in ["LICENSE", "NOTICE", "THIRD_PARTY_NOTICES.md", "FONT-LICENSE.txt"]:
        output.write(root / name, f"MOLite/{name}")
    for file in (root / "licenses").rglob("*"):
        if file.is_file():
            output.write(file, (Path("MOLite/licenses") / file.relative_to(root / "licenses")).as_posix())
lines = []
for file in [apk, archive]:
    digest = hashlib.file_digest(file.open("rb"), "sha256").hexdigest()
    lines.append(f"{digest}  {file.name}")
    print(f"{file.name}: {file.stat().st_size} bytes")
(dest / "SHA256SUMS.txt").write_text("\n".join(lines) + "\n", encoding="ascii")
