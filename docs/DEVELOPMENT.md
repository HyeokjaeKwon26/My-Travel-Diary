# Development — modern 2D edition

The current New edition is 2.1.0 (code 2), application ID `com.traveler.next`. It installs alongside the original `com.traveler` application and updates New 2.0.0 with the same dedicated signing key. Maps and vehicles use 2D Canvas. Original source history and AGPL licensing remain intact. See [2.1.0 features](ENHANCEMENTS_2_1.md) and [migration](SIDE_BY_SIDE_MIGRATION.md).

## Build and validate

Use JDK 17, Android SDK 36 and the Gradle wrapper:

```powershell
./gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
./gradlew.bat connectedDebugAndroidTest
./gradlew.bat assembleRelease
```

Local release signing is configured through ignored `keystore.properties` and uses the New edition's dedicated production key. It is not compatible with the original application's certificate. Keep the original app installed while migrating records and photos; never uninstall it to work around a signature mismatch. Release code is optimized and non-debuggable. Never commit private keys or credentials.

Database version 5 migrates additively to 6. Original routes, visits, media references, and manual overrides must survive. Saved photo analysis and selections live outside the evictable cache, not as copies of photos. Archives use `MyTravelDiary`, version 1; restore also accepts the 3D edition's version-1 archive. Local photo permissions and original image files do not transfer through an archive.

## Rendering boundaries

- `feature/map/flat`: fixed north-up camera, Canvas vehicles, optional visible-area street tiles and UI settings.
- `feature/map/renderer`: projected basemap, clipped routes and shared screen/video map rendering.
- `core/journey`: shared horizontal geometry and archives; no terrain preparation.
- Video encoding submits the same Canvas scene to the codec surface. Its OpenGL texture submission does not render a 3D scene.
- Street detail is opt-in, with provider-compliant caching. Export reads a frozen cache, has no network worker and always draws bundled geography beneath missing tiles.

Keep the [Korean README](../README.md), [English README](../README.en.md), user guides, [privacy notice](PRIVACY.md), and [Disclaimer](../DISCLAIMER.md) aligned with actual behavior. Use screenshots from the current 2D app and label illustrative trips. Older technical documents and the [legacy README](LEGACY_README.md) describe previous releases, not current behavior or performance guarantees.

Release validation and practical limits: [modernization status](MODERNIZATION_STATUS.md).

Current source update: [optional media storage and portable ZIP backups](PORTABLE_MEMORIES.md).

In-app update implementation and release metadata generation: [IN_APP_UPDATES.md](IN_APP_UPDATES.md).
