# Architecture

## Runtime flow

```text
External app copies text
        |
        v
Android system Clipboard
        |
        v
CyberKeyboardImeService (default IME)
        |
        +--> reject password-field / OS-sensitive clips
        |
        v
ClipStore (SQLite, private app storage)
        |
        +--> Archive UI and search
        +--> Clipboard-only suggestion chips in keyboard
        +--> re-insert old text through InputConnection
```

## Components

- **MainActivity:** Jetpack Compose UI for archive search, saved-item cards, favorites, keyboard activation, and archive-suggestions toggle.
- **KeyboardImeService:** actual Android input method. Owns the clipboard listener and initial custom keyboard view.
- **ClipStore:** private SQLite database. Each accepted clipboard event is stored as a separate row with full text and timestamp. No automatic retention cap or cleanup job is configured.
- **KeyboardPreferences:** stores the archive-suggestion preference. Suggestions are from saved clipboard records only; no typed-word prediction or autocorrection.

## Privacy boundaries

- Password fields suppress suggestions and clipboard capture.
- Clips marked sensitive by the source application are skipped.
- The archive does not upload clipboard contents.
- Translation is a future HTTPS capability; never ship privileged API credentials in the APK.
- Accessibility Service is not used to bypass Android clipboard restrictions.

## Translation boundary

The keyboard will call a provider through a dedicated translation layer in a later step. Choose the provider after checking language coverage, latency, data retention, quota, and secure credential handling. Preview the returned translation and insert only after user confirmation.

## Current implementation notes

The first slice uses native SQLiteOpenHelper for predictable local storage. Search currently uses SQLite LIKE and should be upgraded after measuring a large archive. Arabic and English are the first built-in layouts.
