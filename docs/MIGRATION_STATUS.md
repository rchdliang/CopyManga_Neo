# CopyManga Neo multiplatform migration

Baseline: `rchdliang/CopyManga_Neo` commit `466cef7c6acd2f082a9f7432cc06a082d83bc1c9` (2026-10-14).
Priority: Android > Windows > iOS > Linux > Wasm.

Status: **continuing parity passes; runtime acceptance deferred, not release-ready**.

The Android UI and domain implementation now live in the shared module. Android retains its existing Room/data repositories, Hilt wiring, download services and native storage/permission adapters. The old Android-only screen copies have been removed; an activity alias preserves the original launcher component. JVM, iOS and browser hosts use portable repositories and platform-specific adapters. An unavailable operation must report its limitation rather than return fake success.

No production credentials or signing keys belong in this repository. `COPYMANGA_TAP_STRING` remains supplied externally. This snapshot does not include uncommitted changes on the user's PC. See BUILDING.md and IOS_BROWSER.md for configuration; browser and mobile-bundle constants are not secret storage.

## Current native CI verification: a0d7e18 / c5f3dd68 (2026-10-15)

Local source **a0d7e185f1811ccd14016c96c05febe93df54f5a** and published
**c5f3dd681258e1ce3bf2074ff46c0f098919f7cd** have the same file tree. All five
configured target jobs have successful CI evidence, across the two runs below;
this is build/test/package verification, not full migration or runtime acceptance.

