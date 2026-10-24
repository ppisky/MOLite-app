"""Build the pinned MOLite C ABI plus a small JNI adapter. No server process."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
PIN = json.loads((ROOT / "native/core-version.json").read_text())

def run(args, cwd, env=None):
    subprocess.run([str(x) for x in args], cwd=cwd, env=env, check=True)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--platform", choices=["android", "desktop"], required=True)
    args = parser.parse_args()
    core = Path(os.environ.get("MOLITE_CORE_DIR", ROOT.parent / "MOLite-core")).resolve()
    if not (core / "Cargo.toml").exists():
        raise SystemExit("Clone https://github.com/ppisky/MOLite-core beside MOLite-app or set MOLITE_CORE_DIR")
    revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=core, text=True).strip()
    developing = os.environ.get("MOLITE_CORE_DEVELOPMENT") == "1"
    if revision != PIN["revision"] and not developing:
        raise SystemExit(f"Expected core {PIN['revision']}, got {revision}")
    if subprocess.check_output(["git", "status", "--porcelain", "--untracked-files=no"], cwd=core, text=True).strip() and not developing:
        raise SystemExit("Core has uncommitted tracked changes; use the pinned source")
    output = ROOT / "build/native" / args.platform
    output.mkdir(parents=True, exist_ok=True)
    # Skip only when both Rust and adapter inputs are unchanged and outputs exist.
    inputs = [ROOT / "native/molite_jni.cpp", Path(__file__), core / "include/molite.h", ROOT / "native/core-version.json", core / "Cargo.toml", core / "Cargo.lock"]
    stamp = output / "stamp.json"
    signature = {str(p): p.stat().st_mtime_ns for p in inputs}
    expected = [output / a / n for a in ["arm64-v8a", "x86_64"] for n in ["libmolite_core.so", "libmolite_jni.so"]] if args.platform == "android" else [output / "molite_core.dll", output / "molite_jni.dll"]
    if stamp.exists() and json.loads(stamp.read_text()) == signature and all(p.exists() for p in expected):
        print(f"MOLite {args.platform} native libraries are current")
        return
    if args.platform == "android":
        sdk = Path(os.environ.get("ANDROID_HOME", os.environ.get("ANDROID_SDK_ROOT", "D:/Android/Sdk")))
        ndk = Path(os.environ.get("ANDROID_NDK_HOME", sdk / "ndk/28.2.13676358"))
        toolchain = ndk / "toolchains/llvm/prebuilt" / ("windows-x86_64" if os.name == "nt" else "linux-x86_64")
        exe = ".exe" if os.name == "nt" else ""
        for target, abi, clang_target in [("aarch64-linux-android", "arm64-v8a", "aarch64-linux-android26"), ("x86_64-linux-android", "x86_64", "x86_64-linux-android26")]:
            env = os.environ.copy()
            compiler = toolchain / f"bin/clang{exe}"
            env[f"CARGO_TARGET_{target.upper().replace('-', '_')}_LINKER"] = str(compiler)
            env[f"CC_{target.replace('-', '_')}"] = str(compiler)
            env[f"CFLAGS_{target.replace('-', '_')}"] = f"--target={clang_target}"
            env[f"AR_{target.replace('-', '_')}"] = str(toolchain / f"bin/llvm-ar{exe}")
            env[f"CARGO_TARGET_{target.upper().replace('-', '_')}_RUSTFLAGS"] = f"-C link-arg=--target={clang_target} -C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-soname,libmolite_core.so"
            env["CMAKE"] = str(sdk / f"cmake/3.22.1/bin/cmake{exe}")
            env["ANDROID_NDK_HOME"] = str(ndk)
            run(["cargo", "build", "--locked", "--release", "--lib", "--target", target], core, env)
            dest = output / abi
            dest.mkdir(exist_ok=True)
            shutil.copy2(core / f"target/{target}/release/libmolite_core.so", dest)
            run([toolchain / f"bin/clang++{exe}", f"--target={clang_target}", "-shared", "-fPIC", "-O2", "-std=c++17", "-static-libstdc++", "-Wl,-z,max-page-size=16384", "-I", core / "include", ROOT / "native/molite_jni.cpp", "-L", dest, "-lmolite_core", "-o", dest / "libmolite_jni.so"], ROOT)
            run([toolchain / f"bin/llvm-strip{exe}", dest / "libmolite_core.so", dest / "libmolite_jni.so"], ROOT)
    else:
        if sys.platform != "win32":
            raise SystemExit("Desktop native build currently supports Windows x64; Android supports arm64 + x64")
        run(["cargo", "build", "--locked", "--release", "--lib"], core)
        shutil.copy2(core / "target/release/molite_core.dll", output)
        vswhere = Path(os.environ["ProgramFiles(x86)"]) / "Microsoft Visual Studio/Installer/vswhere.exe"
        vs = subprocess.check_output([str(vswhere), "-latest", "-products", "*", "-property", "installationPath"], text=True).strip()
        java = Path(os.environ["JAVA_HOME"])
        build = output / "build.cmd"
        build.write_text(f'@echo off\ncall "{vs}\\VC\\Auxiliary\\Build\\vcvars64.bat" >nul\nif errorlevel 1 exit /b 1\ncl /nologo /EHsc /std:c++17 /O2 /LD /I"{java / "include"}" /I"{java / "include/win32"}" /I"{core / "include"}" "{ROOT / "native/molite_jni.cpp"}" /Fo"{output / "molite_jni.obj"}" /link "{core / "target/release/molite_core.dll.lib"}" /OUT:"{output / "molite_jni.dll"}" /IMPLIB:"{output / "molite_jni.lib"}"\n', encoding="utf-8")
        run(["cmd", "/c", build], ROOT)
        packaged = ROOT / "build/native/packaged/common/native"
        packaged.mkdir(parents=True, exist_ok=True)
        for name in ["molite_core.dll", "molite_jni.dll"]:
            shutil.copy2(output / name, packaged / name)
    stamp.write_text(json.dumps(signature, indent=2))

if __name__ == "__main__":
    main()
