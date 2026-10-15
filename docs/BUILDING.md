# Building the migration

This project is an in-progress migration pinned to original commit `466cef7c6acd2f082a9f7432cc06a082d83bc1c9`.

## Toolchain
- JDK 21 (a full JDK, not just a JRE)
- Gradle wrapper 9.6.0
- Kotlin 2.4.20, Compose Multiplatform 1.12.1
- Android SDK platform 37.0 / Build-Tools 37.0.0 for Android
- Node.js and Yarn Classic 1.22.x on PATH for Wasm bundling (verification uses Node 26.2.0). Automatic distribution downloads are disabled to preserve the settings-level repository policy. Override executables with `-Pcopymanga.nodeCommand=/path/to/node` and `-Pcopymanga.yarnCommand=/path/to/yarn` if needed.

Set `sdk.dir` in untracked `local.properties` or `ANDROID_HOME`. Supply `COPYMANGA_TAP_STRING` externally when testing official-App request mode. Never commit this value, signing keys, credentials, or your local SDK path.

## Commands

```sh
./gradlew :app:assembleDebug
./gradlew :shared:desktopTest -PenableAndroid=false
./gradlew :desktopApp:run -PenableAndroid=false
./gradlew :shared:wasmJsBrowserDevelopmentWebpack -PenableAndroid=false
./gradlew :shared:wasmJsBrowserDevelopmentRun -PenableAndroid=false
```

Windows installers must be built and validated on Windows; Linux packages on Linux. iOS framework/simulator/device builds need macOS and Xcode. Declaring a target is not verification of that platform.

Android now launches the shared Compose UI while retaining its original native data/services and platform adapters. Duplicate Android-only screens were removed. Refer to MIGRATION_STATUS.md for the exact current integration and verification state; do not assume all target launchers or platform services are finished.

The iOS project and browser runtime configuration are documented in [IOS_BROWSER.md](IOS_BROWSER.md). Android Studio can open this project root, select the `app` run configuration and deploy to a connected Android device after SDK/Gradle sync. Actual device operation is still an acceptance step; see the verification report.

If Kotlin incremental compilation reports an internal dirty-symbol/classpath error after this structural migration, rerun the affected task with `-Pkotlin.incremental=false`. The final verification used that flag and `--max-workers=1`; it is not a suppression of compiler diagnostics or failed tests.

## Desktop application icons

The window/taskbar icon and Linux package icon use `desktopApp/src/main/resources/copymanga-neo.png`, copied byte-for-byte from the original `app/src/main/ic_launcher-playstore.png` (512 × 512). Both have SHA-256 `86cab9c79a3178dcb3cdcae8a8a2a962f97fe6dc9bcee4814e34aad0f110a8bb`. This preserves the project's original artwork; no redesign is included.

Windows packages use `desktopApp/icons/copymanga-neo.ico`, a format conversion of that same PNG containing 16, 24, 32, 48, 64, 128 and 256-pixel frames. To regenerate with Pillow:

```python
from PIL import Image
with Image.open("app/src/main/ic_launcher-playstore.png") as image:
    image.save("desktopApp/icons/copymanga-neo.ico", format="ICO",
               sizes=[(n, n) for n in (16, 24, 32, 48, 64, 128, 256)])
```

