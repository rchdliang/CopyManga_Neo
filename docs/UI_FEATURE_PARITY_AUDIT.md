# Source-to-shared feature coverage audit

Baseline: `rchdliang/CopyManga_Neo@466cef7c6acd2f082a9f7432cc06a082d83bc1c9`.
Initial audit: 2026-10-14. Source review updated 2026-10-15 through main `28ddcd6`.
This is **not** a device/browser parity sign-off. Later integrated verification is tracked in `MIGRATION_STATUS.md`.

Paths below are relative to the original `app/src/main/java/com/rchdliang/copymanga_neo/` and migrated `shared/src/.../kotlin/com/rchdliang/copymanga_neo/` trees.

## Coverage checked

All 92 original UI source files have shared counterparts. `ui/util/CompactLocale.kt` moved to `multiplatform/ui/components/CompactLocale.kt`; no original UI file is omitted. The shared UI tree has additional capability/DI adapters. File presence alone does not prove behavior.

Inspected original bodies/state against shared implementations and `multiplatform/app/CopyMangaApp.kt` wiring:

- Home: featured/recommended/newest/finished/ranking/topic cards, persisted ranking audience/period, pagination and refresh, blacklist filtering/warnings, separate novel preview and session invalidation.
- Search/discovery: keyword/author/theme search, query types, tags and counts, layout preference, filter and rank controls, topic detail, next-page behavior, sharing targets. Home rank query parameters are preserved by `exploreArguments`.
- Detail: authors/themes navigation, chapter groups, selection/download changes, favorite/read-progress state, offline downloaded chapter fallback, comments and sharing.
- Comments: pages/replies/reports/posting permissions, uncertain-post warning for transport failure, copied text, local downloaded comments, relative/local date display.
- Favorites/history/bookmarks: comic and novel shelves, update badges, owner isolation, cloud history confirmation, local precise positions, bookmarks, history removal/clear, navigation back into comic/novel readers.
- Download/export: original queue states and actions, chapter/comment persistence, novel ownership/mode checks, restart manual resume, risk pause, archive/image format selection, export progress/retry/drafts.
- Profile/settings: login/import/export credentials, editing/avatar, blacklist management, app/reader settings, storage, backup import/export, network settings/diagnostics/log screen, about screen.

The primary screen bodies were retained, with constructor-injected real repositories and explicit platform services. Android implementations remain the compatibility reference and adapter layer. Network-dependent behavior still requires authenticated integration tests.

## Audit findings corrected in the implementation

1. **Durable tag catalogs restored.** Original `data/local/ComicThemeCatalogStore.kt`, `di/DatabaseModule.kt:44` / `PersistentComicThemeCache`, and `data/novel/NovelThemeCatalogStore.kt` retain discovery counts, per-comic blacklist tags/refresh deadlines, and per-mode novel tags across restarts. The portable graph now initializes one `data/repository/portable/PersistentThemeCatalogs.kt` and injects it into manga, discovery and novel repositories. A failed disk read preserves the original and surfaces a startup warning; successful live results remain usable in memory if a cache write fails. Three desktop tests passed for restart, mode isolation, refresh-deadline retention and failed/corrupt storage.

2. **Storage accounting and clear operations corrected.** Original `data/repository/StorageRepositoryImpl.kt` is the reference. `PortableAppGraph` now reports the actual Coil disk-cache size, clears every retained loader's memory cache plus its disk cache, and awaits `downloads.cancelAllNovels()` before `novels.clearCache()`. The common graph test passed. A device-level concurrent download/cache-clear exercise is still required.

3. **iOS existing picker-file mismatch corrected.** The picker creates an empty destination, so an unconditional `O_CREAT|O_EXCL` failed every single-file export. `IosPlatformHost` now reserves the empty file identity; `FileComicExportIo` stages the archive, validates inode/device/empty-file ownership in `NSFileCoordinator`, and writes without arbitrarily truncating a different file. A bounded persistent ownership ledger prevents cleanup from deleting a replaced output. Security-scoped bookmarks restore folder grants. Unknown/replaced targets produce an explicit cleanup warning; revoked grants or a single-file retry without a live reservation require re-selection. Native source compilation passed; Files/iCloud/provider runtime behavior remains unverified.

4. **Scoped image transport wired throughout UI.** `PreviewAwareAsyncImage`, `BookDetailHeader`, `TopicCoverImage` and novel images now use the provided application image loader; reader images already had an explicit loader. This avoids the unconfigured singleton bypass for authenticated/risk/proxy image requests. The graph keeps timeout-keyed loaders stable, clears memory on session changes, and retires old HTTP clients after registered requests and download I/O actually finish. Authenticated live-image/proxy runtime checks remain necessary.

5. **Picker and reading lifetimes corrected.** Export picking now belongs to retained `ComicExportViewModel`, with saved pending state and Android host result buffering for process recreation. `PortableReadingProgress` waits for initialization, survives individual sync failures, fences old account-generation responses, seeds clear tombstones, and publishes reload readiness after the read finishes. `PortableLocalReading` serializes loading/mutations, advances imported timestamps and preserves completion intent after failed deletion. All 11 targeted reading lifecycle tests passed.

