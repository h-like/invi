# 대표 사진 업로드

## 범위와 정책

- 이번 구현: 대표 사진 1장. 갤러리 다중 업로드·자르기는 후속 작업.
- 전체 정책: 대표 1장 + 갤러리 최대 20장(8~12장 권장). 기존 20장 초과 데이터는 지우지 않고 추가만 막는다.
- 원본: JPG/JPEG, PNG, WebP, HEIC/HEIF, 한 장당 30 × 1024 × 1024바이트 이하.
- 파일명·MIME뿐 아니라 실제 헤더와 디코딩 성공을 확인한다.
- Web Worker에서 HEIC를 디코딩하고 긴 변 최대 2,560px로 축소한다. 작은 사진은 확대하지 않는다.
- 기본 WebP 품질 0.88, 1MiB 초과 시 0.84, 0.80을 차례로 시험한다. 1MiB는 목표이지 강제 제한이 아니다. WebP 인코딩 미지원 시 JPEG로 대체한다.
- 메모리 보호: 디코딩된 이미지 최대 1억 픽셀. 원본 용량만으로 디코딩 메모리 사용량을 보장할 수 없으므로 처리 실패·시간 초과와 재시도를 제공한다.
- 원본은 R2에 업로드하지 않는다. 재인코딩 과정에서 원본 EXIF 위치정보 등을 전달하지 않는다. 원본 보관 서비스가 아니다.
- 브라우저의 Worker, createImageBitmap, OffscreenCanvas가 필요하다. 실제 iOS Safari/Android Chrome에서 HEIC 방향·색상·성능을 추가 검증한다.

## 처리 순서

파일 선택 → 형식·용량 확인 → Worker 변환·최적화 → presign → R2 PUT → attach → 공개 이미지 로드 확인 → `hero.content.coverImage` 변경 → 기존 수동 저장.

변환·업로드·공개 이미지 확인이 모두 성공하기 전에는 기존 사진을 바꾸지 않는다. 처리 중 또는 실패한 사진 선택이 남아 있으면 저장·공개를 막는다. 재시도하거나 ‘새 사진 선택 취소’를 눌러 기존 사진으로 돌아갈 수 있다. 업로드 성공 자체는 청첩장 저장 완료가 아니다.

서버 presign 요청에는 `fileName`, `contentType`, `type`, `contentLength`가 필요하다. HEIC는 브라우저에서 최적화되므로 R2에는 JPEG/PNG/WebP만 저장한다. 서버는 소유권, 크기, MIME, 청첩장별 키 경로를 검사한다. attach에서 R2 HEAD 및 제한된 Range GET으로 실제 업로드 여부·메타데이터·시그니처를 확인한다.

## R2 연결

비밀값은 gitignored `backend/src/main/resources/application-local.yml` 또는 해당 프로세스의 환경변수로 주입한다. 프론트의 VITE 변수나 채팅·Git에 비밀키를 넣지 않는다.

`application-local.yml`은 **local 프로필을 활성화**해야 읽힌다. 기존 8080 백엔드를 중지한 뒤 실행한다. `BUILD SUCCESSFUL`만으로 기동 성공을 판단하지 말고 `Started InviApiApplication` 로그를 확인한다.

```powershell
.\backend\gradlew.bat -p backend bootRun --args="--spring.profiles.active=local"
```

필요 항목:
- `invi.r2.account-id`
- `invi.r2.access-key-id`
- `invi.r2.secret-access-key`
- `invi.r2.bucket`
- `invi.r2.public-base-url`: 하객이 로그인 없이 읽을 수 있는 실제 HTTPS 이미지 루트 주소. presigned PUT URL이 아니다.

현재 설정은 `account-id/access-key-id/secret-access-key`에 환경변수 placeholder가 없으므로, 환경변수 사용 시 Spring의 `INVI_R2_ACCOUNT_ID`, `INVI_R2_ACCESS_KEY_ID`, `INVI_R2_SECRET_ACCESS_KEY`로 바인딩한다. bucket/public-base-url은 application.yml의 `R2_BUCKET` / `R2_PUBLIC_BASE_URL` placeholder도 사용할 수 있다.

최소 개발용 버킷 CORS:

```json
[
  {
    "AllowedOrigins": ["http://localhost:5173"],
    "AllowedMethods": ["PUT", "GET", "HEAD"],
    "AllowedHeaders": ["Content-Type"],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 3600
  }
]
```

