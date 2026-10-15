# CopyManga Neo: feature fidelity and platform-boundary audit

> Historical baseline audit, written before implementation. References to missing adapters or unverified compilation below describe that starting point. See MIGRATION_STATUS.md and UI_FEATURE_PARITY_AUDIT.md for current implementation and verification.

Baseline: `466cef7c6acd2f082a9f7432cc06a082d83bc1c9` (2026-10-14 source; application version 0.10.14, code 29).

This is a source audit, not a build or device-test report. No application source was edited. The acquisition worker verified the downloaded source against the commit's Git blobs. Three oversized README screenshots were not downloaded; no conclusions here rely on those screenshots. No `AGENTS.md` exists in the acquired tree. Line references below are relative to `app/src/main/java/com/rchdliang/copymanga_neo/` unless specified otherwise.

## Decision in brief

Preserve the current Android application as the working product while extracting actual behavior. Build common domain/state/UI seams around its real screens; retain Android adapters until equivalent platform implementations and regression tests exist. Windows should first reuse a JVM data layer where useful, rather than force every transport, crypto, file operation and download service into `commonMain` in one pass. iOS, Linux and Wasm must follow the same domain contracts, with honest capabilities where OS behavior differs.

The novel reader is a particularly good extraction target because its new text layout is already Compose-based and its core uses stable source coordinates. It is also a particularly bad candidate for a simplified replacement: selection, annotation, pagination, illustrations, source-coordinate restoration and local source selection are intertwined. See `NOVEL_READER_PARITY.md` for the dedicated audit.

A launcher showing four generic tabs with sample cards would not be this application's migration. Essential parity includes reading/resume behavior, account/network isolation, local downloads, error states and the existing UI's interaction details.

## Scope and structural inventory

The main Kotlin tree contains 290 files and 47,461 lines:

| Area | Files | Lines | Role |
|---|---:|---:|---|
| `domain` | 79 | 2,794 | Models, repository contracts, reading policies, home filter |
| `ui` | 92 | 28,417 | Screens, state holders, reusable components, both readers |
| `data` | 102 | 14,048 | API/DTO, storage, caches, repositories, downloads, exports |
| `di` | 14 | 1,044 | Hilt assembly, network policy and image-loader providers |
| Application/activity/main VM | 3 | 1,158 | Android bootstrap, navigation, theme, session lifecycle |

The syntactic inventory in `source-dependency-inventory.json` records each source file, lines, direct Android imports, JVM/library imports, and additional portability-review tokens. These flags are triage, not proof of compilability: fully qualified references and API availability still require compiler checks. In particular, Compose packages named `androidx.*` are not automatically Android-only, and absence of an `android.*` import does not make a file common-ready.

The project is currently one Android application module. `app/build.gradle.kts` uses `com.android.application`, Hilt and KSP, compile/target SDK 37, min SDK 31, Java 21, Kotlin Compose compiler, Android Compose BOM and AndroidX Material 3. There is no existing KMP module, desktop entry point, iOS framework, or Wasm target in the baseline.

### README must not define scope

README feature descriptions and September screenshots lag the source. The README says comments and single-image saving are not offered. The commit has comic/chapter/novel comments, reporting, chapter comment snapshots, reader image save/save-as/share, local reading/bookmarks, novel browsing and reading, ZIP/CBZ/image-folder export, account registration/profile editing, network configuration and diagnostics. Migrating only the README feature set would remove real functionality.

## Route and navigation inventory

`MainActivity.kt:227–296` defines the actual outer routes. Several enum-like `Screen` objects are not standalone NavHost destinations; the top-level tabs live inside `main`.

