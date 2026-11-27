---
name: template-design-system
description: 청첩장(하객 화면) 템플릿의 생김새를 바꿀 때 사용 — 새 테마 추가, themes.json 프리셋 수정, 새 디자인 파츠(슬롯/값)·토큰·폰트 추가, invitation.css/envelope.css 스타일, 사용자 꾸미기(design.look), 봉투 인트로 그림, page_data의 design/블록 content 필드 추가. 서비스 UI(편집기·목록 화면) 리스타일에는 쓰지 않는다.
---

# 템플릿 디자인 시스템

원칙: **템플릿의 생김새는 데이터다.** 테마 = `themes.json`의 한 항목, 디자인 요소 = 파츠 슬롯 값 + 그 클래스의 CSS. 이 원칙이 깨지면 (1) 기존 청첩장이 템플릿 개선을 못 받고, (2) 사용자 입력이 인라인 CSS로 흘러 외부 요청이 생기고, (3) 이전 버전 탭이 새 설정을 지운다.

핵심 파일:
- `frontend/src/editor/themes.json` — 프리셋 **유일한 원본**(에디터 `DESIGN_PRESETS` + 백엔드 `TemplateSeeder`가 같이 읽는다)
- `frontend/src/editor/model.ts` — `PART_OPTIONS`, `TOKEN_VARS`, `TOKEN_KINDS`, `PART_GROUP_OF`, `PART_LABELS`, `PART_FACES`, `sanitizeLook`, `pageLook`, `withDesign`
- `frontend/src/blocks/fonts.ts` — `FONT_STACKS`, `FONT_FACES`
- `frontend/src/blocks/PageRenderer.tsx` — `part-<slot>-<value>` 클래스 부여
- `frontend/src/blocks/invitation.css`, `envelope.css`, `envelopeArt.tsx`

## 0. 무엇을 추가하는지 고른다

| 하고 싶은 것 | 방법 | 건드리는 곳 |
|---|---|---|
| 새 테마(색·폰트·기존 파츠 조합) | **JSON만** | `themes.json` |
| 기존 슬롯에 새 모양 추가 | 슬롯 값 + CSS | `PART_OPTIONS`, `PART_LABELS`, `invitation.css` |
| 완전히 새 디자인 요소 | 새 슬롯 + CSS | 위 + `PART_GROUP_OF`(+ 필요 시 `PART_FACES`) |
| 사용자가 고를 수 있는 색/간격 | 토큰 | `TOKEN_VARS`, `TOKEN_KINDS`, `sanitizeLook` 허용값 |
| 테마 전용 서체 | 폰트 face | `FONT_FACES`, 프리셋 `fonts` |
| 블록에 새 입력 필드 | page_data content | `api/types.ts`, 정규화 함수(아래 5번) |

판단이 애매하면 "다른 테마도 이걸 쓸 수 있나?" — 그렇다면 슬롯 값으로 만든다. 한 테마에서만 쓰더라도 테마 이름에 묶지 않는다.

## 1. 새 테마 (themes.json)

```json
{
  "code": "new-theme", "name": "표시 이름", "description": "한 줄 설명",
  "design": { "theme": "new-theme", "backgroundColor": "#...", "accentColor": "#...", "fontFamily": "serif", "fontSize": "normal", "heroLayout": "frame" },
  "parts": { "heroFrame": "arch" },
  "tokens": { "line": "#..." },
  "fonts": { "serif": "gowun-batang" }
}
```
- `code` = `design.theme` (= 백엔드 `Template.code` = `category`). 한 번 배포된 code는 바꾸지 않는다 — 청첩장이 참조한다. 목록에서 빼도 DB 행은 남는다.
- **`parts`/`tokens`/`fonts`는 `design` 밖에.** 시더가 `design`을 페이지에 복사하므로 안에 넣으면 그 시점 값이 각 청첩장에 박제된다.
- 기본값과 같은 파츠는 적지 않는다(PART_OPTIONS 각 배열의 첫 값이 기본).
- 대비: 본문색과 `backgroundColor`가 읽히는지 `contrastRatio`(4.5 이상) 기준으로 확인한다.
- `backend/build.gradle`이 이 파일을 `presets/themes.json`으로 복사하고 Dockerfile도 복사한다 — 경로를 옮기지 않는다. 부팅 시 `TemplateSeeder`가 code 기준으로 생성/동기화.
- 썸네일 등 이미지가 필요하면 외부 URL이 아니라 자체 호스팅 자산.

## 2. 파츠 (슬롯과 값)

