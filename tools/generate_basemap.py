#!/usr/bin/env python3
"""
tools/generate_basemap.py

Generates multi-resolution offline vector world basemap for Traveler.
Two detail levels:
  - WORLD (110m): For zoomed-out continental views
  - REGIONAL (10m): For playback-level city/coastal views

Source: Natural Earth Public Domain GeoJSON (1:110m + 1:10m)
License: Public Domain (CC0 1.0 Universal)
Output: app/src/main/assets/basemap_world.json (110m)
        app/src/main/assets/basemap_regional.json (10m, simplified)

NO hand-drawn water polygons. ALL geometry from reproducible source data.
"""

import json
import hashlib
import os
import sys

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
SOURCES_DIR = os.path.join(os.path.dirname(__file__), "basemap_sources")
OUTPUT_WORLD = os.path.normpath(os.path.join(REPO_ROOT, "app", "src", "main", "assets", "basemap_world.json"))
OUTPUT_REGIONAL = os.path.normpath(os.path.join(REPO_ROOT, "app", "src", "main", "assets", "basemap_regional.json"))

def round_coords(coords, precision=3):
    if isinstance(coords[0], (int, float)):
        return [round(coords[0], precision), round(coords[1], precision)]
    return [round_coords(c, precision) for c in coords]

def simplify_line(points, tolerance=0.10):
    """Douglas-Peucker line simplification."""
    if len(points) <= 2:
        return points
    p1 = points[0]
    p2 = points[-1]
    dx = p2[0] - p1[0]
    dy = p2[1] - p1[1]
    line_len_sq = dx * dx + dy * dy

    max_dist_sq = 0.0
    index = 0
    for i in range(1, len(points) - 1):
        p = points[i]
        if line_len_sq == 0.0:
            dist_sq = (p[0] - p1[0]) ** 2 + (p[1] - p1[1]) ** 2
        else:
            t = max(0.0, min(1.0, ((p[0] - p1[0]) * dx + (p[1] - p1[1]) * dy) / line_len_sq))
            proj_x = p1[0] + t * dx
            proj_y = p1[1] + t * dy
            dist_sq = (p[0] - proj_x) ** 2 + (p[1] - proj_y) ** 2
        if dist_sq > max_dist_sq:
            max_dist_sq = dist_sq
            index = i

    tol_sq = tolerance * tolerance
    if max_dist_sq > tol_sq:
        left = simplify_line(points[:index + 1], tolerance)
        right = simplify_line(points[index:], tolerance)
        return left[:-1] + right
    else:
        return [p1, p2]


def process_polygons(geojson_data, tolerance, precision, poly_type):
    """Extract and simplify polygons from GeoJSON."""
    polygons = []
    for feat in geojson_data.get("features", []):
        geom = feat.get("geometry")
        if geom is None:
            continue
        gtype = geom.get("type", "")
        props = feat.get("properties", {})
        name = props.get("name", props.get("name_en", props.get("featurecla", poly_type.title())))

        raw_polys = []
        if gtype == "Polygon":
            raw_polys = [geom.get("coordinates", [])]
        elif gtype == "MultiPolygon":
            raw_polys = geom.get("coordinates", [])

        for poly in raw_polys:
            simplified_rings = []
            for ring in poly:
                s_ring = simplify_line(ring, tolerance=tolerance)
                if len(s_ring) >= 3:
                    simplified_rings.append(round_coords(s_ring, precision))
            if simplified_rings:
                polygons.append({
                    "name": name,
                    "type": poly_type,
                    "rings": simplified_rings
                })
    return polygons


def process_lines(geojson_data, tolerance, precision, boundary_type):
    """Extract and simplify boundary lines from GeoJSON."""
    lines = []
    for feat in geojson_data.get("features", []):
        geom = feat.get("geometry")
        if geom is None:
            continue
        gtype = geom.get("type", "")
        props = feat.get("properties", {})
        name = props.get("name") or props.get("name_en") or props.get("featurecla") or boundary_type

        raw_lines = []
        if gtype == "LineString":
            raw_lines = [geom.get("coordinates", [])]
        elif gtype == "MultiLineString":
            raw_lines = geom.get("coordinates", [])

        for line in raw_lines:
            pts = simplify_line(line, tolerance=tolerance)
            if len(pts) >= 2:
                lines.append({
                    "name": name,
                    "boundaryType": boundary_type,
                    "points": round_coords(pts, precision)
                })
    return lines


