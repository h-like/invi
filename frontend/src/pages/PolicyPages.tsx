import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { SiteHeader } from '../discovery/SiteHeader'
import { CONTACT_EMAIL, POLICY_EFFECTIVE_DATE } from '../site'
import '../discovery/flow.css'

function Contact() {
  return CONTACT_EMAIL
    ? <a href={'mailto:' + CONTACT_EMAIL}>{CONTACT_EMAIL}</a>
    : <span>운영자 문의처(배포 시 설정)</span>
}

function PolicyLayout({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="discovery-shell">
      <SiteHeader />
      <main className="flow-narrow policy-page">
        <h1>{title}</h1>
        <p className="flow-muted">시행일: {POLICY_EFFECTIVE_DATE}</p>
        {children}
        <p className="policy-links"><Link to="/privacy">개인정보처리방침</Link> · <Link to="/terms">이용약관</Link> · <Link to="/">홈으로</Link></p>
      </main>
    </div>
  )
}

/**
 * Keep this text true to what the code does: collected fields, the wedding+10-day deletion,
 * and the hosting providers. Change POLICY_EFFECTIVE_DATE (site.ts) whenever it changes.
 */
export function PrivacyPage() {
  return (
    <PolicyLayout title="개인정보처리방침">
      <p>invi(이하 “서비스”)는 모바일 청첩장을 만들고 하객에게 전하는 데 필요한 만큼만 개인정보를 처리합니다. 서비스는 개인이 운영합니다.</p>

      <h2>1. 처리하는 개인정보와 목적</h2>
      <ul>
        <li><strong>회원(청첩장을 만드는 분)</strong>: 이메일, 이름, 로그인 수단(카카오·Google)과 그 식별값, 이메일 가입 시 비밀번호(복원할 수 없게 암호화해 저장). 회원 확인과 로그인, 청첩장 관리에 씁니다.</li>
        <li><strong>청첩장 내용(회원이 직접 입력)</strong>: 신랑·신부와 혼주 이름, 예식 일시와 장소, 계좌 정보, 사진, 안내 문구. 청첩장을 만들어 하객에게 보여주는 데 씁니다.</li>
        <li><strong>하객</strong>: 참석 응답(이름, 참석 여부, 인원, 신랑측·신부측, 식사 여부, 메시지)과 방명록(이름, 메시지). 해당 청첩장의 회원에게 전하는 데 씁니다. 하객은 로그인하지 않습니다.</li>
        <li><strong>자동으로 처리되는 정보</strong>: 접속 IP 주소와 접속 기록. 응답·방명록 남용을 막는 요청 수 제한에 서버 메모리에서만 잠시 쓰고 따로 저장하지 않으며, 호스팅 업체의 접속 기록에 남을 수 있습니다.</li>
      </ul>
      <p>광고·분석 도구와 그 쿠키를 쓰지 않습니다. 로그인 상태는 이용자 브라우저의 저장공간(localStorage)에 보관합니다. 카카오·Google 로그인을 처리하는 동안에만 로그인 요청을 확인하는 세션 쿠키를 잠시 씁니다.</p>

      <h2>2. 보유 기간과 파기</h2>
      <ul>
        <li>무료 청첩장과 그 청첩장의 참석 응답, 방명록, 사진은 <strong>예식일로부터 10일이 지나면 삭제</strong>합니다. 필요한 내용은 그 전에 따로 보관해 주세요.</li>
        <li>회원 정보는 탈퇴를 요청할 때까지 보관하고, 요청을 받으면 지체 없이 삭제합니다.</li>
        <li>데이터는 데이터베이스에서 지우고, 사진 파일은 저장소에서 지웁니다. 복구할 수 없는 방법으로 파기합니다.</li>
      </ul>

      <h2>3. 제3자 제공</h2>
      <p>개인정보를 제3자에게 제공하지 않습니다. 다만 회원이 공개한 청첩장은 링크를 아는 누구나 볼 수 있고, 회원이 숨긴 내용은 하객에게 전달하지 않습니다. 참석 응답과 방명록 관리 화면은 해당 청첩장의 회원만 볼 수 있습니다.</p>

      <h2>4. 처리 위탁과 국외 이전</h2>
      <p>서비스 운영을 위해 아래 업체에 처리를 맡기며, 데이터가 국외 서버에 저장됩니다. 서비스를 이용하는 동안 네트워크로 전송되고, 위 2의 보유 기간 동안 보관됩니다. 국외 이전을 원하지 않으면 서비스를 이용할 수 없습니다.</p>
      <div className="policy-table-wrap">
        <table className="policy-table">
          <thead><tr><th>업체(국가)</th><th>맡기는 일</th><th>저장 위치</th><th>항목</th></tr></thead>
          <tbody>
            <tr><td>Render Services, Inc.(미국)</td><td>서비스 서버 운영</td><td>싱가포르</td><td rowSpan={3}>위 1의 모든 항목</td></tr>
            <tr><td>Neon(미국)</td><td>데이터베이스 보관</td><td>싱가포르</td></tr>
            <tr><td>Cloudflare, Inc.(미국)</td><td>사진 파일 저장</td><td>아시아·태평양</td></tr>
          </tbody>
        </table>
      </div>
      <p>카카오·Google 로그인을 고르면 해당 업체가 회원 확인 결과(이메일, 이름, 식별값)를 서비스에 보냅니다.</p>

      <h2>5. 이용자의 권리</h2>
      <p>회원과 하객은 자신의 개인정보를 열람, 정정, 삭제하거나 처리를 멈추도록 요청할 수 있습니다. 하객은 남긴 응답·방명록 삭제를 청첩장 회원에게 요청하거나 아래 문의처로 요청할 수 있습니다. 탈퇴와 청첩장 삭제도 문의처로 요청해 주세요. 확인 후 지체 없이 처리합니다.</p>

      <h2>6. 안전성 확보 조치</h2>
      <p>모든 통신을 HTTPS로 암호화하고, 비밀번호는 복원할 수 없게 암호화해 저장합니다. 청첩장 편집과 응답 관리는 소유한 회원만 할 수 있게 서버에서 확인합니다.</p>

      <h2>7. 만 14세 미만</h2>
      <p>만 14세 미만은 회원으로 가입할 수 없습니다.</p>

      <h2>8. 개인정보 보호책임자</h2>
      <p>운영자 · 문의: <Contact /></p>
      <p>개인정보 침해 신고와 상담은 개인정보침해신고센터(privacy.kisa.or.kr, 국번 없이 118)나 개인정보분쟁조정위원회(kopico.go.kr, 1833-6972)에도 할 수 있습니다.</p>

      <h2>9. 변경</h2>
      <p>이 방침을 바꾸면 시행 전에 이 페이지에 알립니다.</p>
    </PolicyLayout>
  )
}

