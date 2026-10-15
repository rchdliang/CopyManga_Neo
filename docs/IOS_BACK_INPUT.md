# iOS native back input

The UIKit root now connects `NativeBackBridge` to the shared `UiBackDispatcher`.
A leading-screen-edge pan (left in LTR, right in RTL) is accepted only when a shared handler is enabled, the
root is attached to a window, and neither the root nor its Compose child presents
a native modal. Ordinary horizontal reader swipes away from that edge remain
untouched. Start/progress/cancel/commit use the same pinned-handler dispatcher as
Android; the shared stack is not modified until commit. A cancelled or reversed
gesture does not navigate. Backgrounding/disappearance cancels pending progress.

Hardware Escape uses the responder-chain key command only when a shared handler
can consume it. Native pickers/share sheets are excluded. Toolbar back callbacks
remain unchanged. The shared framework host now composes the previous destination and seeks real
shared-cover transitions through the same pinned bridge. Preview effects remain
gated; see `PREDICTIVE_BACK.md` for component evidence and native acceptance gaps.

`NativeBackBridgeTest` covers no-op roots, cancellation, completed gestures,
disconnect/reconnect, and threshold policy. These are synthetic common tests;
UIKit gesture arbitration, responder-chain behavior, actual modal presentation,
and Swift/Kotlin framework export still require an Apple build and device checks.
