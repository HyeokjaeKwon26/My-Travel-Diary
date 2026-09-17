# Development — modern 2D edition

The original application ID remains `com.traveler`. Version 1.1.0-rc1 (code 2) ports the shared diary, photo, playback and export improvements from My Travel Diary 3D RC12 while rendering maps and vehicles in 2D Canvas. Original source history and AGPL licensing remain intact.

## Build and validate

Use JDK 17, Android SDK 36 and the Gradle wrapper:

```powershell
./gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
./gradlew.bat connectedDebugAndroidTest
./gradlew.bat assembleRelease
```

Local release signing is configured through ignored `keystore.properties`. The original v1.0.0 APK was signed with the workstation's Android debug certificate. Updates must retain that exact certificate to install over the original; an unrelated new production key cannot update existing installs. Release code is optimized and non-debuggable even though this compatibility certificate originated as a debug key. Never commit private keys or credentials.

Database version 5 migrates additively to 6. Original routes, visits, media references, and manual overrides must survive. Saved photo analysis and selections live outside the evictable cache, not as copies of photos. Archives use `MyTravelDiary`, version 1; restore also accepts the 3D edition's version-1 archive. Local photo permissions and original image files do not transfer through an archive.

## Rendering boundaries

- `feature/map/flat`: fixed north-up camera, Canvas vehicles, optional visible-area street tiles and UI settings.
- `feature/map/renderer`: projected basemap, clipped routes and shared screen/video map rendering.
- `core/journey`: shared horizontal geometry and archives; no terrain preparation.
- Video encoding submits the same Canvas scene to the codec surface. Its OpenGL texture submission does not render a 3D scene.
- Street detail is opt-in, with provider-compliant caching. Export reads a frozen cache, has no network worker and always draws bundled geography beneath missing tiles.

Keep the [Korean README](../README.md), [English README](../README.en.md), user guides, [privacy notice](PRIVACY.md), and [Disclaimer](../DISCLAIMER.md) aligned with actual behavior. Use screenshots from the current 2D app and label illustrative trips. Older technical documents and the [legacy README](LEGACY_README.md) describe previous releases, not current behavior or performance guarantees.

Release validation and practical limits: [modernization status](MODERNIZATION_STATUS.md).
