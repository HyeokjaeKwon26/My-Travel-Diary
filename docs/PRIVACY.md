# Privacy and data handling / 개인정보 안내

## 한국어

- 타임라인 파일, 사진 분석, 여행 재생과 영상 생성은 기기에서 처리합니다. 타임라인 파일이나 사진 원본을 업로드하는 서비스는 없습니다.
- **Internet street detail은 기본적으로 꺼져 있습니다.** 지도 설정에서 켜면 현재 화면에 필요한 OpenStreetMap 타일을 Wi-Fi 또는 모바일 데이터로 요청합니다. 공급자는 IP 주소와 요청 지역을 나타내는 타일 좌표를 받습니다. 지형 다운로드와 여행 전체 지도 사전 다운로드는 없습니다.
- 지도 캐시는 최대 96 MiB이며 공급자의 캐시 만료 규칙을 따릅니다. 캐시가 없으면 내장 지도가 표시됩니다. 영상 생성 중에는 새 지도를 다운로드하지 않고, 시작 시 고정한 캐시를 사용합니다.
- 사진 분석은 내장 ML Kit 모델로 로컬에서 수행합니다. SDK는 기기·앱·성능·사용 진단 정보를 전송할 수 있습니다. 이는 사진 원본 업로드와 다릅니다. [Google의 데이터 공개 안내](https://developers.google.com/ml-kit/android-data-disclosure)를 참고하세요.
- 사진 선택 결과는 앱 내부에 저장하지만 원본 사진을 복사하지 않습니다. 갤러리 원본 삭제나 접근 권한 변경으로 사진이 보이지 않을 수 있습니다. 이미 저장한 MP4는 독립된 파일입니다.
- 여행 백업에는 위치 기록과 사진 연결 정보가 포함되며 원본 사진은 포함되지 않습니다. 앱 삭제 전 백업과 원본을 따로 보관하세요. 복원은 기존 여행을 덮어쓰지 않고 새 여행을 만듭니다.
- 영상의 장소 이름 일반화 옵션은 경로, 지도 글자, 사진 속 주소·얼굴 등을 가리지 않습니다. 공유 전 영상을 확인하세요. 공유 대상은 Android 공유 화면에서 직접 선택합니다.

## English

Timeline import, photo analysis, playback and video creation run on your device. The app has no service for uploading Timeline files or original photos.

**Internet street detail is off by default.** Enabling it requests visible-area tiles from OpenStreetMap over Wi-Fi or mobile data. The provider receives your IP address and tile coordinates revealing the requested area. There are no terrain downloads or whole-trip map downloads. The 96 MiB cache follows provider expiry rules; missing detail falls back to the bundled map. Export freezes existing cached detail and makes no new map requests.

Photo analysis uses a bundled ML Kit model locally. The SDK may send device, app, performance and usage diagnostics, separate from photo contents; see [Google's disclosure](https://developers.google.com/ml-kit/android-data-disclosure).

Saved photo choices are not copies of original images. Gallery deletion or permission changes can make photos unavailable. Saved MP4 videos are independent files. Journey backups contain location records and photo references, not originals. Keep backups and originals before uninstalling. Restore creates a new trip rather than overwriting an existing one.

Generalizing place names does not hide routes, map labels or information inside images. Review exports before sharing. You choose recipients in the Android sharesheet.

Map attribution and policy: [OpenStreetMap copyright](https://www.openstreetmap.org/copyright) · [Tile usage policy](https://operations.osmfoundation.org/policies/tiles/).
