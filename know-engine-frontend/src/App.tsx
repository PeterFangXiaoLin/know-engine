import axios from 'axios'
import { useEffect, useRef, useState } from 'react'
import type { DragEvent, FormEvent } from 'react'
import {
  ArrowDownToLine, ArrowRight, ArrowUpRight, BookOpen, Check, CheckCheck,
  CheckCircle2, ChevronRight, CircleHelp, CloudUpload, ExternalLink,
  File, FileCheck2, FileText, Fingerprint, FolderOpen, Info, Layers3,
  LoaderCircle, LockKeyhole, Plus, ShieldCheck, Sparkles, Upload, UserRound, X,
} from 'lucide-react'
import {
  formatFileSize, getUploadError, MAX_UPLOAD_MB, safeDocumentUrl,
  uploadDocument, validateFile,
} from './lib/documents'
import type { DocumentStatus, UploadRecord } from './types/document'

const statusLabels: Record<DocumentStatus, string> = {
  INIT: '已初始化', UPLOADED: '已上传', CONVERTING: '转换中',
  CONVERTED: '已转换', CHUNKED: '已分片', VECTOR_STORED: '已向量化',
}

const timeFormatter = new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit' })

function Brand({ compact = false }: { compact?: boolean }) {
  return (
    <a href="#upload" className="brand" aria-label="KnowEngine 文档上传">
      <span className="brand-mark"><Layers3 size={23} strokeWidth={1.8} /></span>
      {!compact && <span>Know<span className="font-normal">Engine</span><span className="brand-dot">.</span></span>}
    </a>
  )
}

function DocumentIllustration() {
  return (
    <div className="document-art" aria-hidden="true">
      <div className="art-orbit orbit-one" /><div className="art-orbit orbit-two" />
      <div className="art-dot dot-one" /><div className="art-dot dot-two" />
      <div className="art-sheet sheet-back"><span /><span /><span /></div>
      <div className="art-sheet sheet-front">
        <div className="art-sheet-icon"><FileText size={27} strokeWidth={1.5} /></div>
        <div className="art-line long" /><div className="art-line" /><div className="art-line short" />
        <div className="art-sheet-grid"><span /><span /><span /><span /></div>
      </div>
      <div className="art-spark"><Sparkles size={24} /></div>
      <div className="art-check"><Check size={17} strokeWidth={2.5} /></div>
      <span className="art-caption">A little knowledge. Endless possibilities.</span>
    </div>
  )
}