- [Windows run 38728360827](https://github.com/rchdliang/CopyManga_Neo/actions/runs/38728360827)
  at `c5f3dd68` passed **387 tests in 76 JUnit suites**, with zero failures,
  errors or skipped tests. `createRuntimeImage`, `packageMsi` and `packageExe`
  succeeded on Windows. The [verified MSI/EXE artifact](https://github.com/rchdliang/CopyManga_Neo/actions/runs/38728360827/artifacts/11970037211)
  expires **2026-10-22**. The EXE has no Authenticode signing certificate.
  Installation and GUI launch were not tested.
- [Run 38726264087](https://github.com/rchdliang/CopyManga_Neo/actions/runs/38726264087)
  at earlier published `e546` passed the Android, Linux, Wasm and iOS simulator
  jobs. Android's CI unit-test step passed; the **107-test count** is separately
  verified by the local Android run, not an inferred CI XML count. iOS built an
  **unsigned ARM64 simulator application with Xcode**, beyond the earlier KLIB
  checks; no simulator launch, physical-device IPA or signing is claimed.
- That earlier run's Windows job passed 386/387 tests and produced no installer.
  The failure was a URI-path alias assertion; `a0d7e18` / `c5f3dd68` changes only
  that test, so the four earlier successful jobs cover the same production code.
  Current local Linux-host desktop tests also passed all 387 cases.

The production Windows export fix (`21f4621`) addresses JDK attributes returning
null `fileKey`: it queries a file handle for volume identity and the full 128-bit
file ID rather than trusting size/timestamps alone. It retains changed-file,
ordinary-file and no-reparse-point protections and fails closed if identity is
unavailable. The later assertion fix compares filesystem identity across Windows
URI aliases without weakening those production guards.

The Android debug app (`30fb574`) uses
`com.rchdliang.copymanga_neo.migrationtest` and **CopyManga Neo 测试版**, leaving the
daily app's identity unchanged. See ANDROID_TEST_BUILD.md for data separation,
shared export-directory limits and debug-signature compatibility. Local and CI
debug certificates differ; do not promise in-place updates between those APKs.

| Windows native installer | Bytes | SHA-256 |
| --- | ---: | --- |
| MSI | 116,570,868 | `c0fd271e253a1ac76b3319233edb95aedd93ae3f3735ad1153631e7bb012f3a8` |
| EXE | 117,172,736 | `e3877e62f87481c7cbd2aa2d72421e242f2e92aa7eafd3b4da40791a1aa61105` |

Current CI downloads: [Android](https://github.com/rchdliang/CopyManga_Neo/actions/runs/38726264087/artifacts/11968473913),
[Linux](https://github.com/rchdliang/CopyManga_Neo/actions/runs/38726264087/artifacts/11967549408),
[Wasm](https://github.com/rchdliang/CopyManga_Neo/actions/runs/38726264087/artifacts/11968409337),
[iOS simulator](https://github.com/rchdliang/CopyManga_Neo/actions/runs/38726264087/artifacts/11969610040).
The separate portable Windows JARs built at `30fb574` each passed 17 structural
checks against 93 inputs; they still require matching external JDK 21. Older
Linux/Wasm packages at `518062a` are historical, not these current CI artifacts.

Remaining acceptance includes application GUI/install flows, real-account network
and reader/download/export behavior, Android real-device coexistence, iOS physical
device signing/runtime, and browser permissions/CORS. Non-Android Wi-Fi binding
and iOS/browser background-transfer differences remain documented limitations.
All checkpoint sections and hashes below are historical unless explicitly reused.

## Earlier validated code checkpoint: 518062a (2026-10-15)

Checkpoint **518062a** corrects two portable search/discovery compact-layout
initial defaults from `true` to Android's baseline `false`. Three regression
tests were added. Existing explicit choices remain preserved: storage JSON uses
`encodeDefaults = true`. This is a source-confirmed preference-default correction,
not a claim of exhaustive parity. The desktop text-share cancellation correction
and local Binaryen build configuration from previous checkpoints remain included.

Validation after the default correction:

- **382 shared desktop tests passed**, and desktop application compilation passed.
- Android debug assembly and **105 Android JVM tests** passed in **1m12s** (8
  executed / 66 up-to-date tasks; the unit-test task executed). APK ZIP CRCs and
  v2 signature verification passed.
- Windows x64 and ARM64 packaging each passed in **26s** (5 executed / 14
  up-to-date tasks). Packaging/preflight regenerated; desktop compilation reused
  its already-passed output. Each artifact passed **23 structural checks against
  93 inputs**, within the verifier scope recorded below.
- Linux portable-JAR and app-image packaging reran successfully in **24s** (6
  executed / 17 up-to-date tasks). The JAR passed **21 structural checks / 93
  inputs**; the app image passed **6 structural checks**, with safe archive paths
  and executable modes verified. The image contains 209 files / 206,899,358 bytes.
  The prior 21-check non-GUI runtime probe was **not rerun**; current runtime
  modules were verified byte-identical to those previously probed.
- Both iOS Arm64 device/simulator KLIB compile tasks executed and passed in
  **1m6s** (6 executed / 7 up-to-date tasks). No framework/Xcode linkage or device
  runtime was tested.
- Wasm development passed in **1m46s** (7 executed / 22 up-to-date tasks);
  separate production distribution passed in **3m7s** (8 executed / 23 up-to-date
  tasks). Linking, Binaryen 130 optimization, webpack and distribution reran.
  The production ZIP contains 17 files and passed **24 static checks**. Dynamic
  dependency and asset-size warnings remain; no browser application was run.

| Current artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `CopyMangaNeo-windows-x64-0.10.14.jar` | 92,462,144 | `961e969f5be86cdd9968adbb2e5c832915a07f95911dc07fecc242f5f7868db3` |
| `CopyMangaNeo-windows-arm64-0.10.14.jar` | 91,623,556 | `bca4bd775ec759eb351ca1e01a9e9e169cddba55c6ccfd2e95c6d9e35b8dd2c9` |
| `app-debug.apk` | 88,445,141 | `ec5fa4aa47b52460a7b216210b371dcddfd543dd6209919d5bd6a7a718e6ec28` |
| `CopyMangaNeo-linux-x64-0.10.14.jar` | 94,184,439 | `8b1ad906dac8e619ec09c83cb18973a944bfab8efb20ba31bc56605f52136433` |
| `CopyMangaNeo-linux-x64-0.10.14-self-contained.tar.gz` | 120,506,588 | `d6f6e762b832f0ffbec9ee462019a817de42b3c80a41a15ada270746ff9baa8e` |
| `CopyMangaNeo-wasm-production-20261015.zip` | 6,686,887 | `d0d8e7bf783af904023d4fedfead4233766a79881389c7ba4e3f8250e86d9705` |

The final dated source snapshot must include **518062a** and the subsequent
validation documentation. Earlier snapshots and hashes below are historical.
No Android device/emulator, Windows/Linux application GUI or browser runtime
acceptance is claimed. Portable Windows JARs still need matching external JDK 21;
iOS Swift/Xcode linkage and all documented platform limitations remain deferred.

## Earlier restored validation: 90aaeed (2026-10-15)

Checkpoint **90aaeed** fixes desktop text sharing after caller cancellation: the
caller must still be active before opening the chooser and again after its modal
loop returns, before copying text or opening mail. Cancellation and action-failure
regressions are covered by desktop tests.

Gradle **9.6.0** and Temurin **JDK 21.0.7+6** were restored from official packages
with matching published checksums. The environment-local helper uses the existing
proxy, Java trust store and workspace dependency cache; no repository build-script
change or disabled certificate validation was needed. Android SDK platform 37.0
revision 2, Build-Tools 37.0.0 and platform tools were restored from official
packages after SDK license approval.

Fresh validation of this checkpoint:

- **379 shared desktop tests passed**, with no failures/errors/skips; desktop
  application compilation passed (combined gate: 47s).
- Android debug assembly and **105 retained Android JVM tests** in 15 suites
  passed, with no failures/errors/skips (combined gate: 5m31s). Debug APK ZIP
  integrity and v2 signature verification passed. Two native libraries were
  packaged unstripped; this warning did not block the build.
- iOS Arm64 device and Arm64 simulator shared KLIB compilation passed in
  **1m53s**. This compiles Kotlin libraries only; Swift/Xcode linkage and
  simulator/device runtime were not tested.
- Windows x64 and ARM64 portable JARs were rebuilt with the cancellation fix.
  Each passed **23 structural checks against 93 resolved input JARs**: ZIP CRCs
  and duplicate entries, launcher/manifest, removal of module descriptors,
  Java 21 compatibility of the checked launcher/platform classes, all three
  service descriptors/providers, seven Compose resources, both multi-release
  entries, original icon, and architecture-matched Skiko/JNA DLLs with the
  Skiko checksum. This is the current verifier's scope, not a rerun of all 26
  checks recorded at older checkpoints.
- A bounded source comparison against original baseline **466cef7** found no
  new concrete reader regression. It does not establish full behavior parity.

| Current artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `CopyMangaNeo-windows-x64-0.10.14.jar` | 92,462,145 | `976ddf14fc590e7a5cda81d37829b77bed2da0759251bc34e84b09d9612319fb` |
| `CopyMangaNeo-windows-arm64-0.10.14.jar` | 91,623,557 | `84fe38d49bb0afee631971e7ab13764ef884ead20ebc22d2e8691ee695ac302e` |
| `app-debug.apk` | 87,722,446 | `2e79bbc0879134a586eda1dace699243de21a10f3361be4476ad3ef5b5975479` |

The source ZIP and Git bundle preserve the **90aaeed** working tree; the bundle
comes from a shallow reconstruction, lacks earlier upstream history and is not a
complete-history backup. After cloning, restore the known shallow boundary
`4aa51451db943c6b98b30bc623e63402b1a1a4c4` in the clone's Git shallow file; this
procedure passed `git fsck --full`, history traversal and clean-worktree checks.
Without that metadata, ordinary fetch into an empty repository and traversal
into missing parents can fail. The ZIP is the source-only fallback. Later
documentation updates are separate from those
immutable source snapshots. These Windows JARs require
an external, architecture-matched JDK 21; they have no bundled runtime and are
not MSI/EXE installers. No Android device/emulator or native Windows application
was run. The post-checkpoint Wasm build follow-up is recorded below. Native
acceptance, iOS Swift/Xcode linkage and documented platform
limitations remain deferred. This is not full parity or release acceptance.

### Wasm build follow-up: a5c37c5

Validated code checkpoint **a5c37c5** adds the build-configuration correction below.
The development webpack bundle and optimized production webpack bundle now pass.
The production build exposed Binaryen's automatic project Ivy repository, which
conflicted with the existing `FAIL_ON_PROJECT_REPOS` policy. A minimal root
`build.gradle.kts` change disables that tool download and selects local `wasm-opt`
through `copymanga.binaryenCommand`, matching the Node/Yarn tool pattern. The
repository policy remains unchanged. This build-configuration follow-up is not
included in the immutable source ZIP/bundle named `90aaeed`; use the final dated
source snapshot containing `a5c37c5` and these documentation updates.

The validated tools were Node **24.19.0**, Yarn **1.22.22** and official Binaryen
**130**. A writable `kotlin.user.home` resolves the restored environment's
read-only default tooling-cache path. Development webpack passed; production
executable linking and Binaryen optimization executed successfully, and the
separate production webpack invocation passed in **18s**. Run development and
production webpack in separate Gradle invocations: combining them exposed a
Gradle 9.6 shared-npm-output implicit-dependency validation failure, which was
not suppressed. Development reported one dynamic-dependency warning; production
reported four warnings including dynamic-dependency and asset/entry-size warnings.
No browser application was executed. These are bundle/build results, not browser
runtime, CORS, text rendering or cross-platform parity acceptance.

### Linux packaging follow-up: a5c37c5

Linux x64 portable JAR and self-contained application-image builds passed in
**1m36s**. The portable JAR passed **21 structural checks against 93 inputs**.
The application image contains 209 files / 206,899,356 bytes; dependency,
launcher/configuration/icon and extracted Skiko native-resource checks passed.
The compressed archive preserves all image files and executable modes.

A non-GUI probe passed **21 runtime checks**, including GBK/GB18030/MS936/MS950,
equality with the full JDK's charset catalog, required modules, EC/TLS, 150 trust
anchors, application/JNA class lookup and icon resources. The probe used a copy
of the image runtime with byte-identical modules and the original JDK's Java
launcher; it did not run the packaged application launcher, UI or network.
No DEB/RPM installer or native GUI acceptance is claimed. Existing platform and
deprecation warnings remain. Windows JAR hashes were unchanged by these builds.

| Linux artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `CopyMangaNeo-linux-x64-0.10.14.jar` | 94,184,440 | `b88aa22588afd421f3ae6698f8e37dc6008e3fcd17c332bd91117bdd496b3c8d` |
| `CopyMangaNeo-linux-x64-0.10.14-self-contained.tar.gz` | 120,503,221 | `7cdaa152fe65c8879445a4c75e040c5b28294a2c52cd78bc573b888aacb8bf0e` |

All checkpoint sections below are historical; current artifact hashes and
validation scope are those above.

## Desktop image/file parity follow-up (2026-10-15)

Production commit **6040544** adds three bounded, source-confirmed corrections:

- **6a15373:** selectable BMP avatars now reach the existing bounded ImageIO decoder; the WebP probe no longer rejects BMP through the comic-export format allowlist.
- **b67a5b5:** bare renamed save targets inherit the suggested suffix before overwrite confirmation and archive reservation. Explicit suffixes and dotfiles remain unchanged.
- **6040544:** document writes check cancellation after staging and before publication, preserving existing bytes and cleaning temporary files on cancellation/failure.

The three focused desktop suites passed **11 tests** (4 avatar, 4 destination,
3 document-write), with no failures/errors/skips, in **23s**. A deterministic
cancellation test first reproduced publication after cancellation, then passed
with the fix. Tests exercise real BMP/JPEG decoding and local filesystem writes
on Linux; no native Windows GUI or DLL was run. Independent source review found
no further concrete issue in these changes. Adjacent picker-cancel, reader
save/share state and bounded document-read review found no additional confirmed
defect. These are bounded checks, not exhaustive parity acceptance.

Both affected Windows portable JARs were rebuilt once after the final code change:
x64 in **13s**, then ARM64 in **11s**. Earlier exported artifacts remain preserved
separately. The current artifacts are:

| Portable artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `CopyMangaNeo-windows-x64-0.10.14.jar` | 92,460,228 | `d968dbd436b9f2bf8161cd42d984f966bfdd183ef33f708e39beffe749860f36` |
| `CopyMangaNeo-windows-arm64-0.10.14.jar` | 91,621,640 | `6211b1b9895ea9c31cc46ce3f2e7054ff1c74ae5a0a70ed5f4f5ec7020168c21` |

Each passed **26 structural checks against 93 exact inputs**: ZIP integrity and
unique entries; correct Main-Class and Multi-Release flag; removal of 15 module
descriptors; preservation of 2 versioned implementations, all **29,037 classes**,
3 service descriptors/providers, 7 Compose resources and the original icon;
Java 21 class compatibility; exact Skiko DLL and embedded checksum; and matching
JNA DLL. PE32+ architecture is AMD64 `0x8664` or ARM64 `0xaa64` respectively.
The corresponding input reports, input hashes and project JARs accompany the
verification evidence. These require a matching external Windows JDK 21, without
a bundled runtime or MSI/EXE installer. Android/iOS/Wasm and the old aggregate
suites were not rerun for these desktop-only changes. Native runtime acceptance,
iOS linkage and the documented platform limitations remain deferred.

## Initial restored Windows packages and picker correction (2026-10-15)

The saved `807dd3a` bundle was restored and verified in an isolated checkout.
Both Windows portable JARs now build with the pinned JDK 21 / Gradle 9.6.0 /
Kotlin 2.4.20 / Compose 1.12.1 toolchain. The initial x64 build took 6m29s with
a cold dependency cache. The final x64 rebuild, including the WebP avatar-picker
filter correction at **86fb7b7**, passed in 39s; ARM64 packaging then passed in 12s.
All Gradle builds used one worker and nonincremental, in-process Kotlin compilation.

| Portable artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `CopyMangaNeo-windows-x64-0.10.14.jar` | 92,458,098 | `1d2dadc0cde7b6e502167c9e3620724e4ad105ee7c124fe1d6802d8358909208` |
| `CopyMangaNeo-windows-arm64-0.10.14.jar` | 91,619,510 | `5805116b864873e103cc8c8ca24b08dd91c0719b6a2d36ffa8d6b780a36a7665` |

Each final artifact passed **26 structural checks against its exact 93 input JARs**:
full ZIP CRC/duplicate inspection, correct launcher class, `Multi-Release: true`,
removal of all 15 library module descriptors, preservation of both versioned
implementations and all 29,036 classes, Java 21 class-version compatibility,
all three service descriptors and provider classes, seven Compose resources,
and the byte-identical original icon. Skiko 0.150.1 DLL bytes and embedded SHA-256
match the inputs; JNA 5.19.1's target DLL matches as well. Both DLLs have PE32+
headers with AMD64 `0x8664` for x64 or ARM64 `0xaa64` for ARM64. Input paths and
SHA-256 values are retained in the accompanying verification reports.

The one-line picker correction exposes supported `.webp` avatars in the default
file filter. It does not change the existing decoder. This session compiled and
packaged the corrected desktop source; it did not rerun the earlier aggregate
test suites or claim native file-picker interaction.

These JARs require a matching external **Windows JDK 21**. They contain no bundled
Java runtime and are not MSI/EXE installers. No Windows DLL or application was
executed on Linux. Native launch/rendering/clipboard/tray/dialog acceptance,
iOS Swift/Xcode linkage and the platform limitations below remain unverified.
Packaging success does not establish full feature parity or release readiness.

## Previous aggregate verified checkpoint (2026-10-15 03:25 UTC)

Production code through **b3a8f18** passed the complete serial aggregate gate in **5m54s**:

- Android debug assembly and all **105 retained Android JVM tests**.
- Desktop application compilation and all **365 shared desktop tests**, with no failures, errors or skipped tests.
- iOS Arm64 device and Arm64 simulator shared KLIB compilation; no Xcode/Swift linkage or device-runtime claim.
- Full Wasm development executable and webpack bundle. One generated-runtime dynamic dependency warning remains.

The real shared screens now use a single Navigation Compose stack and framework predictive transitions. Previous-route composition is inactive/cache-only; nested modal input remains ahead of the route bridge. The synthetic raster tests exercise actual shared-cover geometry at gesture progress, cancellation and commit, while the state scene covers private payload restoration, distinct repeated visits, rapid push/pop cleanup and main-tab return. No private route payloads are bound to browser URLs. The obsolete inert snapshot/custom stack implementation and its dedicated tests were removed; replacement behavioral coverage remains. These checks do not certify native gesture performance or every real cover image.

The canonical account-owner, desktop Escape/reflow, route-effect, image cache-only and persistence fixes are included. The last correction persists discovered share hosts across restart, retries discovery after WWW fallback, and preserves generation/rollback semantics. A bounded source review of core screen/repository wiring, downloads/offline access, account actions and share/save paths found no additional confirmed gap; it is not exhaustive runtime acceptance.

Kotlin 2.4.20's independent Wasm incremental linker cache is disabled after a reproducible internal cache failure. The full framework-linked build also needed a 4 GiB Gradle heap; the unchanged final code now passes with that documented resource setting.

**Remaining work is explicit:** native Android/Windows acceptance is deferred by request; Windows/macOS native installers and iOS Swift/Xcode linkage need the corresponding systems. Non-Android Wi-Fi confinement remains fail-closed, and iOS/browser background transfer equivalents are not implemented. Browser CORS/API restrictions and memory-only credentials remain platform differences. See WINDOWS_WIFI_DOWNLOADS.md and the acceptance checklist. This checkpoint is not a claim of complete cross-platform parity or release readiness.

## Earlier desktop packaging follow-up (2026-10-15 04:00 UTC)

The user explicitly made desktop Wi-Fi-only confinement optional. Its isolated
native Windows implementation is parked after a successful compile-only gate;
it is not enabled in the validated application and does not block migration.
The existing unsupported-policy explanation remains instead of silently using
an unrestricted network.

Through **b178091**, Linux `:desktopApp:createDistributable` produces a 199 MB
application image with its bundled Java runtime. Dependency analysis and a
separate non-GUI probe exposed missing instrumentation, management, Unsafe and
extended charset providers in the initial minimal runtime. The distribution
now includes `java.instrument`, `java.management`, `jdk.unsupported` and
`jdk.charsets`. The probe reused byte-identical packaged runtime modules and
passed all checks: GBK/GB18030/MS936/MS950 decoding, referenced runtime classes,
EC/TLS providers, default trust anchors, application/JNA class lookup and icon
resources. It did not launch the application UI or make network requests.
This is Linux app-image packaging evidence, not a Windows installer or native
application acceptance result.

The guarded uber-JAR path also builds offline on Linux through **1fcafc3**.
Structural inspection confirms all three service-provider descriptors from the
93 resolved input JARs, valid ZIP contents, the application icon and Linux Skiko
payload, `Multi-Release: true`, and removal of per-library module descriptors.
The resulting Linux JAR is 94,180,355 bytes. It requires a matching external
JDK 21; the separate app image includes its runtime.

Explicit `windows-x64` / `windows-arm64` target selection is implemented, with
foreign-host run/test/native-installer guards. Windows-selected shared and desktop
source compilation passed, but at this earlier checkpoint Windows x64 packaging
was blocked by the missing `org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.150.1`
JAR. That environment's network policy denied its official Maven download, and
the offline build confirmed it was not cached. This earlier blocker is superseded
by the successful Windows artifacts
and architecture-specific verification above. See BUILDING.md for prerequisites.

Browser Escape now ignores repeats, modified keys and already-consumed events.
The synthetic Node adapter suite passes 25 cases and the full Wasm development
bundle passes again at **08a3aa8**. iOS background-transfer constraints are
recorded in IOS_BACKGROUND_TRANSFERS.md; that document does not enable background
transfers.

All sections below are historical checkpoints, superseded by the current implementation and results above where they differ.

## Continued parity passes (2026-10-15 02:02 UTC)

Runtime deferral does not pause implementation. The following source-backed gaps have been addressed in additional commits:

- Safe confirmed desktop archive replacement, preserving originals on failure/cancellation; tray reopening restores minimized windows.
- Focus-scoped comic/novel keyboard navigation, respecting RTL, selection, dialogs and text fields without changing source-coordinate persistence.
- Dialog-local comment feedback and independent modal/inline comment models, so opening comments does not erase another panel's draft.
- Corrupt novel cache repair, validated illustration downloads, semantic/size validation of local reading records, and usable app startup with a retained reading-library error.
- Atomic OS-protected account envelopes, absent-only legacy recovery, empty logout tombstones, source-matched cross-mode invalidation and remembered-login preferences.
- Original desktop application icons and Windows-sensitive clipboard exclusion requests with transfer-aware native memory cleanup; no ordinary clipboard fallback on protection failure.
- Android predictive gesture progress/cancel ownership and iOS edge/hardware-back bridge, including leading-edge direction. Previous-route/shared-cover predictive preview remains incomplete.
- Browser fresh-click gates for asynchronous save/share/clipboard/fullscreen operations, retryable notification permission, cancellation and ownership cleanup.
- Bounded inert previous-route preview, full-palette/session/viewport invalidation, profile exclusion, background clearing and non-consuming pointer capture. This is not a live previous route or full shared-cover transition.
- Route-local lifecycle and back eligibility during overlapping navigation animations; outgoing readers pause instead of retaining host-resumed state, and cancelled predictive gestures do not pause the current reader.
- Recoverable desktop close/background/explicit-quit flow, awaiting graph transfer cleanup and preserving the queue. Failed reading persistence offers explicit retry or discard of unsaved progress without deleting the library.
- Canonical-data-directory single-instance locking and acknowledged reopen requests; no second repository/cache writer starts while another owns the directory.
- Source-matched WEB API/browser navigation headers, bounded WebP clipboard/avatars with orientation handling, quiet native share cancellation, release-safe portable debug defaults, and reader keep-awake release on pause.
- Corrupt queue/download-record/export-task isolation with preserved bytes and explicit user-action errors; unrelated settings/library/novel screens remain available.
- Bounded GET-only public CDN redirects and source-matched safe resource headers. API/write/quota requests do not gain automatic redirects or replay.

Verification at this checkpoint:

- Android debug assembly and desktop application compilation pass through production commit 568eb6c.
- Shared desktop suite: **344 passed**, no failures/errors/skips, including native in-memory WebP codecs, route lifecycle, quiet cancellation, single-instance, transfer-corruption and bounded preview policies. The additional raster scene test passed at 797245f, checking downsampled pixels, click propagation and immutable readback after layer cleanup.
- Retained Android JVM suite: **104 passed** after fixing one cross-module smart-cast assumption; assertions were retained. The latest retained run passed through a6d7d5e, including the default-success download-readiness interface hook. Subsequent portable transport/desktop changes do not alter the retained Android network implementation.
- iOS Arm64 KLIB compilation passed through production commit 568eb6c, including orderly shutdown, route lifecycle, transport headers, transfer-state recovery and typed native share cancellation; Swift/Xcode linkage remains unverified.
- Browser adapter synthetic Node suite: **21 passed**. These mock DOM/API checks are not a browser runtime pass.
- Full Wasm development executable and webpack bundle passed through production commit 568eb6c after the cold tooling dependencies were populated. The one generated-runtime dynamic dependency warning remains; no browser runtime claim.

The ninth combined build gate passed in 4m19s after correcting one nested layout DSL scope reference. Best-effort previous-destination preview now uses bounded in-memory pixels captured on pointer-down without delaying navigation; quick taps/keyboard paths may fall back safely. Shared-cover interpolation during the gesture remains incomplete. Automatic reading pauses on a back gesture without forcing route pause/reload or restarting on cancellation. No app runtime, native installer, Swift/Xcode linkage, push or release is claimed.

## Route side-effect hardening (2026-10-15 02:38 UTC)

Commits a3ba4a4 and 8cdafe9 make inactive composed destinations read-only previews: initial requests, pagination, notices, native overlays and pickers are gated; reading-position reporting requires the committed route and an actually resumed lifecycle. Restoring a viewport is independent of activating its reporting, so transition activation does not reset its anchor.

Preview image requests preserve cache identity but disallow network reads. The portable image transport rejects Coil's `only-if-cached` miss locally; Android's retained OkHttp path is covered by a no-DNS/no-network regression. Policy-aware painter equality permits an active route to retry a prior preview cache miss.

The focused combined gate passed in 3m1s at 8cdafe9: **354 shared desktop tests and 105 retained Android JVM tests**, zero failures/errors/skips, desktop application compilation and Android debug assembly. These are source/component checks, not native application runtime acceptance. iOS/Wasm results above remain tied to their earlier commits until rerun.

A separate, unmounted Navigation Compose host probe compiled for desktop, Android, iOS device/simulator KLIB and Wasm. Its first scene execution stalled and was interrupted; a diagnostic retry is in progress. It is not integrated or claimed to resolve the shared-cover predictive transition gap.

## Windows input, reflow and ownership pass (2026-10-15 02:59 UTC)

Through 9dec340, verified canonical account names and reading ownership are consistent across restart; old-owner requests are fenced while avatar-only refreshes remain generation-stable. Existing owner records are neither moved nor deleted. Desktop Escape is child-first and tracks consumed presses so orphan releases/repeats do not navigate underneath an overlay. The continuous desktop reader preserves a stable fractional image anchor across width reflow; explicit jumps, interaction and lifecycle changes supersede corrections without sharing the initial-restoration flag.

Verification: **372 shared desktop tests passed**, desktop application compilation and Android debug assembly passed in 2m43s. iOS Arm64 KLIB and the full Wasm executable/webpack build subsequently passed in 2m26s. The latter required the documented nonincremental Wasm-link workaround after a compiler-internal fragment-cache failure; no source assertions or features were disabled. Android's retained JVM suite remains 105 passing at 8cdafe9.

The separate framework-navigation branch now passes its actual shared-cover pixel/geometry test and the native-input bridge test. A rapid push/pop cleanup case failed and is being repaired there. That host has not yet been merged into this validated main branch. Actual application/native runtime acceptance is still deferred.

## Earlier verified checkpoints (2026-10-14 18:20 UTC)

- Final Android `:app:assembleDebug` succeeded after shared-UI integration, removal of duplicate screens, launcher-alias compatibility and consumed export-retry state correction. APK size: 93,359,766 bytes, min SDK31/target37, application ID `com.rchdliang.copymanga_neo`, version29/0.10.14. Manifest inspection confirms the original `.MainActivity` launcher alias targets `.multiplatform.AndroidSharedActivity`.
- Final shared desktop suite: **218 tests passed, zero failures/errors/skips**. This covers domain policies, reading/persistence races, network generations/replay policy, bounded resource streaming, download state, archive/export handling, platform file I/O and UI decision helpers.
- Desktop application Kotlin compilation passed. This is the common JVM application used by Windows and Linux, not a Windows installer/runtime check.
- Full shared iOS Arm64 KLIB compilation passed after external-configuration and native file-coordination changes. Linux KLIB compilation does not prove Xcode/Swift linkage or device operation.
- Full Wasm application source, development executable and browser webpack bundle passed. The development bundle emits the application JS and both application/Skia Wasm binaries. Webpack reports one dynamic-dependency warning from generated runtime code; browser execution is deferred.
- Isolated domain/cache/archive suite: 66 JVM tests passed, including a real ZIP64 archive opened by JDK ZipFile. Actual Wasm/Node execution: 55 algorithm/cache tests passed. These overlap with the full suite and must not be summed as distinct coverage.
- CPU-only offscreen Compose/Skia rendering and visual inspection covered ten phone/wide layouts: About, Settings, Reading Settings, comic/novel detail, novel reader, downloads and history. Fixture data was used.
- Native/browser adapter binding probes and local proxy/file-I/O tests passed. These do not establish OS-level runtime parity.
- Read-only Android compatibility review confirmed original Room v5/data-local implementations and preference stores are retained; the shared UI retains all 98 original shorthand Preview annotations.

The verification used serial Gradle workers. Kotlin 2.4.20 incremental test compilation encountered an internal dirty-symbol error after restructuring; a non-incremental rerun completed successfully. No source assertion was suppressed. The streaming fixture was corrected to exceed Ktor's 1 MiB channel capacity and now checks cancellation and producer termination.

## Runtime checks deferred by request

The user explicitly deferred actual application runtime verification on 2026-10-14. A software-only Android31 emulator booted after approximately 15 minutes, but APK installation failed with a package-service broken pipe. It was shut down without further retries. Desktop window verification was blocked by the current sandbox's local-socket restriction. Neither is reported as an application runtime pass.

## Platform limitations requiring explicit verification

- Windows installers require Windows; iOS framework/Xcode linking and device/simulator validation require macOS. Linux app-image packaging is verified above; native application runtime remains unverified.
- Desktop/iOS/browser generic transports cannot guarantee Wi-Fi-bound transfers. Wi-Fi-only downloads pause with a reason instead of silently using another network.
- iOS/browser download scheduling is foreground-only. Desktop services live for the application lifetime and expose tray actions; native tray behavior still needs OS testing.
- Browser upstream requests remain subject to CORS, forbidden headers and browser proxy policy. There is no security bypass or automatic proxy deployment.
- Browser login credentials are memory-only; file streaming requires supported File System Access APIs. Other capabilities report unsupported states explicitly.
- iOS photo saving requires the declared add-only Photos permission. File-provider/bookmark behavior needs real-device validation.

Feature-by-feature implementation evidence is in UI_FEATURE_PARITY_AUDIT.md, FEATURE_PARITY_AUDIT.md and NOVEL_READER_PARITY.md. Target declarations and successful compilation alone are not claims of full feature parity.

The headless scene test emitted Fontconfig cache-directory warnings in this restricted workspace; its pixel and click assertions passed. This does not establish actual Android/Windows gesture performance or native GPU allocation behavior.