| Route / entry | Inputs | Actual destination and navigation contract |
|---|---|---|
| `main` | Saved tab | `HOME`, `DOWNLOADS`, `BOOKSHELF`, `PROFILE`; back from a non-home tab returns to Home |
| `novel?path={path}&resume={resume}` | Optional path and serialized `LocalReadingRecord` | Novel browse/detail/reader hierarchy; covers bottom navigation and rail |
| `detail/{pathWord}` | Comic identifier | Full-screen comic detail; chapter to reader; tag to search; author to author search; login-needed action can return to Profile |
| `reader/{comicPathWord}/{chapterPathWord}/{chapterName}/{groupPathWord}?page={page}&fraction={fraction}` | Encoded identifiers; optional page/fraction | Full-screen comic reader with explicit resume target; fraction is finite and clamped to 0..1 |
| `search?themePathWord={themePathWord}&themeName={themeName}&query={query}&queryType={queryType}` | Optional initial theme or author query | Full-screen search/discovery, with nested all-tags UI |
| `explore/{section}?theme={theme}&top={top}&audience={audience}&period={period}&topic={topic}&topicTitle={topicTitle}` | Section and optional filters/topic | Recommended, ranks, newest, finished, topics, filter; topic drill-in/back semantics |

`ProfileScreen.kt:101–234` has an additional saved internal hierarchy:

- Main profile → Local library → Cloud history (login-dependent)
- Main profile → Settings → Reading defaults / Downloads & notifications (inside Settings)
- Settings → Data management → Diagnostic logs
- Settings → About
- Main profile → Network settings
- Main profile → Tag blacklist
- Login, registration and profile edit are dialogs, not separate outer routes

`NovelScreen` likewise has internal browsing/detail/reader state. Do not infer route coverage from `NavHost` alone.

### Shell fidelity requirements

Evidence: `MainActivity.kt:299–616,619–964`; shared transition helpers in `ui/components/ComicSharedTransition.kt` and `NovelSharedTransition.kt`.

- Width threshold is 840dp. Narrow windows use the bottom navigation bar; wide windows use a collapsible `WideNavigationRail` with avatar/profile entry and menu state description.
- Main destinations use a `SaveableStateHolder`, not destructive screen replacement. Tab changes animate directly in ordinal direction for 300ms; there is deliberately no intermediate pager-page traversal.
- Outer route fades are 150ms enter, 100ms exit, immediate pop exit. Detail cover and container use shared transitions, with cached source data held while detail loads.
- Book covers, background shape, selected/unselected icons, bookshelf update badge (99+ cap), rounded wide content surface, safe drawing/status/navigation insets and accessible state descriptions are part of the app's current presentation.
- Android notification intents can open Downloads or retry a specific export. The host must consume each intent once across lifecycle recreation.
- Fresh-activity and resumed lifecycle events feed the global risk gate; app settings must load before choosing theme; export messages are lifecycle-aware.

## Feature and state inventory

### Home, discovery and search

Evidence: `ui/home/HomeScreen.kt:159–430`, `HomeViewModel.kt:39–75,219–408`; `ui/explore/ComicExploreViewModel.kt:22–53`; `ui/search/SearchViewModel.kt:27–52,212–362`; `SearchScreen.kt:189–270`.

- Home: search entry, recommendations, official light novels, hot updates, newest, rank lists, topics, finished comics. Separate state exists for comic and novel home requests.
- Home rank preferences include audience (`male`/`female`) and period (`day`/`week`/`month`/`total`). Rank positions are preserved even when filtering removes entries.
- Home shelves page independently, react to viewport/card count, and keep fixed sections while blacklist filtering becomes ready so late content does not shift the user's scroll anchor.
- Home tag blacklist is enabled independently of login. Missing theme metadata and filter errors have separate warnings. Scope remains Home; do not apply the filter silently to shelves or explicit search.
- Risk-control UI blocks networking, explains the pause, permits retry and provides entry to locally downloaded content. Retained results, first-load errors, incremental paging errors and empty results are distinct states.
- Explore supports recommended/ranks/newest/finished/topics/filter, topic covers/detail lists, ordering, audience/period and theme/top filters, refresh and load-more.
- Search retains draft query separately from submitted query/type; supports broad/name/author/translation-group types as exposed by the model, discovery theme/top filters when not doing keyword search, total/nextOffset paging and compact/list layout toggling. A detail-supplied theme is promoted once; selecting filters locally must not reorder tags or jump horizontal scroll.
- Search uses the real Material SearchBar field, IME submit, clear, embedded search action, keyboard dismissal, all-tags entry and preserved filter state. Do not replace it with a static text field plus fake results.

Shared candidates: states and filtering/paging logic; `ComicCard`, `ComicGridLayout`, topic/card layouts, `GridLoadMoreEffect`, stateless `SearchPage` and most content composables. Required adapters: repository calls, image loading, current Hilt wiring, saved route inputs, logging and compact-number formatting.

