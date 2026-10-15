# Windows Wi-Fi-only downloads: remaining implementation gap

## Status

The user made desktop Wi-Fi-only support optional on 2026-10-15. It is not a
migration blocker. The experimental native transport is isolated from the
validated application and has only a compile gate, not Windows acceptance.
The API design below records an earlier assessment; in particular its
`DnsQueryEx` candidate is not a validated implementation recommendation.
Further native-policy work is deferred while core packaging proceeds.

**Unsupported, fail-closed; not implemented parity.** Windows downloads currently
require disabling “仅 Wi-Fi 下载”. Detecting an available Wi-Fi adapter alone must
never authorize this policy. Android binds both resolution and connections to its
selected network; desktop does not yet have an equivalent transport.

Read-only API review: 2026-10-15. No Windows native calls, network probes, app
runtime or Gradle build were performed for this assessment.

## Verified integration seams

- `shared/src/desktopMain/kotlin/com/rchdliang/copymanga_neo/data/download/portable/DesktopDownloadServices.kt`:
  monitor reports Wi-Fi binding unavailable; provider rejects Wi-Fi-only clients.
- `shared/src/desktopMain/kotlin/com/rchdliang/copymanga_neo/multiplatform/platform/DesktopHttpClient.kt`:
  routed CIO client supports direct/HTTP proxy requests. Installed Ktor 3.6.0
  `CIOEngineConfig` exposes `dnsResolver`, but no socket-factory/interface hook.
- Installed JDK 21 `StandardSocketOptions` and `ExtendedSocketOptions` expose no
  Windows unicast-interface option. A local-address bind is not proof of egress
  through that interface. JNA 5.19.1 supplies native calls, not a supported public
  JVM socket-handle accessor. Do not rely on reflective JDK descriptor access.
- Installed OkHttp builder exposes `dns`, `socketFactory`, `retryOnConnectionFailure`
  and `fastFallback`. A dedicated Ktor OkHttp engine is a possible integration
  route, but would require adding the engine to desktop dependencies and a real
  native socket adapter. Android already uses that engine; desktop currently does not.
- Common `DownloadNetworkPolicy.kt` carries `bindingId`; `DownloadManager.kt`
  presently applies policy updates using `allowed`. An allowed-to-allowed binding
  change must also retire active requests, not merely select a new client later.
  `DownloadImageTransport.kt` and `PortableAppGraph.kt` provide client-selection
  seams for image and metadata/novel downloads respectively.

## Candidate design, not a validated implementation

1. **Discover and version a binding.** Use `GetAdaptersAddresses` and verify
   `IfType == IF_TYPE_IEEE80211`, operational state, usable addresses and nonzero
   family-specific indices. Retain stable adapter identity plus a generation,
   not just an index that can be reused. A bound reachability check must use the
   same transport; the existing generic HEAD probe cannot establish this policy.
   Invalidate on interface/address/DNS changes and on ambiguous or failed native
   inspection. Do not infer Wi-Fi from interface names or prompt for location/SSID.
2. **Own the resource sockets.** A JNA Winsock adapter must create its own handles,
   apply and verify `IP_UNICAST_IF` (IPv4 index in network byte order) or
   `IPV6_UNICAST_IF` (IPv6 index in host byte order) before connect. Prefer separate
   address-family sockets initially; do not leave IPv4-mapped/dual-stack paths
   unbound. A custom `SocketImpl`/`SocketFactory` could expose these streams to
   OkHttp and JVM TLS without JDK reflection. This is substantial transport work,
   including connect/read timeouts, socket options, cancellation, partial I/O and
   close semantics. Retain ordinary TLS hostname/certificate checks.
3. **Own DNS query lifetime.** Candidate resolution is `DnsQueryEx` with a nonzero
   `InterfaceIndex` and explicit DNS servers belonging to the selected adapter.
   Zero means all interfaces and is forbidden. Resolve A/AAAA deliberately;
   prevent NetBT/multicast/search-suffix fallback and cross-generation cache reuse.
   Review query options such as `DNS_QUERY_WIRE_ONLY`, `DNS_QUERY_NO_NETBT`,
   `DNS_QUERY_NO_MULTICAST` and `DNS_QUERY_TREAT_AS_FQDN`. Never fall back to JVM
   resolution, generic system DNS or an unrelated public resolver. Validate
   configured encrypted-DNS/VPN interactions on Windows rather than assuming that
   an interface field proves every deployment's behavior. Native callback storage
   must survive cancellation until completion; free returned records exactly once.
