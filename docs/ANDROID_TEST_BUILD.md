# Android independent test build

Build locally with `./gradlew :app:assembleDebug :app:testDebugUnitTest` using the
SDK/JDK described in [BUILDING.md](BUILDING.md). The output is
`app/build/outputs/apk/debug/app-debug.apk`.

- Debug package: `com.rchdliang.copymanga_neo.migrationtest`
- Launcher label: `CopyManga Neo 测试版`
- Version name suffix: `-migrationtest`
- Release package, label and signing configuration are unchanged.

This debug APK can coexist with the daily original app. Its private database,
settings, login and downloads start separately; it does not migrate the original
app's private data. Saved images in `Pictures/CopyManga Neo`, exported files and user-selected
shared folders are not isolated: choose a separate test export folder to avoid affecting daily files.

Local builds keep normal Android debug signing. The manual Android CI job opts
into the repository's existing release signing configuration with
`-PuseReleaseSigningForTest=true`; the package remains `.migrationtest`. The flag
fails configuration if signing properties are incomplete or the keystore is
missing, and invalid credentials fail signing rather than falling back to a
random runner debug certificate. Ordinary release behavior is unchanged.

The manual workflow uses the same existing `ANDROID_KEYSTORE_BASE64`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`
Secrets as the existing release workflow. No new key or Secret is needed. The
helper reads Secrets only from step-scoped environment variables, uses restrictive
file permissions and Java-properties escaping, and logs no secret values. The
keystore lives in the disposable runner's temporary directory; an `always()` step
removes it and the properties file before artifact uploads. Configuration cache
is disabled for this build; uploads are limited to APKs and test reports. Never
commit, upload as an artifact, print, or distribute signing material. Only dispatch
this signing workflow against trusted, reviewed repository code.

Updating this test app in place requires the same package and signing certificate.
The first transition from an older debug-signed test APK to the CI release-signed
test APK may require uninstalling only the test app, which removes its private
data. Back up anything needed first. Subsequent CI test APKs use the same existing
certificate and can update in place while that signing key is unchanged and
Android version/update requirements are satisfied. Switching back to a locally
debug-signed build can again require a test-app reinstall. Do not uninstall the
daily original app to install this test build.

## Verification

Before distributing an APK, inspect the actual artifact using SDK `aapt2 dump
badging app-debug.apk` and `aapt2 dump xmltree app-debug.apk --file
AndroidManifest.xml`. Check the package, label, version suffix and provider
`com.rchdliang.copymanga_neo.migrationtest.reader.images`. Check that the launcher
alias resolves to the existing `AndroidSharedActivity` class and that download /
export services and notification receiver remain non-exported. Verify its signing
certificate with SDK `apksigner verify --print-certs app-debug.apk`.

For CI artifacts, compare the signer SHA-256 fingerprint to a trusted existing
release APK or a previously verified CI test APK. Do not infer stable signing
from the filename alone. Real-secret restoration and an actual CI-signed APK have
not been exercised locally; local validation uses synthetic non-key bytes only.
Run `python3 -m unittest discover -s tests/ci -p 'test_*.py'` for the secret-free
preparation and workflow regression checks. On 2026-10-15, all five checks and
`actionlint` passed; Gradle with the signing flag and absent release properties
failed with the intended configuration error. The default local
`assembleDebug testDebugUnitTest` build still passed after this change (existing
compile/test outputs were up to date). No CI dispatch or real-key signing was
performed for these checks.

On a device, keep the original installed, install the test APK and check:

1. Both launcher entries exist; the test entry opens the test app.
2. Login/settings and downloaded items are separate from the daily app.
3. Reader image sharing works (variant-specific FileProvider authority).
4. Download notification open/pause/resume/cancel/dismiss affects only the test
   app; export cancel/retry returns to the test app.
5. Export into a separate test folder and open/share the resulting file.
6. Opening the daily app still shows its original data.

Internal notification/service intents already specify their component using the
current Context and class. Their action strings can remain unchanged: Android
scopes explicit components and PendingIntent creator identities to the installed
package. Notification channels/IDs are likewise app-scoped. The manifest provider
uses `${applicationId}`, and reader sharing uses `app.packageName`.

`X-Requested-With` remains `com.rchdliang.copymanga_neo` for both installation
variants, preserving the pre-suffix Android behavior and matching the portable
client. This is a compatibility precaution, not a claim that the server requires
that identifier. Unit tests cover this header and the debug BuildConfig identity.
Successful compilation/static metadata inspection does not replace the device
checks above.

## Local verification (2026-10-15)

`assembleDebug` and `testDebugUnitTest` succeeded with SDK platform 37.0 revision 2
and Build-Tools 37.0.0: 107 tests, zero failures/errors/skips. The actual APK's
binary manifest and resources confirm the package, version suffix, test label,
reader authority and explicit launcher alias target described above. The merged
Compose resources and AndroidX Startup provider authorities also use the test
package. `apksigner verify --print-certs` passed with an Android Debug certificate.
These are build/artifact checks only; coexistence and service behavior on a real
Android device remain to be checked.

## Small-window login regression check

Use dummy credentials; no real login is needed to check the layout. In a floating
window or split screen, focus the account field, enter text, then move to the
password field with the IME open. Shrink the window again while password remains
focused. Type and delete several characters, toggle password visibility, and
scroll down to the remember/auto-login options. Check that the focused field stays
visible, typing does not jerk the form, and the fixed Cancel/Login actions remain
reachable. Also check a normal-height window and close/reopen the dialog.

`LoginDialogScrollTest` runs the shared dialog offscreen at 400, 300 and 250 pixels
of height, checks per-frame typing/deletion stability and focus, reduces an already
focused dialog's height constraint, and checks IME submission and edited-password retry callbacks.
The original fixed header left only 41 pixels for a 64-pixel field at the smallest
size. The header now scrolls with the form; full-field relocation runs on focus or
viewport/field size changes, not on each keystroke. These host tests do not emulate
Android's actual IME animation or establish that a device-specific jitter is fixed.
