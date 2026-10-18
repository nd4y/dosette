# ADB transport provenance

Vendored from [libadb-android-bc](https://github.com/osservatorionessuno/libadb-android-bc),
commit `dcd931071b5e4b9ebc0ac598201755d5a8814b71`, originally
[LibADB Android](https://github.com/MuntashirAkon/libadb-android).
Used under Apache-2.0; original SPDX headers and LICENSES are retained.
Includes a Java BoringSSL-compatible SPAKE2 implementation and Bouncy Castle TLS.
No native binaries, hidden API bypass or arbitrary shell UI are shipped by Dosette.

Unused USB, mDNS, legacy PRNG and connection-manager adapters are omitted.

Local changes: bounded TCP connect/read deadlines; pairing socket is closed even
when TLS setup fails; remove peer-info logging; clear pairing password on close;
fix unsigned InputStream.read and drain queued stream data after remote close;
expose the TLS state so Dosette rejects plaintext ADB;
remove a static SSLContext cache that otherwise reuses a discarded client key.
Upstream cryptographic/stream tests are retained. ADB TLS uses the standard ADB
trust model, with user-authorized SPAKE2 pairing rather than Web PKI validation.
The upstream library has not undergone a security audit.
