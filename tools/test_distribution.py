"""Verify the Windows distributable's own JVM and native libraries."""
import os
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
image = root / "composeApp/build/compose/binaries/main/app/MOLite"
test = root / "build/distribution-smoke"
test.mkdir(parents=True, exist_ok=True)
data = Path(tempfile.mkdtemp(prefix="native-", dir=test))
subprocess.run([str(image / "MOLite.exe"), "--verify-native", str(data)], check=True, timeout=30)
assert (data / "molite.sqlite3").exists(), "Packaged app did not create its core database"
print("PASS: packaged Windows executable, JVM and native core ABI")