### Comic detail and catalog

Evidence: `ui/detail/DetailViewModel.kt:44–87,241–342,560–699,699–905`; `DetailScreen.kt:119–214,500–709`; `ui/components/BookDetailLayout.kt:130–196`.

- Metadata includes cover/title/aliases, authors, status, update, popularity, expandable description and tag chips.
- Main action is Start/Continue with a resolved chapter name, based on local and remote reading status. It must not regress during late login/progress load.
- Shelf membership has separate loading and login state. Comic comments respect content restrictions.
- Author opens search; tag click opens filtered search; tag long-press offers blacklist confirmation and the chip reflects blocked state.
- Catalog groups represent translation/source groups; complete catalog readiness is distinct from loading a page. Sorting does not change stable chapter identities.
- Chapter cells show read-complete, download state and progress. Long press enters management; selection is preserved across visited groups. Select all, invert, to first/latest, between anchors and corresponding unselect operations must survive migration.
- Management computes downloads, deletes and cancels from the desired selected set versus actual status, rather than interpreting every selection as “download.”
- Shared detail layout has a compact app bar and a single lazy grid. Header-title visibility and expanding/collapsing reading action respond to scroll with an 8dp accumulated-direction threshold.
- Detail can fall back to local cache and local catalog behavior when the network is unavailable or blocked. Loading/error/risk-control states and pull-to-refresh are part of parity.

### Comic reader

Evidence: `ui/reader/ReaderScreen.kt:104–290,293–860`, `ReaderViewModel.kt:64–102,165–382,415–463,552–625`, `ReaderGestures.kt`, `ReaderZoomGestures.kt`, `ReaderContent.kt`, `domain/model/ReaderTools.kt`.

Reading modes and image interaction:

- Right-to-left and left-to-right horizontal pager; top-to-bottom continuous lazy list.
- Fit-screen, fit-height, fit-width; per-comic preferences and global defaults.
- Focal-point preserving pinch zoom, drag and inertial panning. Double tap zooms to 2.5x or resets; paged maximum defaults to 5x, continuous to 3x. A touch during fling brakes without triggering page/menu action.
- Left/right 15% tap zones take precedence over bottom menu zone; central bottom 40% toggles controls; center toggles menu; long press targets the image under the transformed pointer.
- Image loading, failure/retry and retry count/timeout; preview/full images and local files; prefetch 0–3 pages. Long images should not require full decode merely to export their original bytes.

Reader navigation and state:

- Stable key format is chapter plus image index, with chapter comment items in the same session. Current anchor includes chapter, page and fraction.
- Direction changes, append-next chapter and removal of comment items preserve the active image anchor. Restoring a fractional long-image position waits until the actual image geometry is ready.
- Completion requires a settled position and the last image bottom visible; placeholder size or merely swiping across the final page cannot mark completion.
- Back first hides controls when appropriate, then respects nested dialogs. Controls auto-hide after five seconds except during slider interaction.
- Progress slider previews during drag and jumps once on release, with per-page haptic ticks. Catalog, previous/next chapter, bookmark list/jump, add/remove bookmark and current chapter/position footer are implemented.
- Continuous chapters can append/preload the next chapter without losing the current anchor; catalog has downloaded-only behavior for local reads.
- Auto-reading is explicit opt-in each time, pauses on touch, dialogs, slider, comment pages and lifecycle pause/stop. Continuous auto-scroll advances 75% viewport over 900ms; screen-awake behavior is tied to running auto-read. Saved tools must not cause automatic restart.
- Volume key page control is optional, with repeat suppression. Desktop keyboard support should add appropriate keys without deleting touch interaction.
- Status-bar hiding, orientation options (system/auto/portrait/landscape) and keep-awake are Android host effects, not reasons to rewrite reader UI.

Integrated tools:

- Inline chapter comments plus comment dialog with shared count state and closure handling; local comment snapshots can be downloaded.
- Original image save-to-gallery, save-as and share; current-chapter export when complete local data is available; chapter URL sharing.
- Tools settings: inline comments, continuous chapters, volume keys, auto interval 2–30s, retries 0–3, timeout 5–60s, preload 0–3.

