# Windows reader entry regression checks

The c5f3dd68 package was reported to show `Reentry into ignoringRedrawRequests is not allowed` immediately on entering both manga and novel chapters. The novel screenshot also showed a separate PowerShell `Add-Type` failure for the keep-awake request.

The desktop entry point now supplies `ComposeDesktopWindowPolicy` backed by the same `WindowState` passed to `Window`. Reader effects change observable placement only; they no longer directly change `GraphicsDevice.fullScreenWindow` during composition changes. The Compose 1.12.1 runtime contains this exact exception in `SwingInteropContainer.postponingExecutingScheduledUpdates`, supporting a reentrant native window update diagnosis. A full Windows stack trace and device retest are still required to confirm the reported error is eliminated; Linux headless tests are not Windows GUI validation.

Local regression coverage exercises original floating/maximized/fullscreen placement restoration, overlapping reader leases, idempotent disposal, repeated sessions, preservation of explicit host placement changes, and actual Compose effect entry/disposal. The separate keep-awake tests verify lossless encoded PowerShell transport; the native keep-awake test executes only on Windows.

## Device acceptance after installing a newly built Windows package

1. Start from a floating window. Open a manga chapter, show/hide the controls, return to its details. No redraw error, and the prior placement should return.
2. Repeat with a novel chapter, then repeat from a maximized window. Test entering/exiting several chapters in succession.
3. Open controls, resize, and use Escape/back to exit reading. Confirm input and scrolling remain responsive.
4. With keep-screen-on enabled, enter a chapter and confirm no `Add-Type` warning. Exit or minimize and confirm the reader request is released. The build's native test verifies startup/release, not actual display behavior under every machine's power policy.
5. Confirm reading progress remains available after restarting. Do not clear application data or change system power/security settings to work around these errors.

Use the package built from the repair commit; reinstalling the old c5f3dd68 MSI as EXE (or vice versa) does not change the app code.