export function TermsPage() {
  return (
    <PolicyLayout title="이용약관">
      <h2>1. 서비스</h2>
      <p>invi는 모바일 청첩장을 만들고 링크로 공유하는 무료 서비스입니다. 개인이 운영하며, 하객은 로그인 없이 청첩장을 보고 참석 응답과 방명록을 남길 수 있습니다.</p>

      <h2>2. 가입과 계정</h2>
      <ul>
        <li>만 14세 이상이면 이메일, 카카오, Google 계정으로 가입할 수 있습니다.</li>
        <li>계정 정보는 본인이 관리하며, 다른 사람에게 넘기거나 함께 쓰지 않습니다.</li>
        <li>청첩장은 계정당 정해진 개수(현재 3개)까지 만들 수 있고, 한도를 넘으면 만들 때 안내합니다. 한도는 바뀔 수 있습니다.</li>
      </ul>

      <h2>3. 회원이 올리는 내용</h2>
      <ul>
        <li>청첩장의 글과 사진, 계좌 정보 등은 회원이 올린 것이며 그 책임도 회원에게 있습니다.</li>
        <li>다른 사람의 정보나 사진은 그 사람의 동의를 받은 경우에만 올려 주세요. 저작권을 침해하거나 법에 어긋나는 내용은 올릴 수 없습니다.</li>
        <li>이를 어긴 내용은 알린 뒤 비공개로 바꾸거나 삭제할 수 있습니다. 급한 피해가 우려되면 먼저 조치하고 알립니다.</li>
      </ul>

      <h2>4. 보관 기간</h2>
      <p>무료 청첩장과 그 응답, 방명록, 사진은 예식일로부터 10일이 지나면 삭제되며 되살릴 수 없습니다. 남기고 싶은 내용은 그 전에 따로 보관해 주세요.</p>

      <h2>5. 서비스 변경과 중단</h2>
      <p>무료로 운영하는 서비스라 내용이 바뀌거나 중단될 수 있습니다. 중요한 변경이나 중단은 미리 알리도록 노력합니다. 무료 서버를 쓰기 때문에 한동안 접속이 없으면 첫 화면이 열리는 데 시간이 걸릴 수 있습니다.</p>

      <h2>6. 책임의 제한</h2>
      <p>운영자는 서비스를 안정적으로 제공하려고 노력하지만, 무료 서비스이므로 운영자의 고의나 중대한 과실이 없는 장애, 데이터 손실, 회원이 올린 내용으로 생긴 손해는 책임지지 않습니다.</p>

      <h2>7. 문의와 분쟁</h2>
      <p>문의: <Contact /> · 이 약관은 대한민국 법을 따릅니다.</p>
    </PolicyLayout>
  )
}
