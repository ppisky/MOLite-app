# Repository Guidelines

## Scope

MOLite is a Compose Multiplatform derivative of RikkaHub connected to MOLite-core.
The current user request supersedes the former UI-only scope. Route providers,
model protocols, conversations, memory, files and MOC through the pinned core C ABI.
Do not recreate these services in Kotlin, add telemetry, or add tool/command execution.
Unsupported functions must be removed or explicitly listed as future work.

## Agent policy

Do not spawn, delegate to, or run subagents unless the user explicitly requests
multi-agent work in the current task. Higher reasoning effort is not authorization.

## Layout

- composeApp/src/commonMain: shared UI, themes, resources and core presentation state.
- composeApp/src/jvmSharedMain: JNI adapter, UTF-8/base64 and local appearance storage.
- composeApp/src/desktopMain: desktop window and system file dialogs.
- androidApp: Android lifecycle, document picker and INTERNET permission only.
- native/: thin JNI adapter and pinned core revision; no duplicated core business logic.
- composeApp/src/desktopTest: meaningful navigation, interaction, and render checks.

## Design

Follow Material 3 Expressive. Use MaterialExpressiveTheme, CardGroup, connected
choice buttons, large rounded surfaces, and whole-row click targets. Retain the
upstream preset color schemes and AGPL attribution. Default to MOLite purple with
dynamic color disabled. Persist appearance settings. Use the user-provided icon.
Service actions must call core, and pending functionality must be clearly identified.

For fidelity changes, inspect and reuse upstream UI code from Git history before
inventing replacement layouts. Model and assistant pickers use bottom sheets.
Preserve original paddings, typography, HugeIcons paths and dynamic colors.
Do not add duplicate window-inset padding around nested Scaffolds.

## Verification

- ./gradlew :composeApp:compileKotlinDesktop
- ./gradlew :androidApp:assembleDebug
- ./gradlew :composeApp:desktopTest

Use gradlew.bat on Windows. Review generated screenshots under
composeApp/build/screenshots when changing layout. No live credentials or backend
are required.

For Android visual checks, use the existing MuMu device through ADB. The original
and preview apps have separate package IDs; never clear the original app's data.
Use tools/capture_adb.py to verify foreground package, and tools/compare_ui.py on
unscaled screenshots. Record the reference APK version, viewport, crop and exact
scope. Do not call unverified pages pixel-perfect or extrapolate a regional
pixel score to the entire application.
