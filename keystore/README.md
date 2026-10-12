# Signing key

`pocketlm-debug.p12.b64` is the shared debug/release key so APKs from GitHub Actions and local builds upgrade in place.

- alias: `pocketlm`
- store password: `pocketlm`
- key password: `pocketlm`

This is a dev key checked into the repo on purpose. Do not use it for Play Store.

The decoded `.p12` is gitignored. Gradle recreates it from the base64 file.

Uninstall any pocketLM that was signed with the old per-machine debug key once. After that, new builds install over the old one.
