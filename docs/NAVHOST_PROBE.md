# Isolated shared NavHost probe

Isolated migration branch, originally based on validated main `6b5b491`, now merged
through main `9dec340`. The initial compatibility/bridge probes passed root-owned
gates. This branch now replaces the application outer host; the validated main
worktree is unchanged. The new production integration and added tests are pending
root compilation/test gates. No application/emulator run or full runtime parity
claim is made.

## Dependency/API evidence

- Official Compose 1.12.1 release table pairs the installed Compose version and
  Lifecycle 2.11.0 with `org.jetbrains.androidx.navigation:navigation-*:2.10.0-beta01`:
  https://github.com/JetBrains/compose-multiplatform/blob/master/CHANGELOG.md#1121-september-2026
- Common NavHost, iOS swipe, desktop Escape:
  https://kotlinlang.org/docs/multiplatform/compose-navigation.html
- Entry lifecycle and Wasm `bindToBrowserNavigation()` (experimental):
  https://kotlinlang.org/docs/multiplatform/compose-navigation-routing.html
- Current common implementation: predictive transition parameters, seek/cancel,
  and previous destination composition using entry-specific owners:
  https://github.com/JetBrains/compose-multiplatform-core/blob/jb-main/navigation/navigation-compose/src/commonMain/kotlin/androidx/navigation/compose/NavHost.kt
- SharedTransitionLayout + destination AnimatedContentScope integration:
  https://developer.android.com/develop/ui/compose/animation/shared-elements/navigation

Initial Maven metadata requests timed out. Root subsequently resolved the exact
beta and compiled desktop, Android, iOS Arm64, iOS simulator Arm64 and Wasm
(successful serial gate, 5m33s). Resolved metadata lists Android, desktop,
iosArm64, iosSimulatorArm64 and wasm-js variants. Android beta is a forwarding
artifact depending on upstream androidx.navigation:navigation-compose-android:2.10.0.
Exact desktop beta and delegated Android bytecode both register NavHost handlers
through LocalNavigationEventDispatcherOwner. Do not infer common availability from Android Navigation
2.10.2 alone. Common dependency coordinates use the JetBrains group.

## Root-owned verification

From this worktree, with the same toolchain/environment/properties as the validated
main gate, run serially (the author did not run Gradle):

```
./gradlew :shared:compileKotlinDesktop
./gradlew :shared:compileAndroidMain
./gradlew :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64
./gradlew :shared:compileKotlinWasmJs
```

`SharedNavHostProbe.kt` intentionally compiles in commonMain without being mounted.
It verifies NavHost, its predictive exit parameter, three destinations, and reuse
of the existing ComicSharedTransitionProvider with a destination scope. It does
not yet exercise real shared elements, gestures, state restoration or networking.

## Thinnest production integration direction (not implemented)

1. Keep the existing shared screens and service/model factories. Adapt navigation
   callbacks to one NavController-authoritative stack; do not mirror mutable
   NavigationState and NavController stacks in both directions.
2. Preserve explicit AppRoute IDs in route arguments and retain the existing
   RouteModels/SavedStateHandle continuation store initially. Restore serialized
   AppRoute records once into the graph; preserve tab state inside Main. Do not
   simultaneously migrate model ownership and navigation persistence.
3. Replace only the outer AnimatedContent with NavHost inside the existing
   SharedTransitionLayout; pass destination scopes through the existing provider.
   Use original transition timings, including predictive fadeOut(0). Snapshot
   preview/current-route transform must not also animate these destinations.
4. Use NavHost entry lifecycle for rendered destinations, while retaining a
   separate committed-active/effects gate. Gate preview entry input and nested
   back handlers. A previous entry being composed is not an active route.
5. Define a single back arbitration path. Dialogs, comments, novel tags and reader
   controls must precede route pop; main-tab-to-home precedes application exit.
   Current UiBackDispatcher and NavHost's own handler cannot independently pop.
   Android/iOS predictive events must reach the selected navigation handler;
   forwarding only a completed pop loses seeking. Remove/disable competing native
   edge/Escape/browser bridges only after replacement preserves modal priority.
   Browser activation dialogs must still consume back before navigation.

## Preview-sensitive effects that must be addressed first

Paths below are relative to shared/src/commonMain/kotlin/com/rchdliang/copymanga_neo/multiplatform/.

- `ui/detail/DetailScreen.kt`: LaunchedEffect(pathWord) invokes loadDetail; resumed
  callback invokes onScreenResumed; toast effect consumes model events. Confirm
  idempotency and keep refresh/event consumption behind committed-active gating.