Reusable first: `ReaderPosition`, tap/zoom math and Compose pointer logic, mode/scale enums after separating orientation constants, settings/control layouts and source-key mapping. Separate `ReaderScreen` into a platform-free content host and explicit window/image/file/input adapters. The current `ReaderSuccessContent` still directly obtains `LocalContext`, `LocalView`, Hilt comment models/image loaders and native Android key repeat state, so it is not common-ready unchanged.

### Bookshelf, history and local library

Evidence: `ui/favorites/FavoritesViewModel.kt:27–49,130–203,256–379`; `ui/history/HistoryViewModel.kt:21–33,77–198`; `ui/library/LocalLibraryViewModel.kt`; `domain/model/LocalReading.kt`.

- Bookshelf switches comic/light novel; login gating and session readiness are explicit. Comics show last-read/update state; novel shelf has independent load/append errors and more-results state.
- Bulk selection/removal, refresh, selection cancel/back and update badge are real actions. Removal is account/session-aware and must not operate on stale selection after a mode change.
- Local library is usable for local reading history and bookmarks and exposes cloud history separately. Resume routes carry the complete saved position, not just a book path.
- Cloud history checks login, paginates, offers explicit clear confirmation and refreshes bookshelf status after clear.
- Local reading records store owner, kind, path, chapter, group, title, page/fraction, section/line/character, completion, time, cover and authors. Local history and bookmarks are distinct collections, with deletion/owner/book tombstones.

### Comments and account actions

Evidence: `ui/comments/CommentsViewModel.kt:33–67,130–208,261–344,454–606`; `CommentsDialog.kt:264–320,848–861`; `ui/profile/ProfileViewModel.kt`; `domain/repository/UserRepository.kt`; `di/NetworkModule.kt:34–49,137–214`.

- Comment targets include whole comic, chapter and novel (domain policy); feeds have total/nextOffset, refresh and incremental errors, downloadable snapshots and closed state.
- Reply previews, nested replies/back, reply-to target, text draft, login/permission checking, submit busy/error, copy and report dialog are implemented. Dismissal with a draft is confirmed.
- Posting, reporting and reply context are tied to session/version. Closed comments must not silently reopen from stale cached UI.
- Login supports remembered username/password and auto-login; registration intentionally does not treat a create-account response as login. Profile nickname/avatar editing distinguishes “saved but refresh failed” from a failed save, avoiding accidental resubmission.
- Credentials export/import is mode-specific and uses a sensitive clipboard marker. Network mode changes and session generations affect saved credentials, requests and UI; an invalidated mode must not automatically log in and kick out the newer mode.
- Sensitive clipboard, file/photo picker, JPEG preparation and credential persistence are platform seams; the forms and state transitions are reusable.

### Downloads and export

Evidence: `ui/downloads/DownloadsViewModel.kt:28–58,138–202`; `data/download/DownloadManager.kt:68–180,204–320`; `DownloadNetworkPolicy.kt:22–65`; `domain/model/DownloadState.kt`; `domain/model/ComicExport.kt`; `data/repository/ReaderImageRepositoryImpl.kt:38–158`.

- One queue handles comic chapters and novel volumes, with type-specific models and owner/mode-aware novel keys.
- States include waiting, waiting for Wi-Fi, requesting authorization/page, downloading, paused, paused-by-risk, completed and error. Queue resume after restart is deliberate; a restored incomplete task must not silently spend quota/start networking.
- Existing completed items survive merged requests; only explicitly selected failed units retry. Progress exposes count, current unit, image count and transfer speed; pause/resume/cancel, cancel all and image concurrency are exposed.
- Session/network changes pause novel tasks; account-bound cached content must not become visible to another owner.
- Wi-Fi-only checks the actual validated default network and binds a fresh client's socket factory/DNS/pool to that network. A VPN with cellular transport is not considered pure Wi-Fi. Preserve the behavioral policy even where the OS exposes a different API.
- Downloaded grid merges comics and novels, shows local completeness, opens local details and supports bulk deletion. Cancellation/deletion waits for active operations to exit so writes do not recreate deleted content.
- Chapter comments are stored separately with completeness/closure state. Removing a downloaded chapter cascades its comment snapshot in the database.
- Comic export supports ZIP, one CBZ per chapter and original-image folders; preparation validates complete source pages, reports skips, chooses a file/directory destination, tracks byte progress, partial output/failures and cleanup warnings, and permits retry/cancel.
- Android background downloads and export have separate foreground services, notifications and retry/open intents; desktop and iOS require capability-appropriate lifetime/notification adapters. Browser lifetime is not equivalent to an Android foreground service.

