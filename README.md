# YoutubeMp3
<img width="1982" height="1388" alt="image" src="https://github.com/user-attachments/assets/aadf1afc-0113-49cb-9246-098ac0df664c" />

YouTube 영상을 검색하고 MP3 오디오로 추출하는 Windows WPF 데스크톱 앱입니다.

## 기능

- 검색어로 YouTube 영상 검색
- 검색 결과 클릭 시 URL 자동 입력
- URL 직접 입력 후 MP3 추출
- 다운로드 진행률 및 상태 표시
- 다운로드 완료 후 폴더 열기 버튼으로 결과물 바로 확인
- `yt-dlp`, `FFmpeg`, `Deno`(JS 런타임) 최초 실행 시 자동 다운로드 — 별도 설치 불필요 (`yt-dlp`는 1주일마다 새 버전을 확인해 자동 업데이트)

MP3는 `내 음악\YoutubeMp3` 폴더에 저장됩니다.

## 다운로드

별도 설치 없이 실행 파일 하나로 동작하는 portable 버전을 [Releases](../../releases)에서 받을 수 있습니다.
압축을 풀고 `YoutubeMp3.exe`를 실행하세요. (인터넷 연결 필요 — 최초 실행 시 `yt-dlp`/`FFmpeg`/`Deno`를 자동으로 내려받습니다.)

### 안드로이드

[Releases](../../releases)에서 `YoutubeMp3-*-android-arm64.apk`를 받아 설치합니다(구형 32비트 폰은 `android-armv7`). Android 10 이상이 필요합니다.

- 검색 또는 주소 입력, 유튜브 앱의 공유 메뉴로 MP3 받기
- 파일은 `Music/YoutubeMp3`에 저장되고 앱의 「내 음악」에서 재생
- 「재생목록」에서 기기의 음악 파일로 목록을 만들어 연속 재생
- 오른쪽 아래 메뉴에서 사주 (게임은 별도 앱 `mobile`, 한글·숫자·알파벳 공부는 별도 앱 `kids-hangul`로 분리)
- 앱을 켤 때 새 버전이 있으면 앱 안에서 받아 업데이트 (처음 한 번은 「이 출처의 앱 설치 허용」 필요)
- `yt-dlp`는 켤 때 하루 한 번 자동 업데이트

## 프로젝트 구조

| 프로젝트 | 설명 |
| --- | --- |
| `YoutubeMp3` | 앱 진입점, DI 컨테이너 구성 |
| `YoutubeMp3.Forms` | View / ViewModel (MVVM) |
| `YoutubeMp3.Main` | 검색/다운로드 서비스 (`YoutubeDLSharp` 래핑) |
| `YoutubeMp3.Support` | 커스텀 윈도우 크롬(타이틀바 버튼 등) 공용 컨트롤 |
| `android/` | 안드로이드 앱 (Kotlin, `youtubedl-android`) — 데스크톱 코드와 공유하지 않음 |

## 개발 환경에서 빌드/실행

```powershell
dotnet build YoutubeMp3.sln
dotnet run --project YoutubeMp3/YoutubeMp3.csproj
```

- .NET 8 SDK (`net8.0-windows`) 필요
- MVVM: `CommunityToolkit.Mvvm`
- 다운로드: `YoutubeDLSharp` (yt-dlp 래퍼)

## 릴리스

`v*` 형태의 태그(예: `v1.0.0`)를 push하면 GitHub Actions가 win-x64 self-contained 단일 실행 파일과 안드로이드 APK를 빌드해 자동으로 GitHub Release에 올립니다.

```bash
git tag v1.0.0
git push origin v1.0.0
```
