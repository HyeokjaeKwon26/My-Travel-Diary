#!/usr/bin/env python3
"""
tools/download_ne_10m.py

Downloads Natural Earth 1:10m source GeoJSON files for the Traveler basemap upgrade.
These are development-time downloads only - the generated basemap asset is bundled in the APK.
No runtime network access.

Source: https://naciscdn.org/naturalearth/
License: Public Domain (CC0 1.0 Universal)
"""

import os
import sys
import urllib.request
import zipfile
import json
import glob

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
SOURCES_DIR = os.path.join(SCRIPT_DIR, "basemap_sources")
DOWNLOAD_DIR = os.path.join(SCRIPT_DIR, "basemap_sources", "_downloads")

# Natural Earth 10m datasets we need
DATASETS = {
    "ne_10m_land": "https://naciscdn.org/naturalearth/10m/physical/ne_10m_land.zip",
    "ne_10m_lakes": "https://naciscdn.org/naturalearth/10m/physical/ne_10m_lakes.zip",
    "ne_10m_admin_0_boundary_lines_land": "https://naciscdn.org/naturalearth/10m/cultural/ne_10m_admin_0_boundary_lines_land.zip",
    "ne_10m_admin_1_states_provinces_lines": "https://naciscdn.org/naturalearth/10m/cultural/ne_10m_admin_1_states_provinces_lines.zip",
    "ne_10m_populated_places_simple": "https://naciscdn.org/naturalearth/10m/cultural/ne_10m_populated_places_simple.zip",
}

def download_and_extract(name, url):
    """Download zip, extract shapefile, convert to GeoJSON."""
    zip_path = os.path.join(DOWNLOAD_DIR, f"{name}.zip")
    extract_dir = os.path.join(DOWNLOAD_DIR, name)
    geojson_path = os.path.join(SOURCES_DIR, f"{name}.geojson")

    if os.path.exists(geojson_path):
        print(f"  [SKIP] {name}.geojson already exists")
        return True

    os.makedirs(DOWNLOAD_DIR, exist_ok=True)
    os.makedirs(extract_dir, exist_ok=True)

    # Download
    if not os.path.exists(zip_path):
        print(f"  Downloading {name}...")
        try:
            urllib.request.urlretrieve(url, zip_path)
        except Exception as e:
            print(f"  [ERROR] Download failed: {e}")
            return False

    # Extract
    print(f"  Extracting {name}...")
    with zipfile.ZipFile(zip_path, 'r') as z:
        z.extractall(extract_dir)

    # Find shapefile and convert to GeoJSON
    shp_files = glob.glob(os.path.join(extract_dir, "**", f"{name}.shp"), recursive=True)
    if not shp_files:
        # Try ogr2ogr if available, otherwise look for existing geojson
        existing_geojson = glob.glob(os.path.join(extract_dir, "**", "*.geojson"), recursive=True)
        if existing_geojson:
            import shutil
            shutil.copy2(existing_geojson[0], geojson_path)
            print(f"  [OK] Copied existing GeoJSON")
            return True

        print(f"  [INFO] Shapefile found, converting with ogr2ogr...")
        shp = shp_files[0] if shp_files else None
        if shp:
            ret = os.system(f'ogr2ogr -f GeoJSON "{geojson_path}" "{shp}"')
            if ret == 0:
                print(f"  [OK] Converted to GeoJSON")
                return True

        print(f"  [ERROR] No shapefile or GeoJSON found for {name}")
        print(f"  You may need to install GDAL/ogr2ogr or manually convert:")
        print(f"    ogr2ogr -f GeoJSON {geojson_path} <shapefile>")
        return False

    # Convert shapefile to GeoJSON using ogr2ogr
    shp = shp_files[0]
    ret = os.system(f'ogr2ogr -f GeoJSON "{geojson_path}" "{shp}"')
    if ret == 0:
        print(f"  [OK] Converted {name} to GeoJSON")
        return True
    else:
        print(f"  [ERROR] ogr2ogr conversion failed for {name}")
        print(f"  Install GDAL: pip install gdal  or  conda install gdal")
        return False


def main():
    print("Natural Earth 10m Data Downloader")
    print("=" * 50)

    os.makedirs(SOURCES_DIR, exist_ok=True)
    success_count = 0

    for name, url in DATASETS.items():
        print(f"\n{name}:")
        if download_and_extract(name, url):
            success_count += 1

    print(f"\n{'=' * 50}")
    print(f"Downloaded: {success_count}/{len(DATASETS)}")

    if success_count < len(DATASETS):
        print("\nSome downloads failed. You can also manually download from:")
        print("  https://www.naturalearthdata.com/downloads/10m-physical-vectors/")
        print("  https://www.naturalearthdata.com/downloads/10m-cultural-vectors/")
        print("\nConvert .shp to .geojson with:")
        print("  ogr2ogr -f GeoJSON output.geojson input.shp")
        sys.exit(1)


if __name__ == "__main__":
    main()
