# SearXNG Runner

안드로이드 폰에서 Termux 를 이용해 SearXNG(오픈소스 메타검색엔진)를 자동 설치·실행·관리하는 앱입니다.

## 중요한 전제

- 앱은 SearXNG 를 직접 실행하지 않습니다. 안드로이드 10 이상의 백그라운드 실행 제한 때문입니다.
- Termux 의 `RUN_COMMAND` 인텐트로 명령을 전달하고, Termux 안에서 `proot-distro` 의 Debian 환경에 SearXNG 를 설치합니다.
- Termux 는 반드시 **F-Droid 또는 GitHub 배포판**이어야 합니다. Play 스토어 판은 동작하지 않습니다.

## 빌드

안드로이드 스튜디오 없이 GitHub Actions 로 빌드합니다.

1. 이 폴더의 파일을 GitHub 저장소에 올립니다.
2. `Actions` 탭 → `Build Debug APK` → 최신 실행 → `SearXNG-Runner-debug-apk` 다운로드.
3. APK 를 폰에 옮겨 설치합니다.

자세한 절차는 `초보자-가이드.md` 를 보세요.

## 사용한 버전

| 항목 | 버전 |
| --- | --- |
| Kotlin | 2.2.21 |
| Android Gradle Plugin | 8.13.2 |
| Gradle (Actions 에서 설치) | 8.14.6 |
| compileSdk / targetSdk | 36 |
| minSdk | 26 |
| JDK | 17 |

외부 라이브러리는 사용하지 않았습니다(AndroidX·Compose 미사용, 안드로이드 기본 위젯만 사용).

## 화면 구성

- 설치 안내(1회): Termux 설치 → 외부 앱 제어 허용 명령 복사 → 권한 허용
- 자동 설치, 시작, 중지, 재시작, JSON 테스트
- 접속 주소(`http://와이파이IP:8080`) 표시와 복사
- 배터리 최적화 제외 요청

## 파일 구조

```
app/src/main/assets/searxng-install.sh   설치 스크립트(여기만 고치면 설치 명령 변경 가능)
app/src/main/assets/searxng-control.sh   시작/중지/재시작 스크립트
app/src/main/java/com/searxngrunner/app/ 앱 코드 8개
```
