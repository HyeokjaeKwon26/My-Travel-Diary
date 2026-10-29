"""Run targeted emulator integration tests against the locally built debug APKs."""
import argparse
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ.get("ANDROID_HOME", str(Path.home() / "AppData/Local/Android/Sdk")))
CLASSES = [
    "com.traveler.core.media.LibraryEnhancementsAndroidTest",
    "com.traveler.feature.video.VideoClipAndroidTest",
    "com.traveler.feature.video.BackgroundVideoAndroidTest",
    "com.traveler.core.media.MediaMigrationAndroidTest",
    "com.traveler.core.media.PortableMediaAndroidTest",
    "com.traveler.core.update.UpdateAndroidTest",
    "com.traveler.feature.update.UpdateUiAndroidTest",
    "com.traveler.feature.update.StartupUpdateAndroidTest",
    "com.traveler.core.media.TripMediaUiAndroidTest",
]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--edition", choices=["2D", "3D"], required=True)
    parser.add_argument("--serial", default="emulator-5580")
    parser.add_argument("--classes", default=",".join(CLASSES))
    parser.add_argument("--skip-install", action="store_true")
    args = parser.parse_args()
    project = ROOT if args.edition == "2D" else ROOT.parent / "Traveler3D"
    package = "com.traveler.next" if args.edition == "2D" else "com.traveler.threed.next"
    adb = [str(SDK / "platform-tools/adb.exe"), "-s", args.serial]
    if not args.skip_install:
        for relative in ["app/build/outputs/apk/debug/app-debug.apk", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"]:
            subprocess.run(adb + ["install", "-r", str(project / relative)], check=True)
        subprocess.run([sys.executable, str(project / "tools/prepare_update_test_fixtures.py"), "--sdk", str(SDK),
                        "--serial", args.serial, "--application-id", package], check=True)
    run = subprocess.run(adb + ["shell", "am", "instrument", "-w", "-r", "-e", "class", args.classes,
                                package + ".test/androidx.test.runner.AndroidJUnitRunner"], capture_output=True,
                         text=True, encoding="utf-8", errors="replace", timeout=1200)
    log = run.stdout + "\n" + run.stderr
    destination = project / "artifacts/enhancement-android.log"
    destination.write_text(log, encoding="utf-8")
    print(log[-6000:])
    print(destination)
    if run.returncode or "OK (" not in log or "FAILURES!!!" in log or "Process crashed" in log:
        sys.exit(1)

if __name__ == "__main__":
    main()