- `PART_OPTIONS[slot]`에 값 추가(새 슬롯이면 배열 첫 값 = "효과 없음" 기본값).
- 아래 레지스트리는 `Record<PartSlot, …>`라 새 슬롯을 빼먹으면 컴파일이 실패한다 — 에러를 따라 채운다: `PART_GROUP_OF`(편집기 그룹: paper/type/staging/envelope), `PART_LABELS`(한국어 라벨, 필요 시 `layouts`). 서체가 필요한 값이면 `PART_FACES`.
- CSS는 **`PageRenderer`가 붙이는 `.part-<slot>-<value>` 클래스만** 대상으로 한다. `invitation-theme-*` 셀렉터 금지(테스트가 막는다). 기본값에는 클래스가 붙지 않으므로 기본 모양은 클래스 없는 기본 스타일로.
- 색은 하드코딩하지 말고 테마 CSS 변수(`--theme-*`, `--inv-*`, 강조색)를 쓴다 — 같은 파츠가 모든 테마 색에서 동작해야 한다.
- 봉투 관련 슬롯(`PART_GROUP_OF[slot] === 'envelope'`)은 `envelope`가 켜졌을 때만 보이고 적용된다.

## 3. 토큰과 사용자 꾸미기 (design.look)

- 프리셋 토큰은 신뢰된 값이지만 **`design.look`의 토큰은 사용자 입력이 인라인 CSS 변수가 된다.** 그래서 새 토큰은 `TOKEN_KINDS`에 종류(`color`=hex만, `tracking`=정해진 목록만)를 지정하고 `sanitizeLook`이 그 외 값을 버리는지 확인한다. `url()`, `var()`, 임의 문자열, 외부 요청이 생기는 값은 받지 않는다.
- 새 종류(예: 크기)가 필요하면 고정 옵션 목록 방식(`NAME_TRACKING_OPTIONS` 선례)으로 만든다. 자유 입력 금지.
- look에는 **템플릿과 다른 값만** 저장(`withPart`/`withToken`이 처리). `pageLook(design)`이 프리셋 위에 겹친다.
- 다른 템플릿을 고르면 look을 비운다(기존 동작 유지).

## 4. 폰트

- npm `@fontsource/*` 등으로 **자체 호스팅**. Google Fonts 등 CDN 금지(하객 기기에서 제3자 요청이 나가면 안 된다). 라이선스(OFL 등) 확인.
- `FONT_FACES`에 `family`/`stack`(시스템 폰트 폴백 포함)/`load`(동적 import) 추가. 라틴 전용 서체는 `latin-*` 서브셋만.
- 테마는 `fonts`(serif/script/display)로 참조하고, 페이지가 실제로 쓸 때만 로드된다.
- 새 패키지는 `package.json`에 버전 고정으로 추가, 저장소에 woff2 파일을 직접 넣지 않는다.

## 5. page_data 호환성 (모르는 필드를 지우지 않는다)

- 블록 `content`에 필드를 추가하면 `api/types.ts` 타입 + 해당 블록의 정규화/읽기 함수를 고친다. hero 혼주·모노그램은 `blocks/heroDetails.ts` 한 곳.
- 정규화는 최상위·블록·`content`를 **펼쳐 복사**해 모르는 키를 보존한다. `design`은 `withDesign()`이 아는 필드만 손대고 나머지는 둔다. 갤러리는 `splitGalleryImages()`/`withGalleryImages()`(모르는 항목은 뒤에 그대로).
- 숨긴 블록(`visible !== true`)의 값을 하객 화면에서 쓰지 않는다(서버가 빼기도 하지만, 모노그램처럼 다른 블록이 읽는 값도 마찬가지).
- 새 필드에 이미지 URL이 들어가면 R2 참조 추적 대상인지 확인한다(`invitation-write-path` 스킬 4번).
- 계약을 바꿨으면 CLAUDE.md "page_data 블록 스키마"를 고친다.

## 6. 봉투 인트로

- 그림은 `blocks/envelopeArt.tsx` **한 곳**. 하객 화면 `EnvelopeIntro`와 편집기 `EnvelopePreview`가 공유하므로 따로 그리지 않는다.
- SVG `id`(gradient, pattern, clipPath)는 `EnvelopeIds`로 봉투마다 접두어 — 미리보기와 하객 화면이 한 문서에 같이 있어도 충돌하지 않게.
- 색은 `envelopeColor` 토큰(없으면 강조색), 실링 이니셜은 `monogram` 규칙(heroDetails)을 따른다.

## 7. 검증

```powershell
cd frontend; npm test
```
```powershell
cd frontend; npm run build
```
- [ ] `editor/model.test.ts`: 프리셋의 모든 parts/tokens/fonts가 레지스트리에 있는지, 모든 슬롯이 그룹에 속하는지, CSS에 `invitation-theme-`가 없고 모든 비기본 값에 CSS가 있는지 — 이 테스트들이 통과해야 한다.
- [ ] 새 토큰: `sanitizeLook`이 허용값만 남기고 `url(...)`·임의 문자열은 버리는 테스트 추가.
- [ ] 새 content 필드: 모르는 필드 보존 테스트(정규화 전후 미지 키 유지).
- [ ] 새 테스트 파일을 만들었으면 `package.json`의 `scripts.test` 목록에 추가(나열된 파일만 실행된다).
- [ ] 백엔드 시더가 새 프리셋을 읽는지: `cd backend; ./gradlew test`.
- [ ] 브라우저 확인: 편집기 미리보기와 `/i/:slug`(또는 `/r/:token`)에서 모바일 폭(375px)으로, 봉투 테마면 인트로까지. 네트워크 탭에 제3자 도메인 요청이 없는지.
