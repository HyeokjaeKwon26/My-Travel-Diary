# Traveler - 내장 음원 라이선스 및 저작권 명세 (Music Licenses & Redistribution Rights)

본 문서는 Traveler 애플리케이션에 내장된 오프라인 배경 음악(Soundtrack)의 저작권, 출처 및 재배포 라이선스 검증 내역을 기록합니다.

---

## 1. 정책 및 원칙 (Policy)

* **100% 오프라인 & Zero-Network**:
  * 모든 배경 음악은 APK 내 로컬 리소스(`app/src/main/res/raw/`)로 번들링되어 제공되며, 런타임 스트리밍, 외부 네트워크 요청, API 키, 계정 로그인을 일체 요구하지 않습니다.
  * 비행기 탑승 모드(Airplane Mode) 및 완전한 오프라인 상태에서도 동일하게 재생됩니다.
* **검증된 퍼블릭 도메인 및 프로젝트 자체 저작물만 포함**:
  * "로열티 프리(Royalty Free)"라는 모호한 라벨에 의존하지 않고, 애플리케이션 내 번들링 및 재배포 권리가 명확히 보장된 **Creative Commons Zero v1.0 Universal (CC0 1.0) Public Domain Dedication** 및 **Traveler 프로젝트 자체 오리지널 합성 음원**만을 포함합니다.
  * 상업적 음원, 저작권 침해 우려 음원, 외부 플랫폼 다운로드 음원은 일체 배제합니다.

---

## 2. 번들링 음원 상세 내역 (Bundled Tracks)

### Track 01: Travel Memories (기본 내장 트랙)
* **파일명**: `traveler_memories.wav` (또는 `traveler_memories.ogg`)
* **표시 트랙명**: `Travel Memories`
* **장르 / 스타일**: Calm, warm, nostalgic, light acoustic guitar & ambient piano arpeggio (Cmaj7 $\rightarrow$ Am9 $\rightarrow$ Fmaj7 $\rightarrow$ Gsus4)
* **길이 및 루프**: 24.0초 무봉제(Seamless) 루프
* **작곡 및 제작**: Traveler Project Original Synthesis (`tools/generate_soundtrack.py`)
* **라이선스 & 저작권 포기 선언 (CC0 1.0 Dedication)**:
  * 본 음원은 Traveler 프로젝트에 의해 직접 작곡 및 합성된 오리지널 음원으로서, **Creative Commons Zero v1.0 Universal (CC0 1.0)** 조건에 따라 전세계 저작권 및 인접권을 포기하고 퍼블릭 도메인(Public Domain)으로 기증되었습니다.
* **재배포 권한**: 영구적(Perpetual), 전세계적(Worldwide), 무제한 앱 내 번들링 및 재배포 허용.
* **마스터링**: 피크 진폭 정규화(Peak Normalization, -1.5dBFS) 완료.
* **출처 검증일**: 2026-08-24

---

## 3. 오디오 재생 엔진 사양 (Audio Architecture)

* **컴포넌트**: `com.traveler.feature.map.audio.TravelSoundtrackPlayer`
* **오디오 포커스**: `AudioAttributes.USAGE_MEDIA` 및 `AudioManager.OnAudioFocusChangeListener` 준수 (전화 수신/타 앱 실행 시 자동 일시정지 및 덕킹 지원).
* **속도 독립성**: 시각적 스토리 재생 속도(0.5×, 1×, 1.5×, 2×, 3×)가 변경되어도 음원의 피치와 템포는 1× 정속을 유지하여 왜곡 방지.
* **라이프사이클**: 여행 재생 활성화 시에만 재생되며, 일시정지/백그라운드/종료 시 자동으로 중단 및 리소스 회수.
