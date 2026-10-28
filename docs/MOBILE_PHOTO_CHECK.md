# 안드로이드 실기기 사진 확인

## 범위

신뢰할 수 있는 같은 개인 Wi-Fi에서 Android Chrome으로 파일 선택·처리 상태·방향·화질·저장 후 유지를 확인한다. 반응형 iframe 검사는 실제 기기 검증을 대체하지 않는다. 테스트용 이메일 계정과 공개해도 괜찮은 사진만 사용한다.

HTTP 기반의 개인 개발망 테스트이며 운영 배포 절차가 아니다. 공용 Wi-Fi에서 로그인하거나 실제 개인정보를 넣지 않는다. 공유기 포트 포워딩, 방화벽 전체 해제, Vite allowedHosts 전체 허용은 하지 않는다.

## 주소 설정

1. `ipconfig`로 실제 Wi-Fi 어댑터의 사설 IPv4를 확인한다. WSL/가상 어댑터 주소나 유선 공인 IP를 사용하지 않는다.
2. 아래 예시는 PC Wi-Fi IP가 **192.168.0.10**인 경우다. 모든 예시 주소를 실제 Wi-Fi IP로 바꾼다.
3. `frontend/.env.mobile.local`에 공개 API 주소만 넣는다. 이 파일은 Git에서 제외되며 Vite `--mode mobile`에서만 읽는다.

```dotenv
VITE_API_BASE_URL=http://192.168.0.10:8080
```

`VITE_` 값은 브라우저에 노출된다. R2 키, JWT 키, OAuth 비밀값을 넣지 않는다. 일반 `.env.local`을 LAN 전용 주소로 덮어쓰지 않는다. 일반 dev/build는 기존 localhost 기본값을 유지하며, 운영 빌드에는 별도의 실제 API Origin 설정이 필요하다. 모바일 모드 산출물을 운영에 배포하지 않는다.

```powershell
git check-ignore -v frontend/.env.mobile.local
```

## R2 CORS

기존 localhost Origin은 유지하고 정확한 Wi-Fi 프론트 Origin만 추가한다. 전체 LAN이나 `*`를 허용하지 않는다. IP가 바뀌면 이 설정과 env/실행 명령을 함께 갱신한다.

```json
[
  {
    "AllowedOrigins": ["http://localhost:5173", "http://192.168.0.10:5173"],
    "AllowedMethods": ["PUT", "GET", "HEAD"],
    "AllowedHeaders": ["Content-Type"],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 3600
  }
]
```

## 실행

먼저 기존 백엔드와 프론트 개발 서버를 해당 터미널/IDE에서 중지한다. 포트 충돌 시 모든 Java/Node 프로세스를 종료하지 말고 기존 invi 서버만 확인한다. 아래 두 터미널은 실행 중 열린 채로 둔다.

### 터미널 1: 백엔드

```powershell
.\backend\gradlew.bat -p backend bootRun --args="--spring.profiles.active=local --server.address=192.168.0.10 --invi.frontend.base-url=http://192.168.0.10:5173"
```

- local 프로필로 gitignored R2 설정을 읽는다.
- `server.address`로 Wi-Fi IP 하나에만 바인딩한다. 공인 IP가 있는 다른 어댑터에 노출하지 않는다.
- CORS는 지정한 `invi.frontend.base-url`의 정확한 Origin만 허용한다. 기본값은 localhost다.
- `Started InviApiApplication`을 확인한다. `BUILD SUCCESSFUL`만으로 기동 성공으로 판단하지 않는다.

### 터미널 2: 프론트

```powershell
npm --prefix frontend run dev -- --mode mobile --host 192.168.0.10 --port 5173 --strictPort
```

`0.0.0.0` 대신 확인한 사설 Wi-Fi IP만 지정한다. `--strictPort` 때문에 5173이 점유되면 다른 포트로 조용히 넘어가지 않고 중단된다. mobile 모드 로그인 화면은 이메일 로그인만 표시한다. 소셜 로그인 제공자의 localhost 콜백 설정은 변경하지 않는다.

## 접속과 확인

PC Chrome에서 먼저 `http://192.168.0.10:5173`과 API 응답을 확인한 뒤 같은 Wi-Fi의 Android Chrome에서 같은 주소를 연다. 휴대폰에서 localhost는 PC가 아니라 휴대폰 자신을 뜻한다.