6. **Browser network-log export implemented.** `BrowserPlatformHost.exportNetworkLog` now uses `NetworkLogRepository.exportChunks`, awaits writes of at most 64 KiB, closes only on success and aborts on failure/cancellation. The previous unsupported synchronous-stream branch has been removed. Actual embedded JavaScript passed the Node bridge fixture for stream close/abort/cancellation and durable handle reload. Full shared Wasm source compilation passed. A real browser/user-gesture/filesystem test has not been performed.

7. **Desktop tray notifications implemented.** `desktopMain/.../multiplatform/platform/DesktopTransferNotifications.kt` installs a real `SystemTray` icon when supported, updates live progress, emits deduplicated start/completion/error messages, opens Downloads on click, and exposes pause/resume/cancel only through user-click menu actions. The parent app removes it on close. Unsupported/headless environments report a capability limitation. Four pure notification-projection tests passed; Windows tray runtime remains unverified.

## Subsequent source-level parity corrections (2026-10-15)

- **Desktop selectable BMP avatars:** the WebP codec probe no longer calls the stricter comic-export format detector, which rejected BMP before ImageIO could decode it. A real 2400×1200 BMP now produces an opaque 1024×512 JPEG; malformed input retains an avatar-specific error. Existing bounded WebP decoding remains covered (`6a15373`).
- **Desktop save names:** bare renamed destinations inherit the suggested file suffix for images, archives, settings and logs. Explicit suffixes/dotfiles remain unchanged. Resolution precedes overwrite confirmation and archive reservation; an existing resolved target still requires approval and retains replacement-change protection (`b67a5b5`). Android supplies the suggested name/MIME to its document provider; this comparison does not claim that every Android provider normalizes names identically.
- **Desktop canceled document writes:** settings/log staging now checks coroutine cancellation immediately before publication. A deterministic export regression first reproduced a canceled write creating its destination; after the fix, cancellation preserves existing bytes or leaves a new destination absent and removes staging. Failure and successful replacement paths are also covered (`6040544`).
- These three suites passed **11 focused desktop tests**, with no failures/errors/skips. They use actual image and filesystem operations on the Linux host, without native Windows dialog/clipboard/tray execution. This bounded review found no additional confirmed defect in adjacent picker cancellation, reader save/share outcome state, or bounded document reads; it is not exhaustive parity acceptance.
- **Desktop WebP avatar selection:** the explicit `image/webp` MIME type now maps to the `webp` file extension. The avatar picker already requested this type and its encoder already supported it, but the default chooser filter hid those files unless the user selected All Files. Windows-selected source compilation and portable packaging passed after the correction at `86fb7b7`; actual native picker interaction remains unverified.
- **Comments:** full-window comments dialogs own their feedback host, so report confirmation/errors are no longer drawn behind the dialog. Feedback has polite accessibility live-region semantics. Reader modal and chapter-inline comments have distinct model keys, preventing modal open/close from resetting the inline draft or cancelling its work; chapter count propagation remains wired.
- **Reader interaction:** desktop keyboard policies distinguish paged navigation from viewport/small scrolling for long continuous images, respect RTL, and avoid stealing text-selection/modal input. Novel image geometry caching and immediate preference-slider feedback were corrected. These are implementation/policy checks, not Windows input/rendering validation.
- **Back/lifecycle ownership:** the outer host now uses one authoritative NavController with opaque route IDs and retained payload/model/picker identity. Native inputs still enter the pinned shared dispatcher; the standalone framework bridge preserves nested handler priority. Real previous-route composition and shared-cover seeking replace the inert snapshot transform. Committed-active lifecycle/effect and cache-only image gates prevent preview replay; cancellation preserves route and reader-local state, while legitimate framework pause/flush is allowed. Six focused framework/component tests passed, including geometry, saved-state restoration and rapid-pop cleanup. See `PREDICTIVE_BACK.md`; actual Android/Windows application acceptance remains deferred.

- **Browser action outcomes:** prepared save/share/link/clipboard/fullscreen actions use an explicit fresh-click gate, with retry and disposal cleanup. Notification settings allow explicit permission retry and explain already granted/blocked permissions. Cancelled image SAVE resets the retained model to idle; cancelled credential copy no longer reports successful copying; cancelled clipboard reading remains null. Synthetic adapter cases cover these paths. They do not establish real-browser activation behavior.
- **iOS back input:** the Swift host connects leading-edge gestures and hardware Escape to the shared dispatcher, respects RTL, excludes native modal presentation, and cancels on lifecycle interruption. Native compilation/device gesture arbitration and keyboard responder behavior still need verification for this new bridge.

The fresh detail/novel/reader comparison found no additional independently confirmed missing chapter, bookmark, favorite, progress or theme action. That is a bounded source-review result, not proof that all user paths are correct.

