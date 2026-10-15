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

Normal Android debug signing is used. Do not commit or distribute a keystore.
Updating this test app in place requires the same package and signing certificate;
a build from another machine or fresh CI runner may require uninstalling only the
test app first, which removes its private data. Back up anything needed first.
Do not uninstall the daily original app to install this test build.

## Verification

Before distributing an APK, inspect the actual artifact using SDK `aapt2 dump
badging app-debug.apk` and `aapt2 dump xmltree app-debug.apk --file
AndroidManifest.xml`. Check the package, label, version suffix and provider
`com.rchdliang.copymanga_neo.migrationtest.reader.images`. Check that the launcher
alias resolves to the existing `AndroidSharedActivity` class and that download /
export services and notification receiver remain non-exported. Verify its debug
certificate with SDK `apksigner verify --print-certs app-debug.apk`.

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
