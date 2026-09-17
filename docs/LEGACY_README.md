# My Travel Diary (Traveler)

[![Release: v1.0.0](https://img.shields.io/badge/Release-v1.0.0-brightgreen.svg)](https://github.com/HyeokjaeKwon26/My-Travel-Diary/releases)
[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)
[![Platform](https://img.shields.io/badge/Platform-Android_8.0%2B-green.svg)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Kotlin-1.9%2B-purple.svg)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/UI-Jetpack_Compose-blueviolet.svg)](https://developer.android.com/jetpack/compose)
[![Privacy](https://img.shields.io/badge/Privacy-100%25_Offline-success.svg)](#-privacy-first)

**My Travel Diary** is a 100% offline, privacy-first Android application that reconstructs your personal journeys into cinematic chronological travel stories. By importing Google Timeline JSON exports and local photo galleries, it builds smooth vector-mapped journeys with dynamic camera motion, photo moments, and adventure telemetry.

> **Language**: [English](#-english) | [한국어](#-한국어)

---

<a name="-english"></a>
## 🌐 English

### 📱 How to Export Your Google Timeline JSON

JSON is a file containing your location history. You do not need to open or edit it. First check the trip dates under **Google Maps → profile picture → Your Timeline**, then export from the phone that holds those records.

#### Export from Android Settings

1. Open your phone's **Settings** app.
2. Select **Location → Location services → Timeline**.
3. Choose **Export Timeline data → Continue**.
4. Pick an easy-to-find folder such as **Downloads**, then tap **Save**.
5. Wait for export to finish. In My Travel Diary, use **+ New Travel Story → Choose Timeline JSON File** to select the saved **`.json` file**.

These steps follow [Google's current Android instructions](https://support.google.com/maps/answer/6258979?co=GENIE.Platform%3DAndroid&hl=en). Menu names can vary by device; if necessary, search for `Timeline` in your phone settings.

#### If you get stuck

- **No export option:** Update Google Maps and check that you are using the phone with your history. Timeline is unavailable on Android Go.
- **No trip records:** Check the account and phone used during the trip. This app cannot recover unrecorded or deleted routes from photos alone.
- **Backup is on, but there is no JSON:** Timeline backup and file export are separate. Use **Export Timeline data** to save the file you need.
- **Google Takeout on a computer:** For your first import, use the phone export steps above. Select a JSON file containing journey records, not a ZIP archive or a map screenshot.

---

### 📖 User Guide

#### 1️⃣ Create Trip & Import Timeline
| Home Screen (My Trips) | New Travel Story Screen |
| :---: | :---: |
| <img src="docs/screenshots/01_home_screen.png" width="300" alt="Home Screen"/> | <img src="docs/screenshots/02_create_trip_screen.png" width="300" alt="Create Trip Screen"/> |

1. Tap **`+ New Travel Story`** on the home screen.
2. Enter an optional **Trip Title** and select your **Travel Date Range** (`YYYY-MM-DD`).
3. Tap **`Choose Timeline JSON File`**, open **Downloads** or your chosen export folder, and select the **`.json` file** saved above.
4. Tap **`Enable`** to grant read access to your local photo gallery.
5. Tap **`Reconstruct Travel Story`**. GPS trajectory filtering, visit clustering, and photo EXIF timestamp synchronization are processed **100% on-device** without any cloud connection.

---

#### 2️⃣ Chronological Diary & Media Exploration
| Trip Diary Timeline | High-Resolution Photo Viewer & EXIF |
| :---: | :---: |
| <img src="docs/screenshots/03_trip_detail_diary.png" width="300" alt="Trip Diary Timeline"/> | <img src="docs/screenshots/05_photo_detail_dialog.png" width="300" alt="Photo Detail & EXIF"/> |

- **Summary Dashboard**: View total distance (`Total Distance`), media count (`Captured Media`), and trip length (`Duration`) at a glance.
- **Chronological Timeline**: Step through every visit place with dwell duration and every transit segment (`Driving`, `Walking`, `Flight`, etc.) ordered by time.
- **Camera Auto-Focus**: Tap any visit or transit card in the diary to smoothly fly the map camera directly to that geographic coordinate.
- **EXIF Photo Viewer**: Tap any photo thumbnail to open a high-resolution dialog showing capture timestamps, precise GPS coordinates, and camera exposure details.

---

#### 3️⃣ 60fps Cinematic Playback & Adventure Cockpit HUD
| Cinematic Playback Start (Cockpit HUD) | In-Flight Tracking Over Bering Strait |
| :---: | :---: |
| <img src="docs/screenshots/04_cinematic_playback.png" width="300" alt="Cinematic Playback"/> | <img src="docs/screenshots/playback_mid_segment.png" width="300" alt="Playback Mid Segment"/> |

- **Cinematic Look-Ahead Camera**: Tap `▶` to play your journey like a movie. The camera smoothly pans, tilts, and zooms according to speed and curvature using Hermite spline smoothing.
- **Adventure Cockpit Glass HUD**:
  - **Clean Mode Indicator**: Shows current transport mode emoji (`✈️ Airplane`, `🚗 Car`, `🚶 Walk`, `📍 Place Name`). Zero fake or mock speed values.
  - **Distance Progress Telemetry**: Synchronized live odometer showing `Traveled Distance / Total Trip Distance` (e.g. `7,317.7 km / 11,000.0 km`), exactly matching the dashboard summary.
  - **Dynamic Progress Bar**: Visual gradient bar displaying exact journey completion percentage.
  - **Ken Burns Photo Cards**: Photos captured at stop locations smoothly fade in during visit pauses.
- **Full Playback Controls**: Pause/Resume, playback rate switcher (1x, 2x...), scrub slider, continuous loop, and bundled travel soundtrack toggle.

---

#### 4️⃣ Place Names & Transport Customization
| Edit Place Name Dialog | Change Transport Mode Dialog |
| :---: | :---: |
| <img src="docs/screenshots/06_edit_place_name_dialog.png" width="300" alt="Edit Place Name"/> | <img src="docs/screenshots/08_edit_transport_dialog.png" width="300" alt="Edit Transport Mode"/> |

- **Custom Place Names**: Tap the pencil icon (`✏️`) beside any visit place in the diary to edit its display name (e.g., `Seven Magic Mountains`, `Grand Canyon Hotel`).
- **Transport Mode Switcher**: If GPS misclassified an activity, tap the transport badge to reassign it to Walking, Running, Cycling, Driving, Bus, Train, Subway, Flight, or Ferry.

---

### ✨ Key Features

- **🗺️ 100% Offline Vector Map Engine**
  - Instant vector rendering via native Android `Canvas` (< 15ms initial render).
  - Bundled multi-resolution Natural Earth vector basemaps (110m world basemap + 10m high-detail regional boundaries).
  - **Zero API keys, zero cloud tokens, zero network requests, zero Google Maps billing.**

- **🎬 Cinematic Story Playback**
  - Smooth 60fps playback engine with Hermite spline path smoothing.
  - Predictive look-ahead camera tracking that dynamically pans, tilts, and zooms based on travel speed and upcoming curvature.
  - Contextual arrival deceleration and cinematic stop pauses.

- **📸 Chronological Photo Binding & EXIF Rotation**
  - Strict parent-child temporal binding between travel episodes and photos using EXIF timestamps and reverse-geocoded spatial proximity.
  - Full EXIF orientation tag parsing (`ExifInterface`) ensuring upright portrait/landscape rendering in both in-app playback and exported MP4 videos.
  - Center-crop image framing preserving original aspect ratios.

- **🧭 Adventure Cockpit Glass HUD**
  - Real-time journey progress tracker displaying current traveled distance vs. total trip distance.
  - Dynamic gradient journey progress bar.
  - Automatic transport mode indicator (`🚗 Car`, `✈️ Flight`, `🚶 Walk`, `🚆 Train`, `🚌 Bus`).

- **📹 Offline Video Export**
  - 1080x1920 (9:16 vertical) native MP4 video export with hardware acceleration.
  - Automatically renders title cards, map routes, photos, and trip summaries.

- **🔒 100% Privacy-First**
  - All timeline parsing, spatial clustering, reverse-geocoding, and map rendering are performed strictly on-device.
  - Your location history and photos never leave your device.

---

<a name="-한국어"></a>
## 🇰🇷 한국어

### 📱 구글 타임라인 JSON 내보내기 방법

JSON은 이동 기록이 담긴 파일로, 직접 열거나 편집할 필요는 없습니다. 먼저 **Google 지도 → 프로필 사진 → 내 타임라인**에서 여행 날짜의 기록을 확인하고, 그 기록이 저장된 휴대폰에서 내보내세요.

#### Android 휴대폰 설정에서 내보내기

1. 휴대폰의 **설정** 앱을 엽니다.
2. **위치 → 위치 서비스 → 타임라인**을 선택합니다.
3. **타임라인 데이터 내보내기 → 계속**을 누릅니다.
4. **Download / 다운로드**처럼 찾기 쉬운 폴더를 고르고 **저장**을 누릅니다.
5. 내보내기가 끝나면 My Travel Diary의 **+ New Travel Story → Choose Timeline JSON File**에서 저장한 **`.json` 파일**을 선택합니다.

이 순서는 [Google의 현재 Android 공식 안내](https://support.google.com/maps/answer/6258979?co=GENIE.Platform%3DAndroid&hl=ko)를 따릅니다. 기기에 따라 메뉴 이름이 다를 수 있으며, 찾기 어렵다면 휴대폰 설정에서 `타임라인`을 검색해 보세요.

#### 준비하다 막혔을 때

- **내보내기 메뉴가 안 보여요:** Google 지도를 업데이트하고, 기록이 있는 휴대폰인지 확인하세요. Android Go에서는 타임라인을 지원하지 않습니다.
- **여행 기록이 없어요:** 여행 당시 사용한 계정과 휴대폰을 확인하세요. 기록되지 않았거나 삭제된 경로를 이 앱이 사진만으로 복구할 수는 없습니다.
- **백업은 켰는데 JSON 파일이 없어요:** 타임라인 백업과 파일 내보내기는 별개입니다. 위의 **타임라인 데이터 내보내기**로 파일을 저장하세요.
- **PC의 Google Takeout을 쓰면 되나요?** 처음 가져온다면 위의 휴대폰 내보내기 방법을 이용하세요. 실제 이동 기록이 담긴 JSON 파일을 선택해야 하며, ZIP 압축파일이나 지도 화면 캡처를 선택하면 안 됩니다.

---

### 📖 사용 방법

#### 1️⃣ 여행 생성 및 데이터 불러오기
| 홈 화면 (내 여행 목록) | 새 여행 생성 화면 |
| :---: | :---: |
| <img src="docs/screenshots/01_home_screen.png" width="300" alt="홈 화면"/> | <img src="docs/screenshots/02_create_trip_screen.png" width="300" alt="새 여행 생성 화면"/> |

1. 홈 화면 우측 하단의 **`+ New Travel Story`** 버튼을 누릅니다.
2. **여행 제목(Trip Title)** 과 **여행 날짜(Date Range, `YYYY-MM-DD`)** 를 설정합니다.
3. **`Choose Timeline JSON File`** 을 누르고 **다운로드** 또는 앞서 저장한 폴더에서 **`.json` 파일**을 선택합니다.
4. **`Enable`** 버튼을 눌러 사진 갤러리 접근 권한을 허용합니다.
5. **`Reconstruct Travel Story`** 를 누르면 인터넷 연결 없이 **100% 기기 내부(On-Device)** 에서 GPS 경로 정제, 방문지 클러스터링, 사진 EXIF 타임스탬프 동기화가 즉시 이루어집니다.

---

#### 2️⃣ 일별 다이어리 및 타임라인 탐색
| 상세 다이어리 타임라인 | 고해상도 사진 뷰어 & EXIF |
| :---: | :---: |
| <img src="docs/screenshots/03_trip_detail_diary.png" width="300" alt="상세 다이어리"/> | <img src="docs/screenshots/05_photo_detail_dialog.png" width="300" alt="사진 뷰어 & EXIF"/> |

- **종합 대시보드 요약**: 여행 전체의 총 이동 거리(`Total Distance`), 사진 장수(`Captured Media`), 총 소요 일수(`Duration`)가 한눈에 요약됩니다.
- **크로놀로지 타임라인**: Day 1부터 마지막 날까지 방문 장소(`Visit`, 체류 시간 포함)와 이동 구간(`Driving`, `Walking`, `Flight` 등)이 시간 순서대로 일목요연하게 표시됩니다.
- **지도 카메라 오토 포커싱**: 타임라인 카드를 터치하면 상단 벡터 지도가 해당 좌표로 부드럽게 자동 이동합니다.
- **정밀 EXIF 뷰어**: 사진을 터치하면 고해상도 다이얼로그가 열리며 촬영 시각, 세부 GPS 좌표, 카메라 노출값 등 메타데이터를 확인할 수 있습니다.

---

#### 3️⃣ 60fps 시네마틱 여행 재생 & 어드벤처 콕핏 HUD
| 시네마틱 재생 시작 (콕핏 HUD) | 베링 해협 상공 비행 트래킹 |
| :---: | :---: |
| <img src="docs/screenshots/04_cinematic_playback.png" width="300" alt="시네마틱 재생"/> | <img src="docs/screenshots/playback_mid_segment.png" width="300" alt="비행 트래킹"/> |

- **시네마틱 룩어헤드 카메라**: 상단 `▶` 버튼을 누르면 여행 경로의 곡률과 속도에 맞춰 카메라가 유연하게 줌/팬/틸트하며 영화처럼 재생됩니다.
- **어드벤처 콕핏 HUD (Adventure Cockpit)**:
  - **클린 모드 표시**: 현재 이동 수단 이모지(`✈️ Airplane`, `🚗 Car`, `🚶 Walk`, `📍 장소명`)를 깔끔하게 표시 (가짜 속도 표시 완전 제거).
  - **이동 거리 실시간 동기화**: 대시보드 통계와 100% 일치하는 `현재 달린 거리 / 전체 여행 거리` 실시간 오도미터 (예: `7,317.7 km / 11,000.0 km`).
  - **동적 그라데이션 게이지**: 여정 진행률을 한눈에 볼 수 있는 프로그레스 바.
  - **켄 번스(Ken Burns) 포토 카드**: 정차 구간에서는 그 장소의 사진이 우측 상단에 부드럽게 페이드인됩니다.
- **재생 컨트롤러**: 일시정지, 배속 조절(1x, 2x...), 재생 바 스크러빙, 무한 루프 반복, 여행 BGM 켜기/끄기를 자유롭게 제어할 수 있습니다.

---

#### 4️⃣ 장소 이름 및 이동 수단 수정
| 장소 이름 수정 (인라인 펜슬) | 이동 수단 수동 변경 다이얼로그 |
| :---: | :---: |
| <img src="docs/screenshots/06_edit_place_name_dialog.png" width="300" alt="장소명 수정"/> | <img src="docs/screenshots/08_edit_transport_dialog.png" width="300" alt="이동 수단 변경"/> |

- **장소명 커스텀**: 타임라인 장소명 옆 연필 아이콘(`✏️`)을 터치하여 원하는 이름(예: `Seven Magic Mountains`, `인천국제공항`)으로 언제든 수정할 수 있습니다.
- **이동 수단 재분류**: GPS 오인식으로 잘못 분류된 구간이 있다면, 이동 수단 뱃지를 눌러 걷기, 달리기, 자전거, 운전, 버스, 기차, 지하철, 비행기, 페리 등으로 변경할 수 있습니다.

---

### ✨ 주요 기능

- **🗺️ 100% 오프라인 벡터 지도 엔진**
  - 안드로이드 네이티브 `Canvas` 기반 초고속 벡터 렌더링 (초기 렌더링 < 15ms).
  - Natural Earth 글로벌 110m 기본 지도 및 10m 고해상도 지역 경계선 번들 내장.
  - **API 키 없음, 클라우드 토큰 없음, 외부 네트워크 통신 0, 구글 지도 API 과금 0원.**

- **🎬 60fps 시네마틱 재생 엔진**
  - Hermite 스플라인 곡선 스무딩을 적용한 부드러운 60fps 카메라 워크.
  - 이동 속도와 코너 각도에 반응하는 예측형 룩어헤드 줌/팬/틸트.
  - 방문지 도착 시 물리 기반 감속 및 정차 연출.

- **📸 크로놀로지 사진 바인딩 & EXIF 자동 회전**
  - 사진의 EXIF 타임스탬프와 지리적 좌표를 이동 에피소드에 엄격하게 시간순 매핑.
  - `ExifInterface` 기반 세로/가로 사진 자동 회전 및 Center-Crop 프레이밍 (인앱 재생 및 MP4 동영상 내보내기 모두 적용).

- **🧭 어드벤처 콕핏 글래스 HUD**
  - 현재 이동거리 / 총 이동거리 실시간 표시 및 동적 프로그레스 바.
  - 불필요한 속도 텍스트 제거 및 깔끔한 이동 수단/장소명 표시.

- **📹 1080x1920 MP4 동영상 내보내기**
  - 하드웨어 가속을 이용한 9:16 세로형 풀HD 동영상 인코딩 및 저장.
  - 타이틀 카드, 이동 경로 애니메이션, 현장 사진, 엔드 카드가 결합된 완벽한 여행 영상 제작.

- **🔒 100% 오프라인 개인정보 보호**
  - 모든 GPS 데이터 분석, 클러스터링, 지도 렌더링이 기기 내부에서만 수행됩니다.
  - 위치 기록과 사진이 절대 외부 서버로 전송되지 않습니다.

---

## 🛠️ Architecture & Tech Stack

- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) & Material Design 3
- **Image Pipeline**: [Coil](https://coil-kt.github.io/coil/) (EXIF-aware image decoding)
- **Vector Rendering**: Custom Skia/Android Canvas Vector Renderer
- **Geographic Utilities**:
  - WGS-84 Geodesic distance/bearing algorithms (`GeodesicUtils`)
  - Offline reverse-geocoding index (`OfflineCityResolver`)
- **Story Engine**:
  - Deterministic timeline canonicalizer (`MovementTimelineCanonicalizer`)
  - Strict chronological episode interleaving (`TravelStoryTimeline`)

---

## 🚀 Getting Started & Building

### Prerequisites
- Android Studio Jellyfish (2023.3.1) or newer
- JDK 17 (Eclipse Temurin or OpenJDK recommended)
- Android SDK 34 (Android 14)
- Minimum SDK: API 26 (Android 8.0 Oreo)

### Build from Source
```bash
# 1. Clone repository
git clone https://github.com/HyeokjaeKwon26/My-Travel-Diary.git
cd My-Travel-Diary

# 2. Run unit and story regression tests
./gradlew testDebugUnitTest

# 3. Build debug APK
./gradlew assembleDebug
```

Built APK output location:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 개발자 · Developer

**Hyeokjae Kwon, M.D., Ph.D.**

[Website](https://hyeokjaekwon26.github.io/) · [GitHub](https://github.com/HyeokjaeKwon26)

---

## 📄 License

This project is licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)**.
See the [LICENSE](LICENSE) file for details.
