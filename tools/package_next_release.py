"""Package both signed New APKs locally; never publishes or includes signing credentials."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys

VERSION = "2.1.0"
ROOT = Path(__file__).resolve().parents[1]
DESTINATION = ROOT / "artifacts" / f"next-release-{VERSION}"
SDK = Path(os.environ.get("ANDROID_HOME", str(Path.home() / "AppData/Local/Android/Sdk")))
NOTES = """2.1.0
여러 여행의 사진 포함 백업, 복원 전 미리보기와 중복 확인, 휴지통을 추가했습니다.
백업·복원·사진 추가·영상 제작의 진행 상황과 중단 후 재시작을 작업 화면에서 확인할 수 있습니다.
여행에 사진·동영상을 추가하고 날짜·방문지를 지정할 수 있습니다.
동영상 구간 선택, 실제 프레임 삽입, 원본 소리와 배경음악의 음량 조절을 지원합니다.
3D 앱에 카메라 전환 개선, 화질·절전 설정과 오프라인 준비 상태를 추가했습니다.
실행 시 업데이트 확인과 선택형 Wi-Fi 자동 다운로드를 지원합니다. 설치는 Android 확인이 필요합니다.
기존 New 2.0.0에서 같은 앱·서명으로 업데이트합니다. 원래 앱은 그대로 유지됩니다.
"""

def main():
    for edition, project in [("2D", ROOT), ("3D", ROOT.parent / "Traveler3D")]:
        destination = DESTINATION / edition
        destination.mkdir(parents=True, exist_ok=True)
        source = project / "app/build/outputs/apk/release/app-release.apk"
        apk = destination / f"My-Travel-Diary-{edition}-New-{VERSION}.apk"
        notes = destination / "RELEASE_NOTES.txt"
        # Validate the exact source first, before replacing any deliverable.
        notes.write_text(NOTES, encoding="utf-8")
        subprocess.run([sys.executable, str(project / "tools/prepare_app_update.py"), str(source),
                        "--tag", f"v{VERSION}", "--notes", str(notes), "--sdk", str(SDK),
                        "--output", str(destination / "source-verification.json")], check=True)
        shutil.copy2(source, apk)
        subprocess.run([sys.executable, str(project / "tools/prepare_app_update.py"), str(apk),
                        "--tag", f"v{VERSION}", "--notes", str(notes), "--sdk", str(SDK),
                        "--output", str(destination / "update-next.json")], check=True)
        (destination / "source-verification.json").unlink()
        digest = hashlib.file_digest(apk.open("rb"), "sha256").hexdigest()
        (destination / "SHA256SUMS.txt").write_text(f"{digest}  {apk.name}\n", encoding="utf-8")
        print(f"{edition}: {apk} ({apk.stat().st_size:,} bytes)")

if __name__ == "__main__":
    main()