4. **Bound the first supported proxy policy.** Reject Wi-Fi-only with proxy modes
   unless equivalent end-to-end routing is proven. Loopback proxies can forward
   over another interface; binding the local connection cannot constrain them.
   System proxy/PAC discovery can perform additional unbound work. Do not silently
   bypass a configured proxy or run generic proxy selection before rejecting it.
5. **Enforce generation ownership.** Allocate separate client/pool/DNS state per
   binding generation. Recheck generation before DNS, connect, each redirect and
   final file commit. On invalidation, first revoke authorization, then cancel
   queries and close sockets/retire pools; join active work before releasing native
   callback state. Never reuse a connection from a retired binding or silently
   retry over the default network. Disable transport retries and fast fallback
   initially. Monitoring alone is insufficient: native socket confinement must
   remain effective between a route change and notification delivery.

These changes are per-request networking, not permission to modify adapter, DNS,
VPN, firewall, routing-table, weak-host or other OS/security settings. Unsupported
VPN/filter-driver/proxy configurations must remain fail-closed; do not promise
protection against arbitrary third-party traffic interception.

## Required acceptance evidence before enabling

Synthetic tests should cover native error handling, byte order, distinct IPv4/IPv6
indices, stale/reused indices, DNS synchronous versus asynchronous completion,
cancel/completion races, exactly-once handle/record cleanup, no system fallback,
proxy rejection before lookup, generation rollover while `allowed` stays true,
redirects and late responses failing before commit.

Native Windows testing must then establish actual routing, not just API success:

- Wi-Fi plus Ethernet/cellular: resource and DNS traffic use only the selected
  Wi-Fi interface even when another interface has the preferred default route.
- Wi-Fi disconnect/reconnect, address/DNS change and adapter replacement during
  DNS, connect, TLS, pooled reuse and streaming: old work stops, no other-network
  fallback, incomplete output is not committed, resume requires a new binding.
- IPv4-only, IPv6-only and dual-stack networks; unreachable DNS, truncated DNS/TCP
  resolution, link-local scope and failed socket-option application.
- Manual remote/loopback proxy, system proxy/PAC, VPN and configured encrypted DNS:
  supported cases demonstrate confinement; unsupported cases explain refusal
  without bypassing settings or emitting a preliminary unbound request.
- Ordinary TLS verification, cancellation, timeouts and repeated shutdown leave
  no active query callbacks, sockets or pools; non-Wi-Fi-only behavior remains intact.

Use controlled test endpoints and appropriate Windows traffic observation with
separate authorization. No matching-platform evidence has been collected yet.

## Official API references

- [Adapter addresses, type, status and indices](https://learn.microsoft.com/en-us/windows/win32/api/iptypes/ns-iptypes-ip_adapter_addresses_lh)
- [IPv4 socket options: IP_UNICAST_IF](https://learn.microsoft.com/en-us/windows/win32/winsock/ipproto-ip-socket-options)
- [IPv6 socket options: IPV6_UNICAST_IF](https://learn.microsoft.com/en-us/windows/win32/winsock/ipproto-ipv6-socket-options)
- [DNS_QUERY_REQUEST and InterfaceIndex](https://learn.microsoft.com/en-us/windows/desktop/api/windns/ns-windns-dns_query_request)
- [DnsQueryEx completion and ownership](https://learn.microsoft.com/en-us/windows/win32/api/windns/nf-windns-dnsqueryex)
- [DNS query options](https://learn.microsoft.com/en-us/windows/win32/dns/dns-constants)
- [Interface change notification](https://learn.microsoft.com/en-us/windows/win32/api/netioapi/nf-netioapi-notifyipinterfacechange)