### Settings, network and diagnostics

Evidence: `ui/profile/ProfileScreen.kt:101–234,534–590`; `SettingsViewModel.kt`; `DataSettingsScreen.kt:39–108,113–267`; `NetworkSettingsViewModel.kt`; `domain/model/NetworkSettings.kt`.

- Theme system/light/dark, Android dynamic palette and expressive motion; global comic reading direction/scale/orientation defaults; Wi-Fi-only download; export-password option and default resets.
- Data management distinguishes disposable image cache, downloaded manga and novel body/illustration/API caches; cache cleanup promises must remain accurate.
- Versioned settings/reading backup validates the whole file first, merges rather than overwrites newer reading positions, excludes login credentials/download files/cache bodies, and has rollback semantics that preserve edits made after import.
- Network mode WEB/OFFICIAL_APP, API/WWW/login endpoints, discovered routes/share hosts, auto-discovery, route/fixed/default choice, proxy system/manual/direct, tests/latency and choose-fastest, reset/rollback, device-identity diagnostics.
- Saving dirty network settings differs from testing a draft; leaving unsaved settings must retain its existing discard handling.
- Local request logs have enable/export/clear behavior and dedicated redaction. Do not place raw credential headers or protocol signing material into generic desktop logging.
- About/version/repository links, Android app-notification settings access and OS permission UX need platform-specific handling.

## Cross-feature correctness contracts to preserve

1. **Session identity is not a boolean.** `AuthDataStore.kt:26–154` separates mode, owner and generation. Network setting changes advance generation, stale requests cannot write auth/cookies, and restored credentials invalidate another mode for the same account. Keep these rules in shared logic with platform credential storage.
2. **A server response cannot overwrite newer local reading.** `ReadingProgressRepositoryImpl.kt:121–177` records locally first, uses monotonically increasing request versions, protects pending synchronization and drops cross-owner work. The application-scope writer survives reader disposal.
3. **Source coordinates and stable keys are persistent formats.** `LocalReading.kt`, `ReadingMergePolicy.kt` and `ReadingPositionPolicy.kt` use UTF-16 length-prefixed identity keys and strict deletion cutoffs. Novel line/character offsets are raw-source UTF-16 offsets, not grapheme counts. Change format only with explicit migration.
4. **Corruption is an error, not an empty library.** `LocalReadingStore.kt:30–90` retains unreadable source and exposes an error; it must not flush an empty replacement over data it failed to decode.
5. **Do not replay consequential writes.** The comment/register/profile/member/official-download transports disable automatic retry and redirects and strip the 503 replay trigger. A generic new client must retain request-specific replay policy.
6. **Risk control is global and intentional.** Keep the app's gate and offline fallback; do not reinterpret risk response as generic retryable I/O and start a retry storm.
7. **Download removal and export failure need actual cleanup semantics.** Persisted queue state, active request cancellation, integrity validation, quota handling and partial outputs belong in the migration contract.
8. **Android data continuity is a product requirement.** Database name/schema, SharedPreferences/DataStore names, cache ownership, settings/reading codecs and directory structure cannot be silently replaced while the existing app remains installed.

## Portability classification and recommended seams

### A. Common extraction with small deliberate repairs

- Most `domain/model` and `domain/repository` types, `ReadingMergePolicy`, `ImportRollbackPolicy`, `NovelBookSearch`, `NovelReaderDocument`, `NovelReaderImageGeometry`, `NovelReaderPage`, `NovelRequestSpec`, `NovelTextTools`, preference normalization, selection/range and download-state policies.
- `ui/components/ComicGridLayout`, `GridLoadMoreEffect` predicate, reader-position resolution, novel source-selection/entry policies and most immutable UI state models.
- Pure Compose layouts/gestures/theme tokens can be common after platform imports, image binding and DI are removed from their boundaries; retain exact layout and callback contracts.