- `ui/reader/ReaderScreen.kt`: openReader has a same-route initialization guard in
  ReaderViewModel, which is helpful but not a complete preview contract. The screen
  also watches local chapters, acquires immersive policy, loads comments/preloads,
  consumes toasts and manages auto-read. Preview must not acquire window leases or
  start speculative network/event work. Plain LaunchedEffect is not lifecycle-gated.
- ReaderScreen's DisposableEffect flushPosition and ON_PAUSE flush can be triggered
  by navigation transitions. A speculative previous-reader composition disposed on
  cancel must not flush. A legitimate current-reader flush on the framework
  lifecycle pause is acceptable when it preserves the correct position, as in the
  original; the invariant is no speculative previous-route writes/network replay
  and no position reset on cancellation. Preserve genuine background/leave persistence;
  do not remove saves globally to avoid preview writes.
- `ui/novel/NovelScreen.kt`: initial openInitialBook, ON_RESUME refreshShelfState,
  notice consumption and disposal/pause flush need the same distinction. Directory
  opening can trigger reload; viewport restoration must not write a preview position.
- Both readers retain composition-local anchor/initial-page state as well as model
  state. Restore it via entry saveable state or explicit retained reader state before
  allowing live preview. Cancellation must preserve current reader scroll/selection,
  model identity, drafts, pending picker operation and stack identity.

Live shared-cover interpolation needs both endpoints composed. NavHost can supply
this without duplicating screen implementations, but cannot promise inert previews
by itself. Required tests before production integration: nested-back precedence,
progress/cancel/commit stack invariants, zero preview network/write/event counters,
reader anchor/model retention, background save, repeated detail routes, tab state,
process recreation and picker continuation. Then native Android gesture/shared-cover
and Windows keyboard acceptance remain necessary. No full-parity claim is made.

## Framework-backed back seam (isolated probe implementation)

`NavHostBackBridge` supplies an independent NavigationEventDispatcherOwner around
NavHost. It has no parent dispatcher and no native input registration. Existing
host events continue through UiBackDispatcher; its stable lower-priority route
registration forwards selected start/progress/cancel/complete to a small
NavigationEventInput subclass. That subclass also observes framework handler
availability so roots and stopped hosts do not swallow application back.

The exact resolved Navigation Event 1.1.1 API includes NavigationEventInput,
NavigationEventDispatcher.addInput/removeInput/dispose, and
LocalNavigationEventDispatcherOwner.provides. DirectNavigationEventInput also
exists, but subclassing the base is useful for onHasEnabledHandlersChanged.
NavHost's exact beta handler prepares both entries on start, seeks via progress,
resets on cancel, and pops via ComposeNavigator only on complete. No internal API
is invoked by the adapter.

Keep this route registration stable and earlier than nested handlers. Screen
BackHandlers that always call route back (NovelScreen is one) must become
conditional overlay-only handlers, or deliberately forward the full gesture;
otherwise they consume progress before it reaches NavHost. Main-tab-to-home stays
an earlier root fallback outside the NavController stack. Do not use the default
host-owned framework dispatcher, which would introduce competing native input.
Do not bind to browser navigation: preserve opaque existing browser history and
never serialize AppRoute.resume, owner identifiers or credentials into URLs or
history payloads. No public deep-link behavior is introduced.

The small offscreen NavHostBackBridgeSceneTest uses real NavHost with placeholder
screens and owner lifecycle. It covers predictive cancel/commit, nested priority,
ordinary back, duplicate commit, route replacement during a gesture and disposal.
It is authored but not yet run; root owns execution:

```
./gradlew :shared:desktopTest --tests '*NavHostBackBridgeSceneTest'
```

Production screens remain unmounted until the separate committed-active effect
changes are integrated. NavController remains the only outer stack; explicit
route IDs and existing RouteModels saved state remain the integration direction.

### Direct bridge dependencies

The first bridge gate failed before tests because Navigation Compose declares
Navigation Event Compose as a runtime dependency. The bridge now directly declares
`org.jetbrains.androidx.navigationevent:navigationevent-compose:1.1.0` and
`androidx.navigationevent:navigationevent:1.1.1`. Cached Gradle metadata confirms
that the former forwards to upstream navigationevent-compose 1.1.1, whose API
variants export navigationevent 1.1.1; these are the exact already-resolved APIs
inspected above. This exposes the existing compatible libraries to compilation,
without selecting a new Navigation Event version. Verification remains root-owned.

### Interrupted scene attempt and bounded retry

