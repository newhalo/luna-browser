# Security Policy

## Supported versions

Only the latest release receives security fixes. Luna is a young project (0.x): please update before
reporting.

## Reporting a vulnerability

**Please do not open a public issue for a security problem.**

Use GitHub's private reporting: **Security → Report a vulnerability** on this repository. Include:

- what you found and why it matters,
- steps or a proof of concept to reproduce it,
- the Luna version and device / Android version.

You can expect an acknowledgement within a few days. We will work with you on a fix and credit you in the
release notes unless you prefer to stay anonymous.

## What is in scope

- Anything that lets a web page escape the browser's intended boundaries (the app exposes **no** JavaScript
  interface to pages; if you find one, that is a bug).
- Mishandling of the filter lists the app downloads (they are parsed by `data/filter/`); a malicious list
  should at worst block content, never crash the app or run code.
- Data leaks: Luna has no analytics or accounts and sends nothing but the requests listed in the
  [README](README.md#privacy).

- The **phone remote** (`remote/PhoneRemote.kt`): a LAN-only HTTP server that is PIN-protected and only runs while the
  app is on screen. Authentication bypasses, ways to make it open non-web URLs, or to run it while off, are in scope.

## Hardening already in place

- **Phone remote:** LAN peers only (loopback / private / link-local addresses), PIN compared in constant time,
  six-digit random PIN, global lock-out (5 wrong tries → 30 s), requests capped at 8 KB and 5 s in total,
  three workers with a short queue, only `http(s)` pages can be opened, a strict Content-Security-Policy on the page.
- App backups are disabled (`allowBackup=false`), so history and the PIN can't be pulled with `adb backup`.
- Debug-only features (WebView remote debugging, filter log) are guarded by the debuggable flag.
- `ACTION_VIEW` intents are accepted for `http` / `https` only; remote thumbnail URLs must be `http(s)`;
  downloaded filter lists are capped at 25 MB and must look like a list.
- No JavaScript interface is exposed to web pages.

## Out of scope

- Vulnerabilities in the Android System WebView / Chromium that Luna renders with — report those upstream.
- Ads or pop-ups that get through the blocker — that is a normal bug, please use the *Ad / pop-up report*
  issue template.

## About the signing key in this repository

`app/dev.keystore` is a **public development key**. Its password is in `app/build.gradle.kts`. It exists so
that every contributor's and CI's debug build is signed identically; it is **not** a secret and does not
identify the maintainers. Official releases are signed with a separate private key that is never committed.