Specific domain leaks that require treatment before moving wholesale:

| Current file | Leak | Required behavior-preserving seam |
|---|---|---|
| `model/DownloadState.kt` | `java.util.UUID` defaults | Inject/generate task IDs outside data model or use common ID abstraction |
| `model/LocalReading.kt`, `NovelAnnotation.kt`, `ReaderTools.kt` | `System.currentTimeMillis()` defaults | Shared/injected wall clock; preserve monotonic version policy |
| `reading/ReadingPositionPolicy.kt` | `Math.round` | Common half-up equivalent with fixture tests, not different tie rounding |
| `reading/NovelTextDocument.kt` | Java Charset decoder | Strict decoder supporting current BOM/legacy charset contract; JVM implementation first |
| `reading/ReadingContentHash.kt` | MessageDigest/Locale | Compatible SHA-256/hex implementation with byte-identical fixture tests |
| `reading/NovelImagePositionDebugStore.kt`, `usecase/FilterHomeComics.kt` | `javax.inject`; latter time call | Constructor dependencies; keep debug diagnostics and time behavior |
| `repository/NetworkLogRepository.kt` | `java.io.OutputStream` | Byte sink/file destination abstraction |
| `repository/RiskControlRepository.kt` | `java.io.IOException` superclass | Common domain failure with transport mapping |

### B. Reusable state machines with lifecycle/DI/platform wrappers

All current screen ViewModels use Android Hilt construction. Much of their actual state and coroutine logic can be shared, but constructor-injected repositories and explicit route inputs should replace Hilt and Android `SavedStateHandle` coupling at the UI boundary. Replace `android.util.Log`, `System` clocks and JVM synchronization where present. Lifecycle-aware state collection remains useful, but actual API/artifact support must be compiled on each target. The toolchain worker has verified published source jars for the candidate Compose foundation 1.12.1 and Material 3 1.12.0-alpha03 contain the current selection/menu and expressive APIs; this source-availability check is not target compilation or runtime parity.

Keep Android Hilt as an adapter initially if needed: Android routes may obtain existing VMs and pass state/events into newly shared content. Then move a feature's presenter once the same real feature has tests and both host adapters.

### C. Android adapters to retain, then replace per platform

| Responsibility | Current coupling | Platform contract |
|---|---|---|
| App/window/back/rotation | Activity, LocalView, WindowInsetsController, ActivityInfo, BackHandler | Host window effect + common back dispatcher; desktop fullscreen/close and iOS orientation policy |
| Runtime permission/notifications | notification permission launcher, service channels, pending intents | Capability-aware notification/background execution service |
| Connectivity | ConnectivityManager, Android Network/DNS/socket binding | Network policy observation; do not assume browser can identify Wi-Fi |
| File open/save/export | ActivityResultContracts, SAF, ContentResolver, DocumentsContract, FileProvider | Open/save destination, read/write bytes, share file, cleanup result |
| Image decode/load | Coil 2, Android Drawable/BitmapFactory, Context | Common image request/content API and verified target loaders/decoders |
| Gallery/share/clipboard | MediaStore, Intent, Android clipboard sensitive flag | Platform media-save/share/clipboard, with sensitive-copy handling |
| Local persistence | SharedPreferences, context DataStore creation, Android Room builder | Stable store interfaces and platform paths/drivers; Android migration adapter |
| Platform format/theme | Android ICU compact numbers, dynamic palette, formatter | Common presentation formatting; Android dynamic-color provider |
| Profile photo/device identity | Android decoder/device Build values | Image preprocessing and device/request-identity provider |

### D. JVM-shareable bridge, not `commonMain`

Retrofit, Gson DTO/codec parsing, current OkHttp interceptors, Java streams/files/ZIP/crypto, Charset decoding, concurrency atomics and many caches can be reorganized into an Android/JVM shared source layer after Android `Context` and Hilt/logging dependencies are extracted. This makes Windows a real functional second target faster. It does not establish iOS or Wasm portability. Use interfaces so native/browser replacements can follow without changing domain semantics or UI again.

For cross-platform transport/serialization rewrites, first create recorded fixture and contract tests against the existing implementation. Preserve optional/malformed API fields, lenient response parsing where intentional, authentication headers, per-request host routing, generations, replay rules and WWW catalog/content parsing. Endpoint-only mocks are insufficient evidence.