def process_places(geojson_data, precision=4):
    """Extract populated places from GeoJSON."""
    places = []
    for feat in geojson_data.get("features", []):
        geom = feat.get("geometry", {})
        props = feat.get("properties", {})
        coords = geom.get("coordinates", [])
        if coords and len(coords) >= 2:
            pname = props.get("name") or props.get("name_en") or props.get("nameascii")
            if pname:
                places.append({
                    "name": pname,
                    "lat": round(coords[1], precision),
                    "lng": round(coords[0], precision),
                    "scalerank": int(props.get("scalerank", 5))
                })
    return places


def load_geojson(filepath):
    """Load a GeoJSON file, return parsed data."""
    with open(filepath, "r", encoding="utf-8") as f:
        return json.load(f)


def generate_world_basemap():
    """Generate low-detail 110m basemap for zoomed-out views."""
    source_files = {
        "land": "ne_110m_land.geojson",
        "lakes": "ne_110m_lakes.geojson",
        "admin_0": "ne_110m_admin_0_boundary_lines_land.geojson",
        "admin_1": "ne_110m_admin_1_states_provinces_lines.geojson",
        "places": "ne_110m_populated_places_simple.geojson"
    }

    sources_meta = {}
    for key, fname in source_files.items():
        fpath = os.path.join(SOURCES_DIR, fname)
        if not os.path.exists(fpath):
            raise FileNotFoundError(f"Missing: {fpath}")
        with open(fpath, "rb") as f:
            content = f.read()
            sources_meta[fname] = {
                "size_bytes": len(content),
                "sha256": hashlib.sha256(content).hexdigest()
            }

    # 110m: tolerance=0.15, precision=2 (existing behavior, no hand-drawn water)
    land = process_polygons(load_geojson(os.path.join(SOURCES_DIR, source_files["land"])), 0.15, 2, "land")
    lakes = process_polygons(load_geojson(os.path.join(SOURCES_DIR, source_files["lakes"])), 0.12, 2, "lake")
    # NO regional_water_bodies — all geometry from source data only
    admin0 = process_lines(load_geojson(os.path.join(SOURCES_DIR, source_files["admin_0"])), 0.15, 2, "ADMIN_0")
    admin1 = process_lines(load_geojson(os.path.join(SOURCES_DIR, source_files["admin_1"])), 0.15, 2, "ADMIN_1")
    places = process_places(load_geojson(os.path.join(SOURCES_DIR, source_files["places"])))

    return {
        "dataset": "Traveler Offline Vector Basemap (World / 110m)",
        "license": "Public Domain (CC0 1.0 Universal)",
        "source": "Natural Earth 1:110m",
        "detail": "world",
        "sources_meta": sources_meta,
        "polygons": land + lakes,
        "boundaries": admin0 + admin1,
        "places": places
    }


