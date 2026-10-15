# iOS and browser launchers

Both launchers build the same `PortableAppGraph` and render `CopyMangaApp`. Their platform adapters supply document storage, downloads, network engines, credentials, image saving, comic export, and system controls.

## iOS

On an Apple Silicon Mac with Xcode, JDK 21, and the Android SDK required by the root Gradle project:

1. Open `iosApp/iosApp.xcodeproj`.
2. Select the **CopyMangaNeo** scheme and an arm64 simulator or connected iPhone/iPad.
3. For a physical device, select your own signing team in Xcode. No developer account or signing credentials are embedded.
4. Build and run. The first build invokes `:shared:embedAndSignAppleFrameworkForXcode`.

The launcher targets iOS 16 or later. `ReaderHostingController` handles UIKit status-bar, home-indicator, and orientation callbacks; Kotlin manages scoped reader-window leases. `NSPhotoLibraryAddUsageDescription` covers explicit original-image saves to Photos.

Local data is under the application's Application Support directory. Credentials use Keychain. File-picker grants use security-scoped bookmarks. A single-file comic export writes only a picker-created empty file whose identity still matches its reservation; directory exports choose non-overwriting names. Interrupted single-file export retries require choosing a fresh destination. Downloads pause in the background and resume only in a live foreground app; no unsupported background-service guarantee is made.

Kotlin iOS KLIB compilation on Linux does not validate Swift compilation, final framework linking, simulator behavior, Photos permissions, iCloud providers, or device operation. Those require Xcode/device testing.

### Optional official-app protocol configuration

The iOS entry now resolves `COPYMANGA_TAP_STRING` in this order:

1. For **Debug** builds only, the launch process environment (for example, an unshared local Xcode run scheme).
2. `CopyMangaTapString` in the built app's Info.plist, expanded from the Xcode `COPYMANGA_TAP_STRING` build setting.
3. Empty when neither is configured. An unresolved build-setting placeholder is also treated as empty.

For a local build, copy `iosApp/LocalSecrets.xcconfig.example` to `iosApp/LocalSecrets.xcconfig` and provide only a value you are authorized to use. The tracked `Config.xcconfig` optionally includes that local file; both target configurations use it. The local secrets file and Xcode user data are ignored by Git. Do not place real values in the tracked project, examples, shared run scheme, or documentation, and do not commit the ignored file forcibly. A user-defined build setting supplied outside tracked project files is also supported.

The Debug environment override avoids storing this value in the app bundle. If you use the plist/build-setting route, the resulting value is readable from the distributed app's Info.plist: this is configuration plumbing, **not encrypted secret storage**. Never use it for personal passwords, account tokens, or unrelated private API keys. No real value is included in this repository.

## Browser

From the project root:

```sh
./gradlew :shared:wasmJsBrowserDevelopmentRun
```

The browser entry uses `shared/src/wasmJsMain/resources/index.html` and `copymanga-neo.js`. Serve a production bundle over HTTPS; localhost is sufficient for development.

### Optional runtime configuration

Before `copymanga-neo.js` loads, the hosting page may set `globalThis.copyMangaNeoConfig.tapString`. Missing or non-string values default to empty. For example, a host-owned script may contain this empty template:

```javascript
globalThis.copyMangaNeoConfig = { tapString: "" };
```

For local development, an optional `shared/src/wasmJsMain/resources/copy-manga-config.local.js` is ignored by Git. If you create it, load it explicitly before the application script in your local hosting page; the default page has no mandatory configuration-file request. Server-injected configuration before the application script is also supported. Do not put real values in the tracked HTML or examples.

**All browser bundle and runtime configuration is readable by visitors and page scripts.** An ignored local file becomes public if you include it in a served build. Never inject personal login credentials, account tokens, or private server secrets. Providing a tap string does not bypass CORS, forbidden headers, or browser proxy restrictions.

Settings are stored in browser local storage and binary downloads/novel caches in IndexedDB. Login credentials and tokens are memory-only. Clearing site data removes browser-local records and caches. Browser storage quota errors are surfaced rather than treated as saved data.

Comic ZIP/CBZ and original-image exports use real File System Access writable streams and durable selected handles. A browser without those pickers cannot perform those exports; there is no unacknowledged anchor-download success fallback. After a reload, a browser may require selecting a destination again to renew permission. A failed cleanup is reported for manual removal.

Escape and browser Back use the same in-app back dispatcher. At the application root, browser Back flushes pending reading records before leaving to the previous page. Failed flushes keep the application open with a visible retry prompt.

Network requests remain subject to the upstream site's CORS policy, forbidden request headers, and browser-managed proxy settings. App-mode protocol traffic can therefore be unavailable in a browser even when the same account and operation work natively. No proxy bypass is included. Downloads pause while the page is hidden; Wi-Fi-only mode cannot claim network binding unavailable to the browser.

## Verification boundaries

The shared domain, novel-cache validation, and comic archive logic have 66 passing desktop regression tests; 55 common tests also pass in actual Wasm execution through Node. The iOS and Wasm exporter source has also been compiled in isolated target probes. This is not a substitute for a complete browser or iOS end-to-end run. Consult the current build report for whole-application compilation and runtime results.
