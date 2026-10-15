# Framework-backed predictive back

The shared app now uses Navigation Compose 2.10.0-beta01 for its outer route host.
NavController is the sole live stack; AppRoute IDs continue to own retained screen
models, picker state and saveable content. SavedNavigation keeps the existing
serialized payload shape. Framework routes carry opaque IDs only, without exposing
private reader/session payloads through browser URLs or history.

## Input and transition ownership

Android, iOS, desktop and browser adapters feed the existing UiBackDispatcher.
NavHostBackBridge supplies a standalone framework dispatcher, avoiding a competing
Activity/default native callback. Nested modal, selection and reader-control
handlers keep priority. The selected handler is pinned for start/progress/cancel/
commit; removal or route replacement cannot pop a different destination. No enabled
shared handler leaves Android system back-to-home available.

AppRouteNavHost provides real destination AnimatedContentScope to the existing
shared-cover providers. Predictive pop uses the original zero-duration exit fade;
shared covers interpolate through the framework overlay. The previous destination
may be composed during a gesture. The former scale/clip transform and inert bitmap
capture/cache are removed; keyboard navigation no longer depends on a prior capture.

## Preview safety and lifecycle

RouteLifecycleScope preserves inherited eligibility for destinations and main tabs.
Only the committed active route may start requests, consume notices, paginate,
report reading positions or acquire native window/picker/dialog effects. Inactive
preview images may read caches but may not fetch from the network. Viewport restore
is separate from reporting activation, so activating a preview does not reset the
reader anchor. These gates remain necessary even though NavHost owns transitions.

NavHost can pause the current reader during a gesture. Its legitimate lifecycle
flush is allowed; cancellation must preserve the route identity and exact reading
state. This is not a promise that no lifecycle callback or persistence can occur.
Previous-route composition must not replay network requests, writes or consumed events.

Payload/model cleanup retains the union of live framework stack, visible transition
entries and still-composed destinations. A popped entry that never composed is
completed through public ComposeNavigator.onTransitionComplete before payload
release. Live or still-composed entries remain framework-owned. This handles a
push/pop within one frame without leaking the transient route or clearing a
normally animated outgoing reader early.

## Verification and remaining acceptance

At 3de5273 the focused six-test gate passed: real shared-cover raster geometry and
reversible seeking, exact cancellation restoration, bridge/modal priority and
exactly-once commit, joint framework/payload saved-state restoration, distinct
visits to the same book, normal disposal retention and never-rendered rapid-pop
cleanup. Synthetic cover pixels and reader-local state are component evidence,
not a full application/device test.

The first aggregate gate passed 378 shared tests, 105 Android unit tests, desktop
compilation, Android debug assembly, iOS device/simulator KLIB and Wasm source
compilation. Wasm executable linking failed with Java heap space at 2 GiB; the
4 GiB retry and the subsequent dead-code cleanup remain to be verified.

Still exercise on Android: real artwork/cache misses, detail/reader/novel paths,
both gesture edges, repeated reversal/cancel/commit, comments/selection/control
priority, activity interruption, root back-to-home, TalkBack, rotation/theme/account
changes, precise reader position/draft preservation and native picker recreation.
On Windows verify modal Escape, keyboard focus, repeated route/tab navigation,
resize/theme restoration and saved reader state. iOS gesture arbitration and browser
history behavior also need their native runtime checks. Framework-backed cover
motion is implemented and component-tested; full visual/runtime parity is not claimed.

## Historical implementation

Earlier checkpoints used only a current-route transform, then a bounded inert
pointer-down snapshot (two images / 8 MiB retained pixel estimate). Their cache and
raster tests validated that earlier mechanism, which could not interpolate live
shared-cover endpoints. Those components and obsolete-only tests were removed after
the replacement framework scenes passed. Historical counts in earlier audit logs
must not be interpreted as current-suite counts.
