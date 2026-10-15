# Browser permission-sensitive actions

The browser adapter displays a local accessible confirmation surface after a
link or image is prepared. The Continue/Retry DOM click calls `navigator.share`,
`showSaveFilePicker`, or `showDirectoryPicker` synchronously, before any await.
Image sharing resolves IndexedDB/fetch and constructs the File before showing
that confirmation. File writing starts after the picker returns. Android/iOS
flows are unchanged.

The confirmation follows the current application palette, traps Tab/Escape,
makes underlying canvas siblings inert, and restores focus on close. Browser
Back cancels the confirmation instead of navigating the app. Coroutine disposal
removes the surface and prevents a stale click from starting an operation.
Retry retains the prepared payload. Cancellation cannot undo a share or picker
already handed to the operating system; it only stops this application's
continuation.

Notifications use an explicit confirmation click. Only granted/denied decisions
are recorded, while default/dismissed/blocked results remain retryable. Legacy
attempt flags no longer suppress a permission prompt that never appeared.

Run synthetic adapter tests with:

    node --test tests/host/browser-activation.test.mjs

These mocks verify sequencing and cancellation, not browser rendering, actual
permission dialogs, screen-reader behavior, or platform interoperability.

Clipboard read/write also require a fresh confirmation click. Prepared sensitive text
stays in the operation closure and generic clipboard failure feedback never includes
the API exception or clipboard payload. Cancelling native sharing never copies data.

Reader fullscreen uses the same confirmation/retry surface, tied to a reader effect
lease. Leaving cancels an unopened gate. A late completed native fullscreen request
can only clean up its currently tracked owner; a replaced lease or foreign fullscreen
element is not exited by the old lease. Fullscreen exit events clear ownership so a
subsequent externally entered fullscreen is not treated as the reader's. User dismissal
of the gate is quiet; toggling reader controls off again creates a fresh request.
These are synthetic ownership/API checks, not browser runtime validation.
