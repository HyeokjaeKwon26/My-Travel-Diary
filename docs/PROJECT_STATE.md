# Traveler (My Travel Diary) - 프로젝트 상태 및 아키텍처 현황 (Project State)

## 1. 프로젝트 최종 목표 및 아키텍처 철학
Google Maps Timeline(타임라인) 내보내기 데이터와 Android 기기 갤러리의 사진/동영상을 결합하여, 여행 일정·이동 경로·이동 수단(도보, 러닝, 자전거, 차량, 버스, 기차, 지하철, 비행기, 페리 등)·방문 장소·사진을 타임라인 다이어리 및 맞춤형 시네마틱 여행 지도로 시각화하고, 오프라인 음향 트랙이 포함된 고화질 MP4 여행 비디오를 내보내는 **100% 로컬 오프라인 (Zero-Network, Zero-API-Key, Privacy-First)** Android 여행 기록 애플리케이션입니다.

---

## 2. 핵심 아키텍처 및 구현 사양 (Pass 21.1 Wire Photo Story Engine into Real Playback & MP4 Audio Export)

### A. Photo Story Engine의 실제 대화형 재생 통합 (P0, P0-01 ~ P0-03)
* **단일 결정론적 스토리 타임라인**:
  * 구형 `TimelineStoryCompressor`를 전면 대체하고, 대화형 Compose Canvas 지도 재생(`TravelMapView.kt`)과 오프라인 MP4 비디오 렌더러(`TravelVideoRenderer.kt`)가 모두 `TravelStoryTimeline.build(renderModel, profile)`를 단일 진실 공급원으로 공유.
  * 다이어리에는 수백 장의 원본 사진이 보존되되, 시네마틱 재생 및 비디오 내보내기에서는 `PhotoStoryEngine`이 선정한 대표 사진 모먼트(6일 여행 기준 20~40장 예산)만을 단일 타임라인에 맞춰 정밀하게 표시.

### B. 실제 유효 타임스탬프 정렬 및 인위적 시간 왜곡 제거 (P0-04 ~ P0-05)
* 과거 코드의 `maxOf(effectiveTs, runningTimeMs)`와 같이 단조성을 강제하기 위해 사진의 실제 촬영 시간을 미래로 인위 변경하던 로직을 전면 제거.
* 클러스터별로 대표 사진을 선정하고 실제 유효 타임스탬프를 부여한 후, 후보 모먼트 전역을 유효 타임스탬프 기준으로 엄격하게 선행 정렬한 뒤 글로벌 예산에 맞춰 트리밍.

### C. 스토리 시간(Story Time) 기반 사진 표시 윈도우 스케줄링 (P0-06 ~ P0-08)
* `PhotoStoryMoment`에 `scheduledStoryTimeSeconds`, `displayStartStorySeconds`, `displayEndStorySeconds` 필드 추가.
* 다시간 방문(Long Visit) 내 다중 클러스터 사진에 대해 서로 겹치지 않는 순차적 표시 윈도우(~1.5~2.5초)를 배정.
* `TravelStoryTimeline.evaluateAtStoryTime` 평가 시 현재 스토리 시간이 배정된 윈도우 내에 있을 때만 `activePhoto`가 활성화되며, 윈도우 외 구간에서는 `activePhoto = null`로 복귀하여 사진이 지도/경로를 계속 가리는 현상 방지.

### D. 경로 마커 불변식 유지 (P0-09 ~ P0-10)
* 경로 마커의 좌표 및 카메라 뷰포트는 사진의 GPS 좌표에 의해 변경되지 않고, 항상 정규화된 이동 경로(Movement) 보간점 또는 방문(Visit) 앵커 좌표에 엄격히 구속(`photoDrivenRoutePositionMutationCount = 0`).

### E. 네이티브 AAC 오디오 인코딩 및 완성형 MP4 비디오 내보내기 (P1 ~ P1-03)
* 번들 오디오 에셋(`R.raw.traveler_memories.wav`)의 PCM 데이터를 읽어 `audio/mp4a-latm` AAC `MediaCodec`을 통해 인코딩 및 2.0초 페이드아웃 루프 처리.
* `MediaMuxer`에 비디오(H.264 AVC) 및 오디오(AAC) 트랙을 동시 멀티플렉싱 (`includeMusic = true` 시 오디오 트랙 포함, `false` 시 비디오 단독).
* 비디오 내보내기 대화상자(`ExportVideoDialog.kt`)에서 재생 시간("Duration: 2:04")과 파일 크기("Size: 87.3 MB")를 명확히 분리 표기하고, **[미리보기 재생(Play Preview)]** 및 인코딩 중 **[취소(Cancel)]** 기능 완비.

---

## 3. 제21.1차 검증 결과 매트릭스 (Pass 21.1 Matrix)

| 항목 ID | 요구사항 | 상태 | 구현 내용 및 검증 근거 |
| :--- | :--- | :--- | :--- |
| **P0** | TravelMapView 대화형 재생에 Photo Story Engine 직접 연결 | **해결 완료 (FIXED)** | `TravelMapView.kt` 내 `TimelineStoryCompressor` 제거 및 `TravelStoryTimeline.build` 적용. 대화형 재생과 MP4 내보내기 동일 타임라인 구동. |
| **P0-01~05** | 실제 타임스탬프 보존 및 인위적 시간 왜곡 제거 | **해결 완료 (FIXED)** | `PhotoStoryEngine.kt` 내 `maxOf` 제거, 유효 타임스탬프 기반 전역 정렬. `PhotoStoryEngineChronologyTest` 통과. |
| **P0-06~08** | 스토리 시간 기반 사진 표시 윈도우 스케줄링 | **해결 완료 (FIXED)** | `TravelStoryTimeline.kt` 내 non-overlapping 사진 윈도우 및 윈도우 외 `activePhoto = null` 보장. `Pass21AlignmentRegressionTest` 통과. |
| **P0-09~10** | 경로 마커 불변식 보장 | **해결 완료 (FIXED)** | 마커 좌표는 방문 앵커 및 이동 보간점에만 구속. `testRouteMarkerNeverMutatedByPhotoGps` 통과. |
| **P1~03** | AAC 오디오 믹싱, 비디오 PTS 단조성, UI 개선 | **해결 완료 (FIXED)** | `TravelVideoEncoder.kt` AAC 인코딩 및 루프/페이드아웃, `ExportVideoDialog.kt` 미리보기/취소/시간-크기 분리. `TravelVideoExportAndroidTest` 통과. |
| **P0** | 날짜 범위 로컬 타임존 필터링 수정 | **해결 완료 (FIXED)** | `tools/verify_real_timeline.py` `ZoneInfo` 기반 `[localStart, localEndExclusive)` 수정. |
| **전체 파이프라인** | 247 JVM 유닛 테스트, 0 Lint 에러, 13 온디바이스 계측 테스트 및 패키징 | **해결 완료 (FIXED)** | `tools/verify_and_package.py` 전체 실행 완료: 247 JVM 유닛 테스트 통과, 0 Lint 에러, 13 온디바이스 계측 테스트 통과, 17종 UI 스크린샷 검증, `Traveler_Pass21.1_Final.zip` 생성. |

---

## 4. 최종 패키징 산출물 (Pass 21.1 Deliverables)
* **`Traveler_Pass21.1_Final.zip`** (SHA-256: `768bce8c4c66a7cbdcf25f8901311ef697477434f6310d373a016f033ba3d0c2`)
* **`verification.zip`** (50.5 MB)
* **`Traveler_Pass21.1_Final.zip.sha256`**
