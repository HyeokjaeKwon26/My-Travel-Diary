# Modern 2D edition — verification record

Version: 1.1.0-rc1 (code 2). Application ID: `com.traveler`.

The original edition now uses the diary, stable photo selections, progress reporting, calendar, sorting, fullscreen controls and video features developed for the 3D edition. Maps and vehicles are drawn in a flat Canvas. There is no terrain preparation, terrain download or 3D scene renderer.

## Compatibility

The published v1.0.0 APK was installed first, followed by the new APK without uninstalling or clearing storage. Database version 5 migrated to 6. Every original column and row in the five data tables was compared: 2 trips, 88 visits, 89 movement segments, 301 media links and 0 manual overrides were preserved. Illustrative screenshot fixtures were added separately.

The update retains the original signing certificate, SHA-256 `54ad7010acf04183d99c44c41f2851cffc743d2db24291bfb070917d43a18290`. That certificate originated as an Android debug certificate; the distributed release build itself is optimized and non-debuggable. Private keys are not part of the repository.

## Automated checks

- JVM tests: 307 tests across 97 suites, no failures or errors. Includes original import/timezone/route tests and new flat-camera bounds, saved photo choices, visit summaries, sorting and export helpers.
- The final Android regression run passed all 29 tests. An additional full export-class run passed all 5 tests (one overlaps the final run), for 33 distinct instrumentation checks. Android checks cover database migration, accurate trip-card summaries, narrow layouts and enlarged fonts, photo aspect ratio and representative-photo action placement, calendar confirmation insets, autohiding controls, seeking, rotation and Back behavior.
- Street-map checks use local fixtures to verify placement in portrait and landscape, cache reuse, disabled-network behavior, conditional HTTP requests and cancellation/revisit behavior.
- Real video tests check H.264/AAC output, portrait 1080×1920, landscape 1280×720, audio on/off, timestamps, audio duration, cancellation cleanup, share intents and saved-player rotation.

The final build (`assembleDebug assembleDebugAndroidTest assembleRelease lintDebug`) completed successfully. Lint reported no errors (84 warnings and 6 informational findings). Packaged native fields, ESRI resources, timezone data and bundled map headers passed verification. Both MP4s decoded fully with FFmpeg without errors.

The minified release candidate was also installed over the existing app and opened its saved trip list. A Boston fixture exported through the actual UI, saved to the gallery, and opened in the built-in preview player. The 9-second MP4 (7,558,999 bytes) decoded fully with FFmpeg. The subsequent source change only removes a leftover terrain sentence and changes the suggested backup filename to `.travel.json`.

## Distribution

The universal APK is the optimized Gradle release. The ARM64 download contains the same code/resources, retaining only `arm64-v8a` native libraries, then realigned and signed with the same update certificate. Both APKs passed signature and ZIP-alignment checks; the manifest retains minimum API 26 and has no debuggable flag.

Final download hashes are published with the [release checksum file](https://github.com/HyeokjaeKwon26/My-Travel-Diary/releases/download/v1.1.0-rc1/SHA256SUMS.txt).

## Screenshots

The [current screenshots](screenshots-modern/) were captured from the Android app with illustrative fixtures. The cover image is extracted from an encoded MP4. The Boston and canyon routes are demonstrations, not navigation data or a user's location history.

## Practical limits

Validation uses an Android API 36 emulator. A physical Galaxy S23 Ultra was unavailable. This does not establish frame rate, battery use, heat or video encoding performance on every phone and tablet.

Street detail is optional and off by default. It loads only the visible area while the map is on screen, including while playback is paused; backgrounding closes network activity. Export freezes available cached detail. Missing detail shows bundled geography. Saved photo selections and journey backups do not copy gallery originals.

See the [development guide](DEVELOPMENT.md) for build commands and the user-facing [Korean](../README.md) / [English](../README.en.md) guides for installation and actual usage.
