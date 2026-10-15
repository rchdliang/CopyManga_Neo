# iOS background image transfers: implementation constraint

## Status

**Unimplemented.** iOS downloads remain foreground-only. This is a source/API feasibility review, not an Xcode, simulator, device, or live-transfer validation. No background capability is enabled by this document.

Apple's background `URLSession` can transfer files while the application is suspended. However, the current arbitrary CDN URL contract cannot preserve this project's pre-request redirect guarantees or its strict in-transfer resource bound. Do not enable background transfer by weakening either invariant, using a post-download check as a substitute, or silently falling back to a different network policy.

## Concrete transport blockers

[Apple's background transfer documentation](https://developer.apple.com/documentation/foundation/downloading-files-in-the-background) states that background sessions always follow redirects and do not call the redirect-decision delegate. `PublicResourceTransport.kt` instead checks every hop before transmission: protected application/API boundaries, HTTPS downgrade, redirect loops, and a five-hop limit. A final-response check is too late. A successful foreground preflight cannot guarantee that a later background request receives the same redirects; a hostname allowlist alone cannot guarantee this either.

`DownloadImageTransport.kt` also enforces a 64 MiB per-image maximum while consuming chunks. An app suspended during a background download cannot rely on its callbacks to enforce that same hard transfer/disk bound. Rejecting an oversized completed file protects cache acceptance, but does not undo network or temporary-storage consumption. Expected-byte hints are not an equivalent cap.

There is no established delivery contract in the present application that removes these problems. Revisit native enablement only after an independently verified transport/server contract can preserve both invariants. Do not infer eligibility from a URL's current behavior.

## Existing integration points

All Kotlin paths below are relative to `shared/src/`:

- `commonMain/.../data/remote/portable/PublicResourceTransport.kt`: pre-request redirect and credential-free public-resource boundaries.
- `commonMain/.../data/download/portable/DownloadImageTransport.kt`: bounded streaming, generation checks, response validation.
- `commonMain/.../data/download/portable/DownloadManager.kt`: coroutine-owned image requests, pause/cancel checks, chapter commits, and restart restoration to paused state.
- `commonMain/.../data/download/portable/DownloadQueueStore.kt`: durable chapter/task intent, currently no native-transfer ledger.
- `commonMain/.../multiplatform/app/PortableAppGraph.kt`: constructs the Ktor image transport and owns work through the UI graph's child scope.
- `iosMain/.../data/download/portable/IosDownloadServices.kt`: foreground-only runtime and network monitor.
- `iosMain/.../multiplatform/platform/IosHttpClient.kt`: disables shared cookies/credentials and rejects unsupported manual proxies.
- `iosApp/iosApp/CopyMangaNeoApp.swift`: scene startup; no background-session event bridge exists.

The `...` package prefix is `kotlin/com/rchdliang/copymanga_neo`.

## Bounded coordinator requirements for any future eligible transport

This is design guidance, not implemented infrastructure:

1. Prepare a bounded batch of already-authorized image requests while foregrounded. Keep API discovery, comments, authenticated novel work, and arbitrary coroutine execution outside the background-transfer capability.
2. Persist intent before resuming native tasks: opaque transfer ID, stable session/task mapping, chapter/image identity, destination reservation, request/account identity where relevant, durable policy revision, cancellation revision, and state. Do not use an in-memory generation as the sole restart identity.
3. Own the coordinator at application level, independently of Compose or a reader controller. On every launch, reconcile the durable ledger with native tasks before queue restoration: cancel unknown work, retain valid ownership, classify missing work as interrupted, and process duplicate events idempotently.
4. Add the Swift application-delegate background-event callback and retain its completion handler. A retained session delegate must journal results through a headless Kotlin bridge. Drain events and durable writes before calling the handler on the main queue; UI creation must not be required.
5. Move the temporary file into a uniquely owned staging location before the download delegate returns. Validate HTTP status, length/content, policy revision, and destination ownership before atomic cache publication. Reuse `LocalDownloadAccess` and `DownloadRecords` completeness checks. Image completion alone must not falsely mark required chapter-comment work complete. See [Apple's temporary-file lifetime requirement](https://developer.apple.com/documentation/foundation/urlsessiondownloaddelegate/urlsession(_:downloadtask:didfinishdownloadingto:)).
6. Persist pause/delete/revocation before native cancellation. Reject late completions using tombstones/revisions so deleted cache entries cannot reappear. UI detachment differs from explicit user cancellation. Any resume data must remain private, identity-bound, and optional.
7. Preserve credential-free public requests, normal TLS validation, and proxy restrictions. A policy/configuration change must not leave old native tasks silently running under obsolete permissions.

## OS and network limits that remain

- Reusing the same session identifier permits recovery after system termination. User force-quit cancels transfers and prevents automatic relaunch until the user opens the app. See [background session semantics](https://developer.apple.com/documentation/foundation/urlsessionconfiguration/background(withidentifier:)).
- Scheduling is not guaranteed. Transfers started while already backgrounded are discretionary regardless of the configured flag. See [isDiscretionary](https://developer.apple.com/documentation/foundation/urlsessionconfiguration/isdiscretionary). Do not promise continuous progress or Android foreground-service lifetime.
- [`allowsCellularAccess = false`](https://developer.apple.com/documentation/foundation/urlsessionconfiguration/allowscellularaccess) prohibits cellular connections; it does not establish this project's stronger Wi-Fi binding/VPN-routing guarantee. Keep unsupported policy combinations unavailable rather than relabeling them as equivalent.

Safe preliminary work would be pure ledger/reconciliation/cancellation contracts tested with a fake transfer engine. It would not establish real background support. Native enablement still requires the transport constraints above to be resolved and subsequent Xcode/device verification.