Bridge attempt 2 compiled but produced no test result before root interrupted it
(exit 130); this is neither a pass nor a diagnosed framework failure. The retry
coordinates scheduler time (`advanceTimeBy(16)`) and render timestamps from the
same TestCoroutineScheduler clock, retains a fixed 30-frame pump per stage, and
logs scheduler/render/drain boundaries to stderr. SharedTransitionLayout and
NavHost now use full-window constraints, matching the production host.

The scene has an explicit child SupervisorJob; disposal and lifecycle destruction
are followed by cancellation/draining before Main is reset. All previous assertions
remain. runTest has a 30-second timeout, but that timeout cannot forcibly stop a
non-cooperative synchronous render loop. Root should retain an external bounded
process timeout and use `--info` to expose captured test-stage stderr in the build
log. These changes improve the harness and diagnosis; they do not establish the
cause or claim a passing scene test before the retry completes.


The next two diagnostic attempts again stopped at scene construction and were
interrupted (exit 130). JVM socket attachment failed, so no runtime stack was
obtained. Exact installed lifecycle-runtime-desktop 2.11.0 bytecode provides a
source-supported explanation: MainDispatcherChecker.updateMainDispatcherThread
uses runBlocking(Dispatchers.Main.immediate). With StandardTestDispatcher as Main,
that queued discovery task cannot run while synchronous construction is blocked.
The next test revision uses UnconfinedTestDispatcher for Main and a separate
StandardTestDispatcher sharing the scheduler for the scene. The bounded frame pump,
cleanup and behavioral assertions remain. This explanation awaits retry evidence;
it is not represented as a captured runtime diagnosis.


## Production integration candidate (component-tested; final aggregate pending)

Reviewable commits: b92fd70 introduces AppNavigation and opaque payload restoration;
6fab455 installs AppRouteNavHost and the route-state raster test; 39dc0f5 adds the
actual shared-cover geometry raster test. Root main activity/image gating and
reader width-reflow changes through 9dec340 are merged before verification.

- NavController alone owns the live outer stack. Private SavedNavigation retains
  its prior serialized tab/stack shape as the payload/restoration fallback.
  Existing restored framework entries win over replay, preventing duplicate stacks.
- AppRoute.id remains the child model/picker/saveable key. The framework route is
  ContentRouteKey(id) only; browser/native hosts receive no payload or new deep links.
- No route effect runs before restore readiness. Notification Downloads waits for
  readiness, then clears to Main and selects the tab before acknowledging its event.
- Model and saveable cleanup follows the union of stack, visible transition entries,
  and still-composed entries. Initial pre-attachment cleanup captures readiness to
  avoid pruning payloads before the restore effect finishes. No 400ms deadline is used.
- Main tabs retain their existing inner AnimatedContent and state providers. Export
  dialogs/application models remain above the route host. Novel internal navigation
  still consumes back; only actual outer-route exits pass to framework seeking.
- The new host uses real destination AnimatedContentScope with existing cover
  providers and the original fade timings. The old inert screenshot capture/scale
  treatment and its obsolete-only tests have been removed. The small original
  probe lives in desktopTest as a fixture for bridge regression coverage.
- Existing platform input adapters remain the sole native source, through the
  standalone framework dispatcher bridge. No host-default second dispatcher is used.

Root-owned focused gate:

```
./gradlew :shared:desktopTest --tests '*NavHostBackBridgeSceneTest' --tests '*AppRouteNavHostStateSceneTest' --tests '*NavHostSharedCoverSceneTest' --tests '*NavHostNavigationStateTest' --info
```

Then run all shared tests and compile Android/desktop/iOS/Wasm serially. The new
state scene exercises joint controller/payload saved-state recreation, inactive
preview effects, cancellation preserving reader-local state, committed cleanup,
and tab/root fallback. The geometry scene uses real production cover modifiers
with synthetic image pixels; it is not a repository-backed full-app acceptance
run. Native gesture/input, real artwork/image-cache behavior, picker recreation
and saved-reader behavior still require their respective acceptance checks.

### Latest verification

The focused retry at 3de5273 passed all six tests in 1m18s, including normal outgoing
retention and rapid never-rendered route cleanup through the public navigator API.
The first aggregate gate then passed 378 shared tests, 105 Android unit tests,
desktop compilation, Android debug assembly, iOS device/simulator KLIB and Wasm
source compilation. Wasm executable linking failed with `Java heap space` at
2 GiB, without the prior generated-cache redefinition error. The project heap is
now 4 GiB and the dead-code cleanup awaits the root-owned final serial gate.
Earlier interrupted scene runs and provisional research above remain historical
evidence; they do not describe the currently passing focused scene gate.