1. 기존 이메일 계정으로 로그인한다. 기기·Origin이 바뀌면 다시 로그인이 필요하다.
2. 공개 가능한 인물/웨딩 사진의 세로·가로 원본을 각각 선택한다. HEIC 파일이 없다면 Android에서 HEIC 검증까지 완료했다고 기록하지 않는다.
3. 처리 표시, 파일 선택 및 저장·공개 중복 방지, 화면 멈춤 여부를 확인한다.
4. 사진이 옆으로 돌아가거나 얼굴이 과하게 잘리지 않는지 확인한다. 대표 사진은 템플릿의 고정 비율에 object-fit:cover로 표시되므로 원본 비율과 화면 크롭은 구분한다.
5. 같은 휴대폰의 사진 앱과 비교한다. 밝기/블루라이트·색상 보정 조건을 가급적 맞추고 피부색, 하얀 옷, 머리카락/드레스 디테일을 확인한다. 육안 비교를 색 정확도 측정이라고 부르지 않는다.
6. 저장 → 새로고침 → 하객 링크 열기에서 동일 사진을 확인한다. 공개된 초대장은 저장하면 같은 하객 URL에 바로 반영된다.
7. 가능하면 새 사진 처리 실패 시 기존 사진 유지 및 재시도를 확인한다. 테스트 후 민감한 개인 사진을 남기지 않는다.

LAN HTTP에서는 자동 클립보드 복사가 차단될 수 있다. 이 경우 공유 주소를 직접 복사한다. 실제 기기 모델, Android/Chrome 버전, 파일 형식·크기·방향, 성공/오류를 기록한다. 비밀번호나 토큰을 기록하지 않는다.

## 안 열릴 때

- **Android 17에서 Chrome만 실패하는 경우**: 다른 브라우저는 열리는데 Chrome의 일반/시크릿 탭 모두 `ERR_TOO_MANY_RETRIES` 등 연결 오류가 나면 OS 앱 권한을 확인한다. 휴대폰 설정 → 앱(애플리케이션) → Chrome → 권한 → 근처 기기/주변 기기(Nearby devices)가 차단돼 있으면, 신뢰하는 개인 Wi-Fi 테스트에 필요한 권한을 허용하고 Chrome을 닫았다가 다시 연다. Android 17의 로컬 네트워크 권한은 Nearby devices 그룹에 속한다. 이 오류 코드만으로 권한 문제라고 단정하지는 않는다. 해당 항목이 없거나 이미 허용돼 있으면 별도 원인을 확인한다. 서버 CORS 확대·방화벽 전체 해제·브라우저 보안 플래그 해제로 해결하지 않는다. [Android 공식 안내](https://developer.android.com/privacy-and-security/local-network-permission)
- PC에서도 안 열림: 두 서버가 실행 중인지, IP·포트·mobile 모드가 맞는지 확인한다.
- PC는 열리지만 휴대폰만 안 열림: 같은 Wi-Fi인지, 게스트망/기기 격리가 켜져 있는지, Windows의 신뢰하는 Wi-Fi 프로필과 방화벽 허용 범위를 확인한다. 공용 네트워크 전체 허용이나 방화벽 해제를 해결책으로 사용하지 않는다.
- 화면은 열리지만 로그인/API 실패: API 주소가 휴대폰 localhost가 아닌 PC Wi-Fi IP인지, 백엔드의 프론트 Origin이 같은 IP·5173인지 확인한다.
- 업로드만 실패: R2 CORS Origin이 휴대폰 브라우저 주소의 scheme/IP/port와 정확히 일치하는지 확인한다.
- IP가 변경됨: mobile env, 두 실행 명령, R2 Origin을 함께 갱신한다. 테스트가 끝난 뒤 오래된 R2 LAN Origin은 제거한다.

## 검증과 복구

```powershell
npm --prefix frontend test
npm --prefix frontend run lint
npm --prefix frontend run build
.\backend\gradlew.bat -p backend test --console=plain
```

기본 localhost 작업으로 돌아갈 때는 LAN 서버 둘을 중지하고 다음처럼 실행한다. mobile 모드 파일은 기본 모드에서 읽지 않으므로 삭제 없이도 기본 API 주소로 돌아간다.

```powershell
.\backend\gradlew.bat -p backend bootRun --args="--spring.profiles.active=local --server.address=127.0.0.1 --invi.frontend.base-url=http://localhost:5173"
```

```powershell
npm --prefix frontend run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

## 2026-10-28 확인 기록

- PC Wi-Fi Origin에서 프론트·공개 API·R2 이미지 읽기 200을 확인했다. 같은 HTTP LAN Origin에서 실제 Worker JPG→WebP 변환도 성공했다.
- 사용자 Android 17 / Chrome 156.0.8078.47에서 일반 탭과 시크릿 탭 모두 연결 오류가 발생했다. 5173 프론트뿐 아니라 8080 health 주소도 열리지 않았다.
- 사용자 보고: 카카오톡 인앱 브라우저에서 대표 사진 교체가 성공했고, 네이버·삼성 브라우저에서는 페이지가 열렸다.
- 사용자 보고: Chrome 앱의 근처 기기 권한을 허용하자 접속이 정상화됐고, Chrome에서도 대표 사진 변경이 성공했다. 해결을 위해 서버 허용 범위를 확대하거나 Chrome 보안 기능을 해제하지 않았다.
- 이후 공개 API 200 및 대표 사진 데이터 존재를 재확인했다. 원본 대비 사진 방향·색감의 육안 판정과 최종 test/lint/build 및 backend test 결과는 별도 사용자 확인 후 완료로 기록한다.
