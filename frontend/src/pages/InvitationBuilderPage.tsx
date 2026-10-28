import { useEffect, useRef, useState, type ChangeEvent, type MouseEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { PageRenderer } from '../blocks/PageRenderer'
import { createInvitationPersistence } from '../editor/persistence'
import { CoverPhotoEditor } from '../editor/CoverPhotoEditor'
import { ACCOUNT_ROLE_LABELS, emptyAccountContent, type AccountEntry, type AccountRole, type Block, type Invitation, type PageData, type PageDesign } from '../api/types'
import { BLOCK_LABELS, createEmptyPage, DESIGN_PRESETS, getPageDesign, GREETING_SAMPLES, isImageUrl, MAX_GALLERY_IMAGES, moveBlock, patchContent, publishIssues, updateBlock } from '../editor/model'

const GROOM_ROLES: AccountRole[] = ['groom', 'groomFather', 'groomMother']
const BRIDE_ROLES: AccountRole[] = ['bride', 'brideFather', 'brideMother']
type Panel = 'content' | 'design' | 'sections'

function text(block: Block | undefined, key: string): string {
  return typeof block?.content[key] === 'string' ? block.content[key] as string : ''
}

export function InvitationBuilderPage() {
  const { id } = useParams<{ id: string }>()
  return <InvitationBuilder key={id} id={id} />
}

function InvitationBuilder({ id }: { id: string | undefined }) {
  const [invitation, setInvitation] = useState<Invitation | null>(null)
  const [draft, setDraft] = useState<PageData | null>(null)
  const [baseline, setBaseline] = useState('')
  const [panel, setPanel] = useState<Panel>('content')
  const [previewOnly, setPreviewOnly] = useState(false)
  const [busy, setBusy] = useState(false)
  const [photoPending, setPhotoPending] = useState(false)
  const photoPendingRef = useRef(false)
  function photoPendingChange(pending: boolean) { photoPendingRef.current = pending; setPhotoPending(pending) }
  const [persistence] = useState(() => createInvitationPersistence(api))
  const [failedAction, setFailedAction] = useState<boolean | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState('')
  const [shareOpen, setShareOpen] = useState(false)
  const [copyStatus, setCopyStatus] = useState('')
  const dialogRef = useRef<HTMLDialogElement>(null)
  const unsaved = draft !== null && JSON.stringify(draft) !== baseline

  useEffect(() => {
    let active = true
    if (id) api.getInvitation(id).then(inv => {
      if (!active) return
      const page = inv.pageData ?? createEmptyPage()
      setInvitation(inv)
      setDraft(page)
      setBaseline(JSON.stringify(page))
    }).catch(err => { if (active) setError(err instanceof Error ? err.message : '청첩장을 불러오지 못했습니다.') })
    return () => { active = false }
  }, [id])

  useEffect(() => {
    if (!unsaved && !photoPending) return
    function warn(event: BeforeUnloadEvent) { event.preventDefault(); event.returnValue = '' }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [unsaved, photoPending])

  useEffect(() => {
    if (shareOpen && dialogRef.current && !dialogRef.current.open) dialogRef.current.showModal()
    if (!shareOpen && dialogRef.current?.open) dialogRef.current.close()
  }, [shareOpen])

  function confirmLeave(event: MouseEvent<HTMLAnchorElement>) {
    if ((unsaved || photoPendingRef.current) && !window.confirm('저장하지 않은 변경 사항이나 처리 중인 사진이 있습니다. 저장하지 않고 이동할까요?')) event.preventDefault()
  }

  if (!invitation || !draft) return <main className="page">{error ? <><p className="form-error" role="alert">{error}</p><Link to="/invitations">내 청첩장으로</Link></> : '청첩장을 불러오는 중...'}</main>

  const hero = draft.blocks.find(b => b.type === 'hero')
  const map = draft.blocks.find(b => b.type === 'map')
  const gallery = draft.blocks.find(b => b.type === 'gallery')
  const account = draft.blocks.find(b => b.type === 'account')
  const design = getPageDesign(draft)
  const sortedBlocks = [...draft.blocks].sort((a, b) => a.order - b.order)
  const images = Array.isArray(gallery?.content.images) ? gallery.content.images.filter(isImageUrl) : []
  const accounts = emptyAccountContent()
  for (const role of [...GROOM_ROLES, ...BRIDE_ROLES]) {
    const entry = account?.content[role] as Partial<AccountEntry> | undefined
    if (entry) accounts[role] = { ...accounts[role], ...entry }
  }
  const publicUrl = window.location.origin + '/i/' + invitation.slug

  function changeContent(block: Block | undefined, patch: Record<string, unknown>) {
    if (!block) return
    setDraft(prev => prev ? patchContent(prev, block.id, patch) : prev)
    setNotice('')
    setError(null)
  }
  function changeDesign(patch: Partial<PageDesign>) {
    setDraft(prev => prev ? { ...prev, design: { ...getPageDesign(prev), ...patch } } : prev)
    setNotice('')
    setError(null)
  }
  function move(id: string, target: string) {
    setDraft(prev => prev ? moveBlock(prev, id, target) : prev)
    setNotice('')
  }
  function updateAccount(role: AccountRole, patch: Partial<AccountEntry>) {
    if (!account) return
    setDraft(prev => {
      if (!prev) return prev
      const existing = prev.blocks.find(b => b.id === account.id)?.content[role] as Partial<AccountEntry> | undefined
      return patchContent(prev, account.id, { [role]: { ...emptyAccountContent()[role], ...existing, ...patch } })
    })
    setNotice('')
  }

  async function persist(publish: boolean) {
    if (!invitation || !draft || persistence.isRunning()) return
    if (photoPendingRef.current) { setError('새 사진의 업로드를 완료하거나 선택을 취소한 뒤 저장·공개해 주세요.'); return }
    setError(null)
    setFailedAction(null)
    setNotice('')
    if (publish) {
      const issues = publishIssues(draft)
      if (issues.length) { setError(issues.join(' ')); setPanel('content'); setPreviewOnly(false); return }
    }
    setShareOpen(false)
    setBusy(true)
    try {
      const result = await persistence.run(invitation, draft, publish)
      if (result.kind === 'busy') return
      if (result.kind === 'save-failed') {
        // Do not replace draft or baseline: the user's unsaved input stays intact.
        setError('저장 완료를 확인하지 못했습니다. 입력 내용은 현재 화면에 남아 있습니다. ' + result.detail)
        setFailedAction(publish)
        return
      }
      const saved = result.kind === 'publish-failed' ? result.saved : result.invitation
      setInvitation(saved)
      setDraft(saved.pageData ?? draft)
      setBaseline(JSON.stringify(saved.pageData ?? draft))
      if (result.kind === 'publish-failed') {
        setNotice('내용 저장 완료 · 공개 확인 필요')
        setError('내용은 저장했지만 공개 완료를 확인하지 못했습니다. 다시 시도해 주세요. ' + result.detail)
        setFailedAction(true)
        return
      }
      if (result.shared) {
        setCopyStatus('')
        setShareOpen(true)
        setNotice('저장하고 공개했습니다.')
      } else setNotice('변경 사항을 저장했습니다.')
    } catch {
      setError('처리 결과를 확인하지 못했습니다. 현재 화면의 내용을 확인하고 다시 시도해 주세요.')
      setFailedAction(publish)
    } finally { setBusy(false) }
  }

  async function copyLink() {
    try { await navigator.clipboard.writeText(publicUrl); setCopyStatus('링크를 복사했습니다.') }
    catch { setCopyStatus('자동 복사가 차단되었습니다. 위 주소를 선택해 직접 복사해 주세요.') }
  }

  return (
    <main className={'builder-page' + (previewOnly ? ' is-preview-only' : '')}>
      <header className="builder-header">
        <Link className="builder-brand" to="/" onClick={confirmLeave}>invi<span>우리의 시작을 담다</span></Link>
        <div className="builder-header-actions">
          <span className="save-state" role="status">{busy ? '저장 중...' : photoPending ? '새 사진 처리 · 확인 필요' : notice || (unsaved ? '저장하지 않은 변경 사항' : '모든 변경 사항 저장됨')}</span>
          <button type="button" className="secondary-button" disabled={photoPending} onClick={() => setPreviewOnly(!previewOnly)}>{previewOnly ? '편집으로 돌아가기' : '미리보기'}</button>
          <button type="button" className="secondary-button" disabled={busy || photoPending || !unsaved} onClick={() => void persist(false)}>저장</button>
          <button type="button" className="publish-button" disabled={busy || photoPending} onClick={() => void persist(true)}>{busy ? '처리 중...' : invitation.status === 'PUBLISHED' ? '저장하고 공유' : '공개 · 공유'}</button>
        </div>
      </header>
      {error && <div className="builder-feedback"><div className="builder-error" role="alert"><p>{error}</p>{failedAction !== null && <button type="button" className="secondary-button" disabled={busy} onClick={() => void persist(failedAction)}>{failedAction ? '저장하고 공개 다시 시도' : '저장 다시 시도'}</button>}</div></div>}
      <div className="builder-layout">
        <aside className="builder-panel" aria-label="청첩장 편집">
          <div className="builder-intro"><p className="builder-eyebrow">YOUR WEDDING, YOUR MOOD</p><h1>우리다운 초대장</h1><p>하나씩 채우면, 오른쪽에서 바로 완성돼요.</p></div>
          <ol className="builder-steps" aria-label="제작 순서"><li>1 디자인</li><li>2 내용 편집</li><li>3 저장 · 공유</li></ol>
          <nav className="builder-tabs" aria-label="편집 설정">
            {([['content', '내용'], ['design', '디자인'], ['sections', '섹션 구성']] as const).map(([value, label]) => <button key={value} type="button" aria-pressed={panel === value} disabled={photoPending} onClick={() => setPanel(value)}>{label}</button>)}
          </nav>
          <fieldset className="builder-fields" disabled={busy}>
            <legend className="sr-only">청첩장 편집 항목</legend>
            {panel === 'content' && <>
              {hero && <section className="editor-section"><h2>두 사람의 이름</h2><div className="form-columns">
                <label>신랑 이름<input value={text(hero, 'groomName')} maxLength={50} onChange={e => changeContent(hero, { groomName: e.target.value })} placeholder="신랑 이름" /></label>
                <label>신부 이름<input value={text(hero, 'brideName')} maxLength={50} onChange={e => changeContent(hero, { brideName: e.target.value })} placeholder="신부 이름" /></label>
              </div><div className="form-columns"><label>예식일<input value={invitation.weddingDate} readOnly aria-describedby="date-hint" /></label><label>예식 시간<input type="time" value={text(hero, 'weddingTime')} onChange={e => changeContent(hero, { weddingTime: e.target.value })} /></label></div>
                <p className="editor-section-hint" id="date-hint">예식일은 청첩장을 만들 때 선택한 날짜입니다.</p>
                <CoverPhotoEditor invitationId={invitation.id} currentUrl={isImageUrl(text(hero, 'coverImage')) ? text(hero, 'coverImage') : ''} disabled={busy} onChange={url => changeContent(hero, { coverImage: url })} onPendingChange={photoPendingChange} />
                <details className="cover-photo-url"><summary>이미지 주소로 직접 입력</summary><label>대표 사진 URL<input type="url" disabled={photoPending} value={text(hero, 'coverImage')} onChange={e => changeContent(hero, { coverImage: e.target.value })} placeholder="https://.../photo.jpg" /></label>
                {text(hero, 'coverImage') && !isImageUrl(text(hero, 'coverImage')) && <p className="form-error">http 또는 https 이미지 주소를 입력해 주세요. 올바른 주소만 표시됩니다.</p>}</details>
              </section>}
              {hero && <section className="editor-section"><h2>초대의 글</h2><label>인사말<textarea rows={6} maxLength={1500} value={text(hero, 'greeting')} onChange={e => changeContent(hero, { greeting: e.target.value })} placeholder="소중한 분들께 전할 초대의 글을 써 주세요." /></label>
                <p className="editor-section-hint">샘플 문구를 선택하면 현재 인사말을 바꿉니다.</p><div className="sample-buttons">{GREETING_SAMPLES.map(sample => <button type="button" key={sample.name} onClick={() => { if (!text(hero, 'greeting') || window.confirm('현재 인사말을 샘플 문구로 바꿀까요?')) changeContent(hero, { greeting: sample.text }) }}>{sample.name}</button>)}</div>
              </section>}
              {map && <section className="editor-section"><h2>오시는 길</h2><label>식장 이름<input value={text(map, 'venueName')} maxLength={150} onChange={e => changeContent(map, { venueName: e.target.value })} placeholder="웨딩홀 · 층 · 홀 이름" /></label><label>주소<input value={text(map, 'address')} maxLength={300} onChange={e => changeContent(map, { address: e.target.value })} placeholder="예식장 주소를 직접 입력해 주세요" /></label><label>교통 · 주차 안내<textarea rows={3} maxLength={1500} value={text(map, 'directions')} onChange={e => changeContent(map, { directions: e.target.value })} placeholder="지하철 출구, 셔틀버스, 주차 정보를 안내해 주세요." /></label></section>}
              {gallery && <section className="editor-section"><h2>웨딩 갤러리 <small>{images.length} / {MAX_GALLERY_IMAGES}</small></h2><label>사진 배치<select value={String(gallery.content.layout ?? 'grid-2')} onChange={e => changeContent(gallery, { layout: e.target.value })}><option value="grid-2">2열 그리드</option><option value="grid-3">3열 그리드</option><option value="slider">가로 슬라이드</option></select></label><ImageListEditor images={images} onChange={next => changeContent(gallery, { images: next })} /></section>}
              {account && <section className="editor-section"><h2>마음 전하실 곳</h2><p className="editor-section-hint">비워 둔 분은 표시하지 않습니다. 계좌 대신 메시지도 전할 수 있어요.</p>{([['신랑측', GROOM_ROLES], ['신부측', BRIDE_ROLES]] as const).map(([title, roles]) => <div key={title}><p className="editor-section-sub">{title}</p>{roles.map(role => <AccountRoleEditor key={role} role={role} entry={accounts[role]} onChange={patch => updateAccount(role, patch)} />)}</div>)}</section>}
            </>}
            {panel === 'design' && <>
              <section className="editor-section"><h2>두 사람의 분위기</h2><p className="editor-section-hint">디자인만 바뀌고 입력한 내용과 사진은 그대로 유지됩니다.</p><div className="design-grid">{DESIGN_PRESETS.map(preset => <button key={preset.design.theme} type="button" className={'design-card' + (design.theme === preset.design.theme ? ' selected' : '')} aria-pressed={design.theme === preset.design.theme} onClick={() => changeDesign({ ...preset.design, fontSize: design.fontSize })}><span className={'design-swatch swatch-' + preset.design.theme} style={{ background: preset.design.backgroundColor, color: preset.design.accentColor }}>Our<br />Wedding</span><strong>{preset.name}</strong><small>{preset.description}</small></button>)}</div></section>
              <section className="editor-section"><h2>색상과 글자</h2><div className="form-columns"><label>배경 색상<input type="color" value={design.backgroundColor} onChange={e => changeDesign({ backgroundColor: e.target.value })} /></label><label>강조 색상<input type="color" value={design.accentColor} onChange={e => changeDesign({ accentColor: e.target.value })} /></label></div><label>글꼴<select value={design.fontFamily} onChange={e => changeDesign({ fontFamily: e.target.value as PageDesign['fontFamily'] })}><option value="serif">클래식 명조</option><option value="sans">모던 고딕</option></select></label><label>글자 크기<select value={design.fontSize} onChange={e => changeDesign({ fontSize: e.target.value as PageDesign['fontSize'] })}><option value="normal">보통</option><option value="large">크게</option><option value="larger">더 크게</option></select></label><p className="editor-section-hint">배경과 대비가 낮은 글자색은 읽기 편한 색으로 자동 보정합니다.</p></section>
            </>}
            {panel === 'sections' && <section className="editor-section"><h2>보여주고 싶은 순서대로</h2><p className="editor-section-hint">행을 드래그하거나 위·아래 버튼으로 순서를 바꾸세요. 숨긴 내용도 저장됩니다.</p><ol className="section-list">{sortedBlocks.map((block, index) => <li key={block.id} className={block.visible ? '' : 'section-hidden'} draggable={!busy} onDragStart={e => { e.dataTransfer.setData('text/plain', block.id); e.dataTransfer.effectAllowed = 'move' }} onDragOver={e => { e.preventDefault(); e.dataTransfer.dropEffect = 'move' }} onDrop={e => { e.preventDefault(); move(e.dataTransfer.getData('text/plain'), block.id) }}><span className="drag-handle" aria-hidden="true">⠿</span><label><input type="checkbox" checked={block.visible} onChange={e => { const visible = e.target.checked; setDraft(prev => prev ? updateBlock(prev, block.id, { visible }) : prev); setNotice('') }} />{BLOCK_LABELS[block.type] ?? block.type}</label><div className="order-buttons"><button type="button" aria-label={(BLOCK_LABELS[block.type] ?? block.type) + ' 위로'} disabled={index === 0} onClick={() => move(block.id, sortedBlocks[index - 1].id)}>↑</button><button type="button" aria-label={(BLOCK_LABELS[block.type] ?? block.type) + ' 아래로'} disabled={index === sortedBlocks.length - 1} onClick={() => move(block.id, sortedBlocks[index + 1].id)}>↓</button></div></li>)}</ol></section>}
          </fieldset>
          <footer className="builder-panel-footer"><Link to={`/e/${invitation.id}/guests`} onClick={confirmLeave}>참석 응답 · 방명록 관리</Link><p>저장 전 미리보기에서는 하객 응답을 전송하지 않습니다.</p></footer>
        </aside>
        <section className="builder-preview-area" aria-label="청첩장 실시간 미리보기"><div className="preview-caption"><span>LIVE PREVIEW</span><span>변경 사항이 바로 반영됩니다</span></div><div className="preview-phone"><div className="preview-phone-bar" aria-hidden="true"><span />invi invitation</div><div className="preview-phone-content"><PageRenderer pageData={draft} weddingDate={invitation.weddingDate} invitationId={invitation.id} interactive={false} /></div></div><p className="preview-footnote">하객에게도 같은 디자인으로 보여집니다.</p></section>
      </div>
      <dialog className="share-dialog" ref={dialogRef} aria-labelledby="share-title" onCancel={() => setShareOpen(false)} onClose={() => setShareOpen(false)}><p className="builder-eyebrow">READY TO SHARE</p><h2 id="share-title">두 사람의 초대장이 완성됐어요.</h2><p>링크를 복사해 소중한 분들께 전해 주세요.</p><label>공개 청첩장 주소<input value={publicUrl} readOnly onFocus={e => e.target.select()} /></label><div className="share-actions"><button type="button" onClick={() => void copyLink()}>링크 복사</button><a className="button-link" href={publicUrl} target="_blank" rel="noreferrer">청첩장 열기</a></div><p role="status" className="editor-section-hint">{copyStatus}</p><button className="secondary-button" type="button" onClick={() => setShareOpen(false)}>편집 계속하기</button></dialog>
    </main>
  )
}

function ImageListEditor({ images, onChange }: { images: string[]; onChange: (images: string[]) => void }) {
  const [url, setUrl] = useState('')
  const [error, setError] = useState('')
  function addImage() {
    if (!isImageUrl(url.trim())) { setError('http 또는 https 이미지 주소를 입력해 주세요.'); return }
    if (images.length >= MAX_GALLERY_IMAGES) { setError('사진은 최대 ' + MAX_GALLERY_IMAGES + '장까지 추가할 수 있습니다.'); return }
    onChange([...images, url.trim()]); setUrl(''); setError('')
  }
  function reorder(from: number, to: number) {
    const next = [...images]; const [image] = next.splice(from, 1); next.splice(to, 0, image); onChange(next)
  }
  return <div className="list-editor"><label>사진 URL<input type="url" value={url} onChange={e => setUrl(e.target.value)} placeholder="https://.../photo.jpg" /></label><button type="button" className="secondary-button" onClick={addImage} disabled={images.length >= MAX_GALLERY_IMAGES}>사진 추가</button><p className="editor-section-hint">8~12장을 권장하며 최대 20장까지 추가할 수 있습니다. 갤러리 파일 업로드는 추후 지원됩니다.</p>{error && <p className="form-error" role="alert">{error}</p>}{images.map((src, index) => <div key={index + '-' + src} className="list-editor-row"><img src={src} alt={'등록한 웨딩 사진 ' + (index + 1)} className="list-editor-thumb" loading="lazy" /><span className="photo-index">{index + 1}</span><button type="button" aria-label={'사진 ' + (index + 1) + ' 위로'} disabled={index === 0} onClick={() => reorder(index, index - 1)}>↑</button><button type="button" aria-label={'사진 ' + (index + 1) + ' 아래로'} disabled={index === images.length - 1} onClick={() => reorder(index, index + 1)}>↓</button><button type="button" aria-label={'사진 ' + (index + 1) + ' 삭제'} onClick={() => onChange(images.filter((_, i) => i !== index))}>삭제</button></div>)}</div>
}

function AccountRoleEditor({ role, entry, onChange }: { role: AccountRole; entry: AccountEntry; onChange: (patch: Partial<AccountEntry>) => void }) {
  const label = ACCOUNT_ROLE_LABELS[role]
  const input = (key: keyof AccountEntry) => (e: ChangeEvent<HTMLInputElement>) => onChange({ [key]: e.target.value })
  return <div className="account-role-editor"><p className="account-role-label">{label}</p><div className="account-editor-row"><input aria-label={label + ' 예금주'} value={entry.holder} onChange={input('holder')} placeholder="예금주" maxLength={100} /><input aria-label={label + ' 은행'} value={entry.bank} onChange={input('bank')} placeholder="은행" maxLength={50} /><input aria-label={label + ' 계좌번호'} value={entry.accountNumber} onChange={input('accountNumber')} placeholder="계좌번호" maxLength={100} /></div><input aria-label={label + ' 계좌 대신 메시지'} value={entry.message} onChange={input('message')} placeholder="계좌 대신 표시할 메시지" maxLength={300} /></div>
}