## Explicit remaining platform differences

- **Wi-Fi-only enforcement outside Android:** original `data/download/DownloadNetworkPolicy.kt:53-65` binds sockets and DNS to the selected Wi-Fi network. Standard desktop, Darwin and browser engines do not yet provide equivalent verified binding. Their factories reject Wi-Fi-only transfers and expose a waiting reason; the preference is never silently ignored.
- **Background transfer lifetime:** original `data/download/DownloadService.kt:58-105` runs a foreground service, wake lock and progress/action notifications. Desktop transfers last for the app scope; iOS/browser transfers require foreground/visible state. iOS/browser background schedulers and equivalent notification actions are not implemented. Android retains its original service adapter.
- **Browser capabilities and credentials:** file save/export requires File System Access and a valid retained grant; sharing/clipboard require the corresponding Web APIs, secure context and applicable user activation. Unsupported capabilities fail visibly. Browser networking supports SYSTEM proxy only and remains subject to CORS; transport uses omitted ambient credentials/manual redirects and explicit app tokens. Browser login tokens/passwords remain session-memory-only because a secure persistent credential store is unavailable, so durable auto-login is not equivalent to native secure storage.
- **Provider permissions and cleanup:** iOS/browser grants may be revoked or unavailable after restart. Those operations require re-selection or report manual cleanup rather than pretending to have saved/deleted data. iOS single-file retries without live reservation also require re-selection.
- **Runtime and distribution verification:** Windows tray/dialog/package integration needs Windows; iOS needs macOS/Xcode and simulator/device validation; browser CORS, user activation, filesystem permission and persistence behavior need a real browser. The user deferred actual browser execution. Source compilation, Node fixtures and offscreen rendering do not establish these runtime outcomes.

## Practical acceptance checks still required

### Concrete missing implementation

- Non-Android Wi-Fi binding and iOS/browser background transfer equivalents remain absent as described above; unsupported capability feedback is not equivalent functionality.

### Deferred Android and Windows runtime verification

- **Android navigation and state:** open home/search/tag/author/detail/reader paths, return repeatedly, switch tabs during animations, and verify only the visible route handles back. Rotate and change theme with detail chapter selection, reader position, inline draft and native save picker pending; confirm the intended state survives and no duplicate picker opens.
- **Android predictive back:** cancel and commit from detail, comic reader and novel reader; test nested comments/selection/controls first, rapid repeated gestures, activity interruption and root back-to-home. Cancellation must preserve exact position/draft; previous-route previews must not replay requests, writes or events. A legitimate current-reader lifecycle flush is allowed. Compare real artwork/shared-cover geometry and transition timing against the original.
- **Android reader/account actions:** save/share a page, cancel save, add/remove bookmarks, reopen precise history positions, post/report comments, and exercise login/session changes. Verify dialog feedback is visible and accessible, and no successful-copy/save message appears after cancellation.
- **Windows reader and shell:** check RTL page keys, long-image viewport scrolling, novel selection/copy/search, text-field focus and modal Escape; resize between rail/bar layouts and toggle theme. Verify native save/share capability feedback, tray click/actions and close/quit behavior with active transfers and pending reading persistence. Use real Windows filesystem/dialog/tray behavior, not Linux/offscreen evidence, for acceptance.
- **Both primary targets:** verify real authenticated images, offline chapters, download pause/resume/retry, export location/error recovery, bookmarks/history and reading-position persistence across restart. Inspect output files and replay saved positions rather than relying on a completion toast.

## Historical verification evidence (initial audit checkpoint)

The entries below retain the earlier evidence and counts; they do not imply that later changes were covered by those runs.

- `toolchain/native-wasm-build.log`: `:shared:compileKotlinIosArm64` and `:shared:compileKotlinWasmJs` passed, 3m 3s. This creates/compiles shared artifacts; it is not a linked Xcode application/device run. Final iOS source compilation also passed after the platform/configuration changes; Wasm executable and webpack bundle passed (see MIGRATION_STATUS.md).
- Desktop pass 4: reading lifecycle 11/11, download queue lifecycle 9/9, download persistence/leases 6/6, queue codec/store 5/5, bounded image transport 5/5, durable theme catalogs 3/3, common graph 1/1, tray projection 4/4, and owned UI helper tests passed.
- Foreground download-services suite: two tests passed; the foreground probe test initially checked before Ktor's default dispatcher ran. Its MockEngine now uses the test scheduler. The final 218-test desktop suite passed with this fixture fix; no production connectivity shortcut was added.
- Portable storage/export worker reports 66 focused desktop tests and 55 actual Node/Wasm tests passing. Browser bridge fixture covers real embedded JS syntax, stream close/abort/cancel, bounded reads, persisted handle reload, and explicit unsupported clipboard/share failures.
- Remaining integrated test/build results are recorded in `MIGRATION_STATUS.md`. Do not mark the full application release-ready based on these partial checkpoints.
