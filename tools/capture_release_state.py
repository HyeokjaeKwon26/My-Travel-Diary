"""Capture synthetic release-emulator trip rows and owned-photo hashes for upgrade comparison.

Requires an adb-root-capable emulator. Never run against a personal phone.
"""
import argparse
from contextlib import closing
import hashlib
import json
from pathlib import Path
import sqlite3
import subprocess
import tempfile

SDK = Path.home() / "AppData/Local/Android/Sdk"
PACKAGES = ["com.traveler", "com.traveler.threed", "com.traveler.next", "com.traveler.threed.next"]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("--serial", default="emulator-5582")
    parser.add_argument("--compare", type=Path)
    args = parser.parse_args()
    assert args.serial.startswith("emulator-"), "Emulator only"
    adb = [str(SDK / "platform-tools/adb.exe"), "-s", args.serial]
    def call(*parts):
        return subprocess.check_output(adb + list(parts), text=True, encoding="utf-8", errors="replace")
    assert call("shell", "id").startswith("uid=0"), "Run adb root on the test emulator first"
    result = {}
    with tempfile.TemporaryDirectory(prefix="traveler-release-state-") as scratch:
        for package in PACKAGES:
            call("shell", "am", "force-stop", package)
            item = {"version": [s.strip() for s in call("shell", "dumpsys", "package", package).splitlines()
                                if "versionCode=" in s or "versionName=" in s], "tables": {}, "photos": {}}
            local = Path(scratch) / package
            local.mkdir()
            root = "/data/user/0/" + package
            for directory in ["databases", "files/trip_media"]:
                if call("shell", "test", "-d", root + "/" + directory, ";", "echo", "$?").strip() == "0":
                    subprocess.run(adb + ["pull", root + "/" + directory, str(local)], check=True, capture_output=True)
            db = local / "databases/traveler_database.db"
            if db.is_file():
                with closing(sqlite3.connect(db)) as connection:
                    tables = connection.execute("SELECT name FROM sqlite_master WHERE type='table'").fetchall()
                    for (table,) in tables:
                        if table.startswith("sqlite_") or table in ["android_metadata", "room_master_table"]:
                            continue
                        quoted = '"' + table.replace('"', '""') + '"'
                        rows = connection.execute("SELECT * FROM " + quoted).fetchall()
                        item["tables"][table] = sorted(json.dumps(row, ensure_ascii=False, default=repr) for row in rows)
            media = local / "trip_media"
            for file in sorted(media.rglob("*")):
                if file.is_file():
                    with file.open("rb") as stream:
                        item["photos"][file.relative_to(media).as_posix()] = hashlib.file_digest(stream, "sha256").hexdigest()
            result[package] = item
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    for package, item in result.items():
        print(package, item["version"], {name: len(rows) for name, rows in item["tables"].items()}, "photos", len(item["photos"]))
    if args.compare:
        before = json.loads(args.compare.read_text(encoding="utf-8"))
        for package, item in before.items():
            after = result[package]
            for table, rows in item["tables"].items():
                assert set(rows).issubset(after["tables"].get(table, [])), (package, table, "existing rows changed")
            for name, digest in item["photos"].items():
                assert after["photos"].get(name) == digest, (package, name, "photo changed")
        print("PASS: all existing trip rows and owned-photo hashes retained in all four apps")

if __name__ == "__main__":
    main()
