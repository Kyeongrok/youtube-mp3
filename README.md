# YoutubeMp3

YouTube 영상을 검색하고 MP3 오디오로 추출하는 Windows WPF 데스크톱 앱입니다.

## 기능

- 검색어로 YouTube 영상 검색
- 검색 결과 클릭 시 URL 자동 입력
- URL 직접 입력 후 MP3 추출
- 다운로드 진행률 및 상태 표시
- 다운로드 완료 후 폴더 열기 버튼으로 결과물 바로 확인
- `yt-dlp`, `FFmpeg`, `Deno`(JS 런타임) 최초 실행 시 자동 다운로드 — 별도 설치 불필요

MP3는 `내 음악\YoutubeMp3` 폴더에 저장됩니다.

## 다운로드

별도 설치 없이 실행 파일 하나로 동작하는 portable 버전을 [Releases](../../releases)에서 받을 수 있습니다.
압축을 풀고 `YoutubeMp3.exe`를 실행하세요. (인터넷 연결 필요 — 최초 실행 시 `yt-dlp`/`FFmpeg`/`Deno`를 자동으로 내려받습니다.)

## 프로젝트 구조

| 프로젝트 | 설명 |
| --- | --- |
| `YoutubeMp3` | 앱 진입점, DI 컨테이너 구성 |
| `YoutubeMp3.Forms` | View / ViewModel (MVVM) |
| `YoutubeMp3.Main` | 검색/다운로드 서비스 (`YoutubeDLSharp` 래핑) |
| `YoutubeMp3.Support` | 커스텀 윈도우 크롬(타이틀바 버튼 등) 공용 컨트롤 |

## 개발 환경에서 빌드/실행

```powershell
dotnet build YoutubeMp3.sln
dotnet run --project YoutubeMp3/YoutubeMp3.csproj
```

- .NET 8 SDK (`net8.0-windows`) 필요
- MVVM: `CommunityToolkit.Mvvm`
- 다운로드: `YoutubeDLSharp` (yt-dlp 래퍼)

## 릴리스

`v*` 형태의 태그(예: `v1.0.0`)를 push하면 GitHub Actions가 win-x64 self-contained 단일 실행 파일을 빌드해 자동으로 GitHub Release에 올립니다.

```bash
git tag v1.0.0
git push origin v1.0.0
```