function App() {
  const [file, setFile] = useState<File | null>(null)
  const [uploadUser, setUploadUser] = useState('')
  const [accessibleBy, setAccessibleBy] = useState('')
  const [dragging, setDragging] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [progress, setProgress] = useState(0)
  const [fileError, setFileError] = useState('')
  const [userError, setUserError] = useState('')
  const [requestError, setRequestError] = useState('')
  const [notice, setNotice] = useState<{ kind: 'success' | 'info'; text: string } | null>(null)
  const [records, setRecords] = useState<UploadRecord[]>([])
  const [selectedRecord, setSelectedRecord] = useState<UploadRecord | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const userInputRef = useRef<HTMLInputElement>(null)
  const controllerRef = useRef<AbortController | null>(null)
  const dragDepthRef = useRef(0)
  const dialogRef = useRef<HTMLDialogElement>(null)

  useEffect(() => () => controllerRef.current?.abort(), [])

  useEffect(() => {
    if (!uploading) return
    const warnBeforeLeaving = (event: BeforeUnloadEvent) => {
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', warnBeforeLeaving)
    return () => window.removeEventListener('beforeunload', warnBeforeLeaving)
  }, [uploading])

  useEffect(() => {
    if (selectedRecord && !dialogRef.current?.open) dialogRef.current?.showModal()
    if (!selectedRecord && dialogRef.current?.open) dialogRef.current?.close()
  }, [selectedRecord])

  function selectFiles(files: FileList | null) {
    if (controllerRef.current || !files?.length) return
    setNotice(null)
    setRequestError('')
    if (files.length !== 1) {
      setFile(null)
      setFileError('每次请选择一份文档，上传完成后可以继续添加。')
      return
    }
    const selected = files[0]
    const error = validateFile(selected)
    setFileError(error || '')
    setFile(error ? null : selected)
  }

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault()
    dragDepthRef.current = 0
    setDragging(false)
    selectFiles(event.dataTransfer.files)
  }

  async function handleUpload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (controllerRef.current) return

    const nextFileError = file ? validateFile(file) || '' : '请先选择需要上传的文档。'
    const nextUserError = uploadUser.trim() ? '' : '请填写上传用户。'
    setFileError(nextFileError)
    setUserError(nextUserError)
    if (!file || nextFileError || nextUserError) {
      if (nextUserError) userInputRef.current?.focus()
      return
    }

    const controller = new AbortController()
    controllerRef.current = controller
    setUploading(true)
    setProgress(0)
    setRequestError('')
    setNotice(null)

    try {
      const document = await uploadDocument({
        file, uploadUser, accessibleBy, signal: controller.signal, onProgress: setProgress,
      })
      setRecords(previous => [{
        id: crypto.randomUUID(), fileName: file.name, size: file.size,
        uploadedAt: new Date(), document,
      }, ...previous])
      setFile(null)
      setNotice({ kind: 'success', text: `「${document.docTitle}」上传成功，已保存到知识库。` })
    } catch (error) {
      if (axios.isCancel(error)) {
        setNotice({ kind: 'info', text: '已取消等待上传结果。服务器可能已接收文件，请核对保存结果后再上传。' })
      } else {
        setRequestError(getUploadError(error))
      }
    } finally {
      controllerRef.current = null
      setUploading(false)
    }
  }

  return (
    <div className="min-h-screen bg-canvas text-ink">
      <a className="skip-link" href="#main-content">跳到主要内容</a>
      <aside className="sidebar">
        <Brand />
        <div className="workspace-label">我的工作空间 <span>PERSONAL</span></div>
        <p className="nav-group-label">知识管理</p>
        <nav aria-label="页面导航" className="space-y-2">
          <a href="#upload" className="nav-item active" aria-current="page"><CloudUpload size={19} />文档上传<span className="nav-active-dot" /></a>
          <a href="#records" className="nav-item"><FolderOpen size={19} />本次上传记录<ChevronRight size={15} className="ml-auto opacity-40" /></a>
        </nav>
        <div className="sidebar-bottom">
          <div className="sidebar-note"><span className="sidebar-note-icon"><BookOpen size={20} /></span><p>让知识，<br />在这里生长。</p><span className="sidebar-note-stars">✦</span></div>
          <a href="#guide" className="nav-item"><CircleHelp size={18} />上传指南<ArrowUpRight size={15} className="ml-auto" /></a>
          <div className="sidebar-footer"><span className="small-brand">K</span><div>KnowEngine<span>Your knowledge, connected.</span></div></div>
        </div>
      </aside>

      <div className="page-shell">
        <header className="topbar">
          <div className="mobile-brand"><Brand compact /></div>
          <div className="flex items-center gap-2.5 text-[13px] text-muted"><span className="hidden sm:inline">工作空间</span><ChevronRight size={13} className="hidden sm:block text-faint" /><span className="text-ink">文档上传</span></div>
          <a href="#guide" className="topbar-help"><CircleHelp size={16} /><span>使用指南</span></a>
        </header>

        <main id="main-content" className="main-content">
          <section className="page-heading" id="upload" aria-labelledby="page-title">
            <div>
              <div className="eyebrow"><span /> BUILD YOUR KNOWLEDGE</div>
              <h1 id="page-title">每一份文档，都是<span>知识的起点。</span></h1>
              <p>收好散落的信息，让有价值的内容在这里汇聚。</p>
            </div>
            <span className="heading-tag"><Layers3 size={14} />知识文档上传</span>
          </section>

          <div className="upload-layout">
            <section className="upload-panel" aria-labelledby="upload-title">
              <div className="panel-heading"><div className="flex items-center gap-2.5"><span className="panel-icon"><Plus size={18} /></span><h2 id="upload-title">添加新文档</h2></div><span className="text-xs text-muted">从这里开始积累</span></div>
              <form onSubmit={handleUpload} noValidate className="upload-form" aria-busy={uploading}>
                <div className="step-title"><span>01</span><h3>选择文档</h3><span className="step-required">必填</span></div>
                <input ref={fileInputRef} type="file" className="hidden" aria-label="选择文档文件" disabled={uploading} onChange={event => { selectFiles(event.target.files); event.target.value = '' }} />
                <div
                  className={`dropzone ${dragging ? 'dragging' : ''} ${fileError ? 'has-error' : ''} ${uploading ? 'is-busy' : ''}`}
                  onDragOver={event => event.preventDefault()}
                  onDragEnter={event => { event.preventDefault(); dragDepthRef.current += 1; if (!uploading) setDragging(true) }}
                  onDragLeave={event => { event.preventDefault(); dragDepthRef.current = Math.max(0, dragDepthRef.current - 1); if (!dragDepthRef.current) setDragging(false) }}
                  onDrop={handleDrop}
                >
                  <div className="dropzone-icon"><CloudUpload size={31} strokeWidth={1.5} /><span><Plus size={10} strokeWidth={3} /></span></div>
                  <p className="dropzone-title">{dragging ? '松开鼠标，添加这份文档' : '将文档拖到这里'}</p>
                  <p className="dropzone-subtitle">或从你的电脑中选择文件</p>
                  <button type="button" className="choose-button" disabled={uploading} onClick={() => fileInputRef.current?.click()}><Plus size={15} />{file ? '重新选择文件' : '选择文件'}</button>
                  <span className="dropzone-hint">每次上传 1 份文档 · 单个文件不超过 {MAX_UPLOAD_MB} MB</span>
                </div>
                {fileError && <p id="file-error" role="alert" className="field-error"><Info size={14} />{fileError}</p>}

                {file && <div className="selected-file">
                  <span className="file-type-icon"><FileText size={21} strokeWidth={1.6} /></span>
                  <div className="min-w-0 flex-1"><p className="truncate font-medium" title={file.name}>{file.name}</p><span>{formatFileSize(file.size)} · {uploading ? '正在上传' : '准备就绪'}</span></div>
                  {uploading ? <LoaderCircle size={18} className="animate-spin text-brand" /> : <button type="button" className="icon-button" aria-label="移除已选择的文件" onClick={() => { setFile(null); setRequestError('') }}><X size={17} /></button>}
                </div>}

                <div className="form-divider" />
                <div className="step-title"><span>02</span><h3>完善文档信息</h3></div>
                <div className="form-fields">
                  <div>
                    <label htmlFor="upload-user">上传用户 <span className="text-brand">*</span></label>
                    <div className={`input-wrap ${userError ? 'has-error' : ''}`}><UserRound size={16} /><input ref={userInputRef} id="upload-user" name="uploadUser" value={uploadUser} onChange={event => { setUploadUser(event.target.value); if (event.target.value.trim()) setUserError('') }} placeholder="填写用户名称或用户标识" autoComplete="username" disabled={uploading} required aria-invalid={Boolean(userError)} aria-describedby={userError ? 'user-error' : undefined} /></div>
                    {userError && <p id="user-error" role="alert" className="field-error">{userError}</p>}
                  </div>
                  <div>
                    <label htmlFor="accessible-by">可见范围 <span className="optional-label">选填</span></label>
                    <div className="input-wrap"><LockKeyhole size={16} /><input id="accessible-by" name="accessibleBy" value={accessibleBy} onChange={event => setAccessibleBy(event.target.value)} placeholder="按知识库约定填写可见范围" disabled={uploading} aria-describedby="visibility-hint" /></div>
                    <p id="visibility-hint" className="input-hint">实际访问权限由知识库的权限规则决定。</p>
                  </div>
                </div>

                {uploading && <div className="upload-progress">
                  <div className="mb-2 flex justify-between gap-3 text-xs"><span>{progress === 100 ? '文件已发送，正在等待保存结果…' : '正在传送你的文档…'}</span><span className="tabular-nums">{progress}%</span></div>
                  <div className="progress-track" role="progressbar" aria-label="文件传输进度" aria-valuenow={progress} aria-valuemin={0} aria-valuemax={100}><div style={{ width: `${progress}%` }} /></div>
                </div>}

                {requestError && <div role="alert" className="notice error"><Info size={18} /><p>{requestError}</p></div>}
                {notice && <div role="status" className={`notice ${notice.kind}`}>
                  {notice.kind === 'success' ? <CheckCircle2 size={18} /> : <Info size={18} />}<p>{notice.text}</p>
                </div>}

                <div className="form-actions">
                  <button type="submit" className="primary-button" disabled={uploading}>{uploading ? <LoaderCircle size={17} className="animate-spin" /> : <Upload size={17} />}<span>{uploading ? '正在上传文档…' : requestError ? '重新上传' : '上传文档'}</span>{!uploading && <ArrowRight size={17} className="ml-auto" />}</button>
                  {uploading && <button type="button" className="cancel-button" onClick={() => controllerRef.current?.abort()}>取消</button>}
                </div>
                <p className="form-footnote"><ShieldCheck size={13} />由你选择内容，为你的知识库添一份积累。</p>
              </form>
            </section>

            <aside className="guide-column" aria-label="文档上传说明">
              <div className="inspiration-panel"><span className="inspiration-eyebrow"><Sparkles size={13} /> MAKE ROOM FOR KNOWLEDGE</span><h2>让散落的知识，<br />有一个<span>好去处。</span></h2><p>一份笔记，一次思考，一篇好文章。<br />值得留下的，都可以从这里开始。</p><DocumentIllustration /><div className="inspiration-footer"><span className="tiny-spark">✦</span> 你的下一次灵感，也许就藏在其中。</div></div>
              <section className="guide-panel" id="guide" aria-labelledby="guide-title"><h2 id="guide-title"><BookOpen size={17} />三步，把文档收进知识库</h2><ol className="guide-steps"><li><span>1</span><div><h3>选一份有价值的文档</h3><p>拖拽或点击选择，添加本地文件。</p></div></li><li><span>2</span><div><h3>留下归属信息</h3><p>填写上传用户，按需设置可见范围。</p></div></li><li><span>3</span><div><h3>上传并确认结果</h3><p>保存成功后，在下方查看文档详情。</p></div></li></ol><div className="guide-note"><Info size={15} /><p>上传完成表示原始文档已保存；后续处理状态以知识库为准。</p></div></section>
            </aside>
          </div>

          <section className="records-panel" id="records" aria-labelledby="records-title">
            <div className="records-heading"><div><h2 id="records-title">本次上传记录 <span>{records.length.toString().padStart(2, '0')}</span></h2><p>每一份积累，都值得被好好收录。</p></div><span className="session-label">当前页面会话</span></div>
            {records.length === 0 ? <div className="empty-records"><div className="empty-records-icon"><FileCheck2 size={25} strokeWidth={1.4} /><span><Plus size={10} /></span></div><h3>还没有上传记录</h3><p>上传你的第一份文档，让知识从这里开始。</p></div> : <div className="records-list">
              {records.map(record => <div key={record.id} className="record-row"><span className="file-type-icon"><FileText size={21} /></span><div className="min-w-0 flex-1"><p className="truncate font-medium" title={record.document.docTitle}>{record.document.docTitle}</p><span className="record-meta">{formatFileSize(record.size)}<span>·</span>{record.document.uploadUser}<span>·</span>{timeFormatter.format(record.uploadedAt)}</span></div><span className="status-badge"><CheckCheck size={13} />{statusLabels[record.document.status] || record.document.status}</span><button type="button" className="detail-button" onClick={() => setSelectedRecord(record)}>查看详情<ArrowUpRight size={14} /></button></div>)}
            </div>}
            <div className="records-footer"><Info size={13} /><span>这里只显示本次页面打开后的成功上传记录，刷新后清空。</span></div>
          </section>

          <footer className="page-footer"><span>KnowEngine <span className="footer-dot">·</span> 让知识连接更多可能</span><a href="#upload">继续积累<ArrowUpRight size={13} /></a></footer>
        </main>
      </div>

      <dialog ref={dialogRef} className="document-dialog" aria-labelledby="document-dialog-title" onClose={() => setSelectedRecord(null)} onClick={event => { if (event.target === event.currentTarget) dialogRef.current?.close() }}>
        {selectedRecord && <>
          <div className="dialog-heading"><span className="panel-icon"><FileCheck2 size={20} /></span><h2 id="document-dialog-title">文档详情</h2><button type="button" className="icon-button ml-auto" aria-label="关闭文档详情" onClick={() => dialogRef.current?.close()}><X size={20} /></button></div>
          <p className="dialog-document-title">{selectedRecord.document.docTitle}</p>
          <dl className="document-details">
            <div><dt><Fingerprint size={15} />文档 ID</dt><dd>{selectedRecord.document.docId}</dd></div>
            <div><dt><UserRound size={15} />上传用户</dt><dd>{selectedRecord.document.uploadUser}</dd></div>
            <div><dt><File size={15} />文件大小</dt><dd>{formatFileSize(selectedRecord.size)}</dd></div>
            <div><dt><CheckCircle2 size={15} />处理状态</dt><dd>{statusLabels[selectedRecord.document.status] || selectedRecord.document.status}</dd></div>
            <div><dt><LockKeyhole size={15} />可见范围</dt><dd>{selectedRecord.document.accessibleBy || '未填写'}</dd></div>
          </dl>
          {safeDocumentUrl(selectedRecord.document.docUrl) ? <a className="primary-button" href={safeDocumentUrl(selectedRecord.document.docUrl)} target="_blank" rel="noopener noreferrer"><ArrowDownToLine size={17} />打开原始文档<ExternalLink size={15} className="ml-auto" /></a> : <p className="input-hint">服务器没有返回可直接打开的 HTTP(S) 文档地址。</p>}
          <p className="dialog-footnote">此处显示上传接口返回的处理状态。</p>
        </>}
      </dialog>
    </div>
  )
}

export default App