def generate_regional_basemap():
    """Generate high-detail 10m basemap for playback-level views."""
    source_files = {
        "land": "ne_10m_land.geojson",
        "lakes": "ne_10m_lakes.geojson",
        "admin_1": "ne_10m_admin_1_states_provinces_lines.geojson",
        "places": "ne_10m_populated_places_simple.geojson"
    }

    # Check availability
    for key, fname in source_files.items():
        fpath = os.path.join(SOURCES_DIR, fname)
        if not os.path.exists(fpath):
            print(f"  [WARN] Missing 10m source: {fname} — run tools/download_ne_10m.py first")
            return None

    sources_meta = {}
    for key, fname in source_files.items():
        fpath = os.path.join(SOURCES_DIR, fname)
        with open(fpath, "rb") as f:
            content = f.read()
            sources_meta[fname] = {"size_bytes": len(content)}

    # 10m: high-fidelity coastlines (tolerance=0.003 degrees, 4 decimals) preserving islands and harbors
    print("  Processing 10m land (this may take a moment)...")
    land = process_polygons(load_geojson(os.path.join(SOURCES_DIR, source_files["land"])), 0.003, 4, "land")
    print(f"    Land polygons: {len(land)}")

    print("  Processing 10m lakes...")
    lakes = process_polygons(load_geojson(os.path.join(SOURCES_DIR, source_files["lakes"])), 0.006, 4, "lake")
    print(f"    Lake polygons: {len(lakes)}")

    # admin_1: simplified boundary lines to maintain compact asset size
    print("  Processing 10m admin-1 boundaries...")
    admin1 = process_lines(load_geojson(os.path.join(SOURCES_DIR, source_files["admin_1"])), 0.05, 3, "ADMIN_1")
    admin1 = [b for b in admin1 if len(b["points"]) >= 3]
    print(f"    Boundary lines: {len(admin1)}")

    print("  Processing 10m populated places...")
    places = [p for p in process_places(load_geojson(os.path.join(SOURCES_DIR, source_files["places"])), precision=4) if p["scalerank"] <= 4]
    print(f"    Places: {len(places)}")

    total_points = sum(sum(len(ring) for ring in p["rings"]) for p in land + lakes)
    total_points += sum(len(b["points"]) for b in admin1)
    print(f"    Total geometry points: {total_points}")

    return {
        "dataset": "Traveler Offline Vector Basemap (Regional / 10m)",
        "license": "Public Domain (CC0 1.0 Universal)",
        "source": "Natural Earth 1:10m",
        "detail": "regional",
        "sources_meta": sources_meta,
        "polygons": land + lakes,
        "boundaries": admin1,
        "places": places
    }


def main():
    if "--check" in sys.argv:
        if not os.path.exists(OUTPUT_WORLD):
            print(f"FAIL: {OUTPUT_WORLD} does not exist", file=sys.stderr)
            sys.exit(1)
        data = generate_world_basemap()
        generated_json = json.dumps(data, indent=2) + "\n"
        with open(OUTPUT_WORLD, "r", encoding="utf-8") as f:
            committed_json = f.read()
        if committed_json.strip() != generated_json.strip():
            print(f"FAIL: Committed basemap differs from generated output", file=sys.stderr)
            sys.exit(1)
        sha256 = hashlib.sha256(committed_json.encode("utf-8")).hexdigest()
        print(f"Basemap check OK (SHA-256: {sha256})")
        sys.exit(0)

    # Generate WORLD basemap (110m)
    print("=" * 50)
    print("Generating WORLD basemap (110m)...")
    world_data = generate_world_basemap()
    world_json = json.dumps(world_data, indent=2) + "\n"
    os.makedirs(os.path.dirname(OUTPUT_WORLD), exist_ok=True)
    with open(OUTPUT_WORLD, "w", encoding="utf-8") as f:
        f.write(world_json)
    print(f"  Written: {OUTPUT_WORLD} ({len(world_json):,} bytes)")
    print(f"  SHA-256: {hashlib.sha256(world_json.encode('utf-8')).hexdigest()}")

    # Generate REGIONAL basemap (10m)
    print()
    print("=" * 50)
    print("Generating REGIONAL basemap (10m)...")
    regional_data = generate_regional_basemap()
    if regional_data is not None:
        regional_json = json.dumps(regional_data, indent=2) + "\n"
        with open(OUTPUT_REGIONAL, "w", encoding="utf-8") as f:
            f.write(regional_json)
        print(f"  Written: {OUTPUT_REGIONAL} ({len(regional_json):,} bytes)")
        print(f"  SHA-256: {hashlib.sha256(regional_json.encode('utf-8')).hexdigest()}")
    else:
        print("  [SKIP] 10m sources not available")

    print()
    print("Done.")


if __name__ == "__main__":
    main()
