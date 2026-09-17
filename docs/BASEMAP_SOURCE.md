# Traveler - 오프라인 벡터 베이스맵 출처 및 생성 사양 (Basemap Sources & Specification)

본 문서는 Traveler 애플리케이션에 내장된 오프라인 벡터 베이스맵(`app/src/main/assets/basemap_world.json`)의 원천 데이터셋, 라이선스, SHA-256 체크섬 및 생성 파이프라인을 기술합니다.

---

## 1. Current map sources (1.1.0-rc1)

- The bundled regional geography and reference roads come from Natural Earth public-domain data. The source manifest and checksums are in [cartography-sources.json](cartography-sources.json). `tools/build_flat_cartography.py` builds `assets/basemap_3d.bin.gz`; the inherited filename names a binary data format, not a 3D renderer. AAPT packages it as `basemap_3d.bin`. The app draws it with a flat Canvas.
- Optional street detail uses [OpenStreetMap standard raster tiles](https://operations.osmfoundation.org/policies/tiles/) from `tile.openstreetmap.org`, with [OpenStreetMap contributor attribution](https://www.openstreetmap.org/copyright). It is disabled by default. Only the visible area is requested, with an identifying User-Agent, server expiry/conditional requests, bounded caching, and backoff. No route prefetch or offline map packs are offered.
- Export uses a frozen copy of previously cached detail without network downloads. Bundled geography is always drawn beneath missing detail. The app is not a navigation product.

The tables below document the original bundled world-map generation. Their historical sizes and hashes are not checksums for the newer binary asset.

---

## 2. 원천 데이터셋 명세 (`tools/basemap_sources/`)

| 소스 파일명 | 원천 데이터셋 | 데이터 유형 | 용량 (Bytes) | SHA-256 체크섬 |
| :--- | :--- | :--- | :--- | :--- |
| `ne_110m_land.geojson` | Natural Earth 1:110m Land | 전세계 육지 다각형 및 해안선 | 138,160 | `0e386ce0d8b4b73b567d1dbbe68fbbdb56c0733d3c8c73d9e8eb3c56a8dbb83e` |
| `ne_110m_lakes.geojson` | Natural Earth 1:110m Lakes | 오대호, 카스피해, 빅토리아호 등 주요 수계 | 36,648 | `7ff15b6d9be7e4bb004cf8a48ef84cf9dc3175bb876ef48ae6673bfba80bfd03` |
| `ne_110m_admin_0_boundary_lines_land.geojson` | Natural Earth 1:110m Admin-0 | 전세계 국가 간 국경선 | 340,010 | `fefd784d193cf9cf2996e38e658ec3421396a583e7ffb2a0ebc7217e65158656` |
| `ne_110m_admin_1_states_provinces_lines.geojson` | Natural Earth 1:110m Admin-1 | 미국 50개 주, 캐나다 주/도 경계선 | 60,552 | `d12aa52a1ba763158022b78d2b7754b20dd676a6cfb16279f048d08595856fc8` |
| `ne_110m_populated_places_simple.geojson` | Natural Earth 1:110m Populated Places | 전세계 주요 도시 및 지역 거점 | 166,071 | `fe7df77e5e3057e937d10e0513e9a59b665dfd12a6730ef33fe73e35ef414757` |

---

## 3. 베이스맵 생성 및 검증 파이프라인

1. **생성 명령어**:
   ```bash
   python tools/generate_basemap.py
   ```
2. **무결성 검증 명령어**:
   ```bash
   python tools/generate_basemap.py --check
   ```
3. **최종 출력 에셋**:
   * 경로: `app/src/main/assets/basemap_world.json`
   * 용량: 약 461 KB
   * SHA-256: `ed086f9de291c3d45b87126c1d0e266ced2f05f113ce56160e6eb0a20e78c651`
   * 구성: 육지 다각형 (`land`), 주요 호수 (`lakes`), 국가 경계선 (`ADMIN_0`), 주/도 경계선 (`ADMIN_1`), 전세계 주요 도시 (`places`)