운영 시 실제 프론트 Origin을 정확히 추가한다. 공개 이미지 도메인도 연결하고 브라우저에서 이미지 로딩을 확인한다. R2 CORS와 백엔드 API CORS는 별개다. 키는 해당 버킷에 필요한 Object Read & Write 범위로 제한한다. 설정 변경 후 백엔드를 재시작한다.

## 검증

```powershell
npm --prefix frontend test
npm --prefix frontend run build
npm --prefix frontend run lint
.\backend\gradlew.bat -p backend test --tests "com.invi.api.media.*" --console=plain
```

실연결 체크:
1. JPEG·PNG·WebP·HEIC 각각 업로드. 세로 HEIC 방향과 웨딩사진 피부·드레스 디테일 확인.
2. 30MiB 초과·지원하지 않는 형식·손상 파일 거절 확인.
3. 업로드 중 상태 및 중복 요청·저장·공개 방지 확인.
4. 네트워크 단절/잘못된 CORS/만료된 업로드 URL/공개 이미지 오류에서 기존 사진·다른 편집 내용 유지.
5. 재시도 성공 및 선택 취소 후 기존 사진으로 저장 가능.
6. 업로드 → 미리보기 → 수동 저장 → 새로고침 → 공개 URL에서 같은 사진 유지.
7. 공개된 초대장 사진 교체·저장 후 같은 공개 URL에서 최신 사진 표시.

## 후속 정리

- 실패·취소·미저장으로 남은 R2 객체 및 교체 전 자산을 정리하는 수명주기/배치가 아직 없다. 기존 사진을 먼저 삭제하면 공개 페이지가 깨지므로 이 구현에서는 삭제하지 않는다.
- presign 발급 기록을 영속 저장하거나 업로드 완료 시 불변 경로로 승격하는 방식은 아직 없다. 짧은 PUT URL 유효기간 동안 덮어쓰기 가능성이 있다.
- 헤더 시그니처 검사는 전체 이미지 디코딩이나 악성 파일 검사를 대체하지 않는다.

## 2026-10-28 로컬 실검증 기록

- R2 invi-media(APAC/Standard), localhost:5173 CORS, 개발용 r2.dev, 버킷 한정 Object Read & Write 키로 실제 연결했다. 비밀값은 gitignored 로컬 설정에만 저장했다.
- 합성 PNG 약 7.3MiB → WebP 약 23KiB, 1920×2560으로 최적화·PUT·attach·미리보기 연결 성공.
- 수동 저장 후 편집기 새로고침, 공유 및 하객 페이지의 이미지 주소 일치와 실제 로딩을 확인했다. 인증 헤더 없는 공개 API도 200으로 동일 이미지를 반환했다.
- 원본 30MiB+1 및 GIF 선택을 거절하고 기존 사진을 유지했다.
- 브라우저에서 R2 PUT 한 번에만 네트워크 실패를 주입했다. 기존 사진 유지 및 저장·공개 차단을 확인한 뒤 즉시 네이티브 XHR을 복구했다. 재시도는 실제 R2 업로드에 성공했다.
- 재시도 JPEG 약 43KiB → WebP 약 11KiB, 1200×1600. 저장 전까지 공개 페이지는 이전 사진을 유지했고 저장·공유 후 같은 하객 URL에서 새 이미지 주소·실제 로딩을 확인했다. 마지막 편집기 새로고침에도 새 사진이 유지됐다.
- 실제 HEIC 샘플의 Worker 변환도 별도로 확인했다(약 1.64MiB → 718KiB, 1920×2560). 실제 iPhone Safari의 방향·피부색·드레스 디테일 검증은 남아 있다.
- 사용자 로컬 최종 실행 결과: npm audit 0, 프론트 테스트 41개 통과, 프로덕션 빌드 통과(Vite 8.3.5), backend media 테스트 통과. CoverPhotoEditor의 렌더 중 ref 접근을 state로 수정한 뒤 다시 lint/test/build를 실행했으며 오류 0개, 기존 AuthContext/Countdown 관련 경고 3개만 남았다.
- 테스트 초대장에는 합성 검증 이미지를 남겼다. 운영 전에는 실제 사진, 커스텀 이미지 도메인, 운영 Origin CORS를 별도로 연결한다.

## Android 실기기 연결

개인 Wi-Fi의 사설 IP로 안전하게 접속하는 방법과 사진 확인 체크리스트는 [MOBILE_PHOTO_CHECK.md](MOBILE_PHOTO_CHECK.md)를 따른다. PC의 브라우저 반응형 검사와 실제 Android Chrome 확인은 별도 결과로 기록한다.