## Android-preserving delivery sequence

1. Freeze the baseline and add parity fixtures/golden screenshots from the running Android build. Keep application ID, release/signing, resources, data locations and foreground services intact.
2. Add common domain/reading module with smallest safe extraction; run original policy tests unchanged where possible. Test source-coordinate, hash, merge/deletion and backup fixtures across JVM and common targets.
3. Extract shared shell/theme/component contracts and one real vertical slice (for example search → comic detail or novel detail → reader) while Android still calls the same repository adapters. Make shared code consumed by Android immediately; avoid unused duplicated screens.
4. Windows desktop host uses the shared screens and actual repositories through JVM bridges, desktop file paths/pickers and background service abstraction. Test resize, mouse wheel/drag, keyboard focus, close/resume and offline state, not just launch.
5. Extract download/cache/export scheduling policy from OS lifetime adapters; complete desktop persistence and interrupted-operation tests. Android must still resume existing data and queue semantics.
6. iOS gets native transport, storage, media/picker/window and background-lifetime adapters. Preserve reader layout/state and expose exact OS capability differences; do not promise indefinite service-equivalent downloads.
7. Linux reuses JVM desktop functionality with Linux packaging/file dialog/clipboard/notification tests.
8. Wasm comes last: prove endpoint CORS, credentials/header restrictions, image loading and durable browser storage early, then implement supported capabilities. Browser proxy settings, raw sockets, filesystem paths and background service lifetime are not portable by changing imports.

This sequence reflects the requested priority Android > Windows > iOS > Linux > Wasm. “Targets declared” and “features migrated” must be tracked separately.

## Minimum acceptance matrix

A feature is migrated only when it works on Android and the new target with real data or contract-equivalent recorded fixtures, and platform differences are explicit.

| Area | Required cases |
|---|---|
| Shell | 839/840dp resize, rail collapse, preserve all tab scroll/filter state, direct tab animation, detail/reader hide shell, nested back order |
| Search/home | delayed first load, refresh with old data, load-more error/retry, filter readiness without scroll jump, returned author/theme route, blacklist scope |
| Detail | local/remote resume race, group change/sort, pending catalog, multiple selection ranges and cross-group state, completed/downloaded badges |
| Comic reader | RTL/LTR/vertical; zoom focus and fling brake; long image fraction restore; mode change, chapter append, comment insertion/removal; explicit page resume; end-of-chapter completion gating |
| Reader tools | auto-read pause on all interactions/background; no auto-restart; slider jump only on release; bookmark round trip; offline adjacent chapter/catalog; image original-byte save and partial export cleanup |
| Novel | Dedicated checklist in `NOVEL_READER_PARITY.md`: reflow anchors, legacy decoding, inline illustrations, selection, annotations, current/book search, source choice, account-bound cache |
| Accounts/network | login/logout/mode switch during request; stale response/cookie rejection; same account across modes; no duplicate posting/register/download authorization on retry/503/redirect |
| Persistence | upgrade existing Android DB/preferences; corrupt-store retention; newer local beats older remote; guest/account separation; deletion tombstones survive backup/import and rollback |
| Downloads | process interruption and manual queue resume; Wi-Fi drop/recovery; manual pause remains paused; quota/risk failure; cancel/delete while active; novel session change; partial/corrupt cache |
| Accessibility/input | readable dynamic/fallback palettes, semantics, focus order, keyboard back/submit, touch long press/zoom, pointer scroll; CJK font/reflow equivalence |

## Existing regression assets and gaps

There are 19 JVM test files covering queue persistence, local-download access, archive/export behavior, reading/backup codecs, progress loading, novel annotations/cache/metadata/page/document/search/text tools, source selection/entry gate/text menu, grid pagination and comic reader positioning. Preserve and port these first. See the actual `app/src/test` tree rather than infer coverage from filenames; existing tests do not establish visual parity, Android lifecycle behavior or target compilation.

Missing evidence in this audit: Android build/run, current screenshots from the pinned commit, desktop/iOS/Linux/Wasm compilation, live API access and OS-level permission/background behavior. The acquisition itself is verified; those runtime stages are not represented as passed.