The desktop Gradle configuration wires the appropriate installer icons; the Compose window reads the PNG as a classpath resource. The transfer tray retains its existing download/progress glyph. Installer branding still needs validation on each target OS; checking icon files does not verify native packaging or taskbar behavior. See [Compose native distribution configuration](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html#application-icon).

## Web CJK font fallback and offline limits

Compose Multiplatform 1.12.1 provides automatic Noto fallback downloads for characters absent from loaded fonts, including CJK characters. The exact published `ui-1.12.1-sources.jar` contains `webMain/androidx/compose/ui/platform/NotoFontDownloader.web.kt`, whose fallback base URL is `https://fonts.gstatic.com/s/`; its CJK variant selection uses `window.navigator.language`. See [automatic font fallback](https://kotlinlang.org/docs/multiplatform/compose-web-resources.html#automatic-font-fallback).

This application keeps that online fallback and does not bundle a CJK font. A fresh offline session, blocked Google font access, or deployment policy that disallows those downloads can leave Chinese glyphs unavailable; glyph placeholders can also appear while fonts load. Do not describe browser Chinese text as self-contained or offline-verified. Merely setting the HTML language or a CSS font family does not package fonts for the canvas renderer. For self-contained deployments, a future change must bundle licensed fallback fonts and preload them through the Compose font resolver, then verify Chinese UI and novel text under blocked-network conditions.

## Windows sensitive clipboard path

Desktop sensitive-text copying uses JNA core 5.19.1 on Windows only. The bridge uses the existing realized AWT window's native handle and the standard `user32`/`kernel32` clipboard APIs; it creates no hidden window, changes no system settings, and uses no shell command or elevated privilege.

Before publishing `CF_UNICODETEXT`, it registers and successfully sets all three documented Windows exclusion formats: `ExcludeClipboardContentFromMonitorProcessing`, `CanIncludeInClipboardHistory` (DWORD zero), and `CanUploadToCloudClipboard` (DWORD zero). All native buffers are allocated and filled before opening/emptying the clipboard. Failed prerequisites never fall back to an ordinary AWT copy. If a write fails after emptying, the previous clipboard contents cannot be restored; partial metadata is cleared while the application still holds the clipboard lock. A close failure is reported as an uncertain copy result rather than retried.

Successful `SetClipboardData` calls transfer memory ownership to Windows. Only untransferred allocations are wiped/freed by the application, and the temporary UTF-16LE byte array is zeroed. This does not erase the caller's immutable JVM string or stop other applications from reading the live clipboard. The UI therefore says that Windows history/cloud exclusion was requested, not that arbitrary clipboard monitoring has been prevented. Linux keeps its existing AWT copy and limitation warning.

`WindowsSensitiveClipboardTest` uses an injected fake native API and synthetic text; it never loads the Windows adapter or accesses a real clipboard. Native execution, history/cloud behavior, native-library packaging and contention still require Windows acceptance testing. References: [Windows clipboard formats](https://learn.microsoft.com/en-us/windows/win32/dataxchg/clipboard-formats), [SetClipboardData ownership](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-setclipboarddata), and [JNA source and distribution](https://github.com/java-native-access/jna).

### Desktop single-instance coordination

Desktop startup takes an OS file lock in `<dataDir>/.desktop-instance` before opening
repositories or the image cache. The data directory is canonicalized, so aliases share
one lock. A second launch sends only a session-qualified “open the existing window”
request and waits up to eight seconds for acknowledgement. It never opens a second
graph; failed locking, unsafe coordination files, or an unacknowledged request show a
launch error instead. Retry after the first process finishes starting or exiting.

The lock file is never replaced or deleted; the OS releases its lock when the process
ends, including a crash. Stale requests cannot acknowledge a new session. Coordination
objects are created owner-only (POSIX permissions or Windows ACLs); existing data-directory
permissions and existing unsafe objects are not modified. No socket, credentials, or
external command payload is involved. Windows ACL/locking and tray reopening still
require matching-platform runtime verification; synthetic filesystem tests do not
establish native Windows behavior.

### Desktop development metadata

Desktop defaults to upstream release identity `0.10.14` / version code `29`, with
debug-only UI and novel-position instrumentation disabled. This is true for both
packaged installers and ordinary development runs; no version bump is implied.
To opt into developer diagnostics, pass `-Dcopymanga.debug=true` to the **application
JVM**. For a one-command development run on a POSIX shell, use
`JAVA_TOOL_OPTIONS=-Dcopymanga.debug=true ./gradlew :desktopApp:run`; that command-scoped
Java option also applies to Gradle's Java processes, but only the application reads
this flag. A Gradle daemon `-D` argument alone is not forwarded to the app. Any value
other than explicit `true` leaves debug disabled. Omit the override when checking
release behavior. The same resolved flag drives both About/debug UI metadata and
novel-position instrumentation. Android continues to supply its own BuildConfig.

## Kotlin 2.4.20 Wasm link-cache workaround

`kotlin.incremental.wasm=false` is set in the project because a development link after source changes failed with `Declaration redefinition happened on vTableGcTypes`. Kotlin's Wasm link-cache setting is independent of `kotlin.incremental`. A fresh nonincremental executable link and webpack build passed with the same compiler/dependencies and assertions unchanged. This disables incremental Wasm linking only; it does not establish the compiler defect's precise cause.

If recovering an older checkout that already has this failure, stop its Gradle build and move aside only `shared/build/klib/cache/wasm-js/developmentExecutable` and `shared/build/kotlin/compileDevelopmentExecutableKotlinWasmJs`, then rebuild. These are generated state; keep source, application data, npm/Yarn dependencies and other platform outputs intact. The workaround can be revisited after a Kotlin upgrade and fresh validation.

## Wasm linker memory

Full nonincremental Wasm executable linking with the framework navigation host
exhausted a 2 GiB Gradle heap (`Java heap space`) after source compilation passed.
The project now sets `org.gradle.jvmargs=-Xmx4096m`; budget additional RAM for the
OS, Kotlin/Native and other build processes. The current 10 GiB environment can
run the aggregate build serially; avoid concurrent platform builds on similarly
sized machines. `kotlin.incremental.wasm=false` remains required for the separate
Kotlin 2.4.20 generated-fragment cache issue. A heap increase addresses the observed
resource failure: the full serial platform gate subsequently passed in 5m54s,
including Wasm executable linking and webpack. This is build evidence, not app execution.

## Windows portable JAR from Linux (JDK required)

The supported Compose uber-JAR task can package explicit Windows JVM natives without
creating a Windows installer or launcher. Select the same target in both projects:

```sh
./gradlew :desktopApp:packageUberJarForCurrentOS -Pcopymanga.desktopTarget=windows-x64 -PenableAndroid=false --max-workers=1
# For a Windows ARM64 JVM, use -Pcopymanga.desktopTarget=windows-arm64 instead.
```

Output is under `desktopApp/build/compose/jars/`, labeled `windows-x64` or
`windows-arm64` rather than the build machine's OS. On the corresponding Windows
machine, use an existing full JDK 21 with the matching JVM architecture:

```bat
java -jar CopyMangaNeo-windows-x64-0.10.14.jar
```

Use the actual generated filename if a build-type classifier is present. An x64
JVM needs the x64 artifact even on an ARM64 machine. No JRE is bundled. With no
property, normal host dependency selection and native packaging remain unchanged.
The same flattening preflight and multi-release preservation protect host uber-JARs. With a
foreign target selected, native run/test/app-image/installer tasks fail rather than
produce a misleading host launcher containing foreign natives. Build and test
ordinary host artifacts without this override; it is a packaging selection, not
an instruction to execute Windows native code on Linux.

A verification task per flatten/build-type task checks its actual inputs before
packaging. Distinct duplicate service descriptors/classes or dependency signature
blocks fail closed; use a thin main JAR with intact dependency libraries if a future
dependency requires service merging. No extra fat-JAR plugin or manual service
merging is used. The inspected dependency set contains multi-release resources;
the output keeps `Multi-Release: true` and omits individual library module descriptors.
Versioned inputs without their own multi-release flag are rejected to avoid changing
their semantics. Ordinary Compose resources and native DLL/checksum files remain
inside the JAR.

Before distributing, inspect the resolved inputs and final JAR for the selected
Skiko DLL/checksum, matching JNA `jnidispatch` DLL, Compose resources, Main-Class and
all service providers. Dependency resolution/packaging and artifact inspection do
not prove Windows launch, rendering, clipboard, tray, network binding or native
file-dialog behavior. Those remain Windows runtime acceptance checks; MSI/EXE
creation still requires Windows.

### Current portable verification boundary

Windows x64 and ARM64 portable packaging now pass through production commit
`6040544`, including the focused desktop image/file fixes. Both final JARs passed 26 structural checks against their respective
93 exact resolved inputs. The checks cover ZIP CRCs/duplicates, the launcher,
multi-release behavior and module-descriptor removal, Java 21 class versions,
all three service descriptors/providers, seven Compose resources, the original
icon, and matching Skiko/JNA PE32+ DLLs. The x64 DLLs use AMD64 `0x8664`; the ARM64
DLLs use ARM64 `0xaa64`. Skiko's embedded checksum matches its exact DLL bytes.
See MIGRATION_STATUS.md for artifact sizes, SHA-256 values and build timings.

No Windows application or native DLL was executed on Linux. These are external-
JDK portable JARs, not bundled-runtime packages or installers; native Windows
acceptance remains deferred. The previously blocked official Maven dependency
resolved successfully in the republished environment. Its installed JDK required
the environment's existing Java trust store for the existing proxy; no repository
mirror, disabled certificate validation or security/network policy change was used.

Each successful preflight writes its declared resolved-input report beneath
`desktopApp/build/reports/portable-jar/`: `<target>-flattenJars-inputs.txt`, or
`host-flattenJars-inputs.txt` without an override. These reports support comparing
service providers and resources against the packaged output.
