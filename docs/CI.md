# Manual multiplatform CI

`.github/workflows/multiplatform-manual.yml` builds selected migration targets and
uploads temporary Actions artifacts. It has **only `workflow_dispatch`**: pushing
this branch does not run it or create a release. The existing
`android-ci-release.yml` is unchanged, including its separate release behavior.

## Registration prerequisite

GitHub requires a manually dispatched workflow to exist on the repository's
**default branch**. A migration-branch-only push does not register this new
workflow, even when using the CLI/API with a branch ref. Before first use, a
maintainer must separately approve and land this workflow on the default branch
(or approve an alternative registration change). This work does not make that
change, merge the migration, change the default branch, or modify the registered
Android workflow to work around it.

After registration, use Actions → **Multiplatform manual build** → **Run workflow**,
select the migration branch, and select the desired platforms. The default is
Android only; Windows, Linux, Wasm and iOS are opt-in. Selecting no platforms
intentionally runs no jobs. Each selected platform runs independently, so one
failure does not cancel the others. A new dispatch on the same ref does not cancel
an active build; GitHub concurrency permits one running and one pending run per
ref and may replace an older pending run.

For example, after registration, to build just Linux on a named migration branch:

```sh
gh workflow run multiplatform-manual.yml --ref YOUR_MIGRATION_BRANCH \
  -f android=false -f windows=false -f linux=true -f wasm=false -f ios=false
```

## Outputs and checks

| Selection | Runner | Build/test scope | Artifact |
| --- | --- | --- | --- |
| Android | Ubuntu 24.04 x64 | `:app:assembleDebug`, `:app:testDebugUnitTest` | Universal independent test APK (existing release signer); JUnit XML/HTML |
| Windows | Windows 2022 x64 | `:shared:desktopTest`, `:desktopApp:test`, `packageMsi`, `packageExe` | MSI and EXE; JUnit XML/HTML |
| Linux | Ubuntu 24.04 x64 | Same desktop tests under Xvfb, `packageDeb`, `packageRpm` | DEB and RPM; JUnit XML/HTML |
| Wasm | Ubuntu 24.04 x64 | Node host-logic tests, `:shared:wasmJsBrowserDistribution` (optimized production) | Static browser distribution |
| iOS | macOS 15 ARM64 | Xcode `CopyMangaNeo` scheme, Debug ARM64 simulator build | `.app` in a permission-preserving `.tar.gz`; Xcode result bundle |

Artifacts include the full commit SHA and run attempt in their names, expire
after seven days, and are available from the run page. Required binary uploads
fail if outputs are missing. Test reports/Xcode results are attempted even on
failure; missing reports warn because setup failures may prevent their creation.
There is no release publication, app-store submission, website deployment,
installer execution, device installation, or GUI acceptance in this workflow.
`:desktopApp:test` may be `NO-SOURCE`; shared desktop tests provide the substantive
JVM coverage. Wasm runs the Node-based browser-activation host-logic tests and a
production build; these are not real-browser acceptance tests. The existing
iOS scheme has no test targets, so this is explicitly build-only, not `xcodebuild
test`. A simulator artifact is not a signed IPA and cannot be installed on a
physical iPhone.

## Toolchain and security

- Uses the checked-in Gradle 9.6.0 wrapper with Temurin JDK 21. Kotlin 2.4.20,
  Compose 1.12.1, Android API 37.0 and Build-Tools 37.0.0 remain source-controlled.
- Android installs the required SDK packages explicitly. The optional existing
  `COPYMANGA_TAP_STRING` secret is passed only to its build step; missing values
  are supported by the Gradle build, but official-App request mode then remains
  unavailable. This manual job also requires the four existing release signing
  Secrets and passes `-PuseReleaseSigningForTest=true` to sign the independent
  `.migrationtest` APK consistently. Missing or invalid signing material fails
  closed. The original release workflow and local default debug signing are
  unchanged. See [Android test signing and first-install caveat](ANDROID_TEST_BUILD.md).
  Signing material is restored through step-scoped environment variables, with
  restrictive file permissions, and removed by an `always()` step before uploads.
  Configuration caching is explicitly disabled. Dispatch only trusted reviewed
  refs: the selected repository code runs with access to these existing Secrets.
- Desktop packaging uses native matching hosts with no foreign-native override.
  Windows exposes the runner's preinstalled WiX 3 tools required by JDK 21
  `jpackage`; Linux installs RPM/fakeroot and Xvfb runtime dependencies. macOS
  desktop packaging is omitted because no DMG/PKG target is configured.
- Wasm uses Node 24.19.0, Yarn Classic 1.22.22 and official Binaryen 130 Linux x64,
  checked against its published SHA-256 before extraction. They are on PATH,
  respecting the project's disabled tool-download/repository policy. Local
  overrides remain `-Pcopymanga.nodeCommand=...`, `-Pcopymanga.yarnCommand=...`, and
  `-Pcopymanga.binaryenCommand=...`. Never combine development and production
  webpack tasks in one Gradle invocation; run them separately if both are needed.
- iOS uses the runner's default Xcode and prints its version. It explicitly checks
  ARM64 to match the sole simulator Kotlin target (`iosSimulatorArm64`). The
  project's existing shell phase builds/embeds the Kotlin framework. Android app
  configuration is disabled through the Gradle project environment property,
  including the nested Xcode invocation. Code signing is disabled; no Apple
  credentials, signing profile, or provisioning update is requested.
- The token has only `contents: read`; checkout credentials are not persisted.
  There are no write/release/deploy actions. Jobs have 45–90 minute timeouts. Direct Gradle build commands use one
  worker; the nested iOS/Xcode Gradle phase retains its project defaults. Official action major versions
  match the existing workflow where applicable; hosted runners satisfy their
  Node 24 runtime requirement. These moving major tags and runner images are
  not immutable supply-chain pins.

## Validation boundary

The workflow is newly authored. YAML/Actions static checks are not successful
GitHub runs. The first dispatch must establish SDK availability, runner-specific
packaging and Kotlin/Xcode compatibility. In particular, iOS source/project
presence does not prove successful compilation, launch, networking, or platform
behavior. Do not report a selected platform as passed before its job finishes.
The first Windows/macOS runner builds and any installer/device acceptance remain
separate from prior Linux-only migration validation.

## Official references

- [GitHub manual workflow registration and branch selection](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow)
- [GitHub runner labels and architectures](https://github.com/actions/runner-images/blob/main/README.md)
- [Windows 2022 included tools (WiX 3)](https://github.com/actions/runner-images/blob/main/images/windows/Windows2022-Readme.md)
- [Java setup v5 and runtime requirements](https://github.com/actions/setup-java/tree/v5)
- [Node setup v6](https://github.com/actions/setup-node/tree/v6)
- [Artifact upload v6](https://github.com/actions/upload-artifact/tree/v6)
- [Binaryen 130 official release assets and SHA-256](https://github.com/WebAssembly/binaryen/releases/expanded_assets/version_130)
- [Compose native distribution tasks and output paths](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html)
- [Kotlin/Wasm production distribution](https://kotlinlang.org/docs/wasm-get-started.html)
