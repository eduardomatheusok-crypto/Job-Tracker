import { useCallback, useEffect, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import {
  ArrowLeft,
  Building2,
  Calendar,
  ExternalLink,
  Globe,
  Loader2,
  Mail,
  MapPin,
  Pencil,
  Save,
  Tag,
  Trash2,
  X,
} from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { StatusBadge } from '../components/StatusBadge.tsx'
import { ApiError, api } from '../lib/api.ts'
import { formatDate, formatDateTime } from '../lib/date.ts'
import { STATUS_META, STATUS_ORDER } from '../lib/status.ts'
import type { Application, ApplicationHistoryItem, ApplicationStatus, Email, UpdateApplicationRequest } from '../lib/types.ts'

const INPUT_CLASS =
  'w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100'

export function ApplicationDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const applicationId = Number(id)

  const [app, setApp] = useState<Application | null>(null)
  const [history, setHistory] = useState<ApplicationHistoryItem[]>([])
  const [emails, setEmails] = useState<Email[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [appData, historyData, emailsData] = await Promise.all([
        api<Application>(`/api/applications/${applicationId}`),
        api<ApplicationHistoryItem[]>(`/api/applications/${applicationId}/history`),
        api<{ content: Email[] }>(`/api/emails?applicationId=${applicationId}&size=50`),
      ])
      setApp(appData)
      setHistory(historyData)
      setEmails(emailsData.content)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Falha ao carregar candidatura')
    } finally {
      setLoading(false)
    }
  }, [applicationId])

  useEffect(() => {
    void load()
  }, [load])

  const handleDeleted = async () => {
    navigate('/applications')
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center gap-2 py-16 text-slate-400">
        <Loader2 className="size-5 animate-spin" />
        Carregando…
      </div>
    )
  }

  if (error || !app) {
    return (
      <div>
        <div className="mb-4 rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700 ring-1 ring-inset ring-rose-200">
          {error ?? 'Candidatura não encontrada'}
        </div>
        <Link to="/applications" className="text-sm font-medium text-indigo-600 hover:text-indigo-700">
          ← Voltar para candidaturas
        </Link>
      </div>
    )
  }

  return (
    <div>
      <Link
        to="/applications"
        className="mb-4 inline-flex items-center gap-1.5 text-sm font-medium text-indigo-600 hover:text-indigo-700"
      >
        <ArrowLeft className="size-4" />
        Voltar para candidaturas
      </Link>

      <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{app.companyName}</h1>
            <StatusBadge status={app.status} />
          </div>
          <p className="mt-1 text-sm text-slate-500">{app.position}</p>
        </div>
        <div className="flex items-center gap-2">
          {!editing && (
            <button
              onClick={() => setEditing(true)}
              className="flex items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
            >
              <Pencil className="size-4" />
              Editar
            </button>
          )}
          <DeleteButton id={app.id} onDeleted={handleDeleted} />
        </div>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <InformationCard app={app} />

          {editing ? (
            <EditCard app={app} onCancel={() => setEditing(false)} onSaved={() => { setEditing(false); void load() }} />
          ) : (
            <HistoryCard history={history} />
          )}
        </div>

        <div className="space-y-6">
          <EmailsCard emails={emails} />
        </div>
      </div>
    </div>
  )
}

function InformationCard({ app }: { app: Application }) {
  return (
    <section className="rounded-2xl bg-white p-6 shadow-sm">
      <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500">Informações</h2>
      <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <InfoRow icon={Building2} label="Plataforma" value={app.platform} />
        <InfoRow icon={MapPin} label="Localização" value={app.location} />
        <InfoRow icon={Calendar} label="Data da candidatura" value={formatDate(app.appliedAt)} />
        <InfoRow icon={Calendar} label="Criada em" value={formatDateTime(app.createdAt)} />
        <div className="sm:col-span-2">
          <dt className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-slate-400">
            <Globe className="size-3.5" />
            Link da vaga
          </dt>
          <dd className="mt-1">
            {app.jobUrl ? (
              <a
                href={app.jobUrl}
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center gap-1 text-sm text-indigo-600 hover:text-indigo-700"
              >
                {app.jobUrl} <ExternalLink className="size-3.5" />
              </a>
            ) : (
              <span className="text-sm text-slate-500">—</span>
            )}
          </dd>
        </div>
        <div className="sm:col-span-2">
          <dt className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-slate-400">
            <Tag className="size-3.5" />
            Notas
          </dt>
          <dd className="mt-1 whitespace-pre-wrap text-sm text-slate-700">{app.notes || '—'}</dd>
        </div>
      </dl>
    </section>
  )
}

function InfoRow({
  icon: Icon,
  label,
  value,
}: {
  icon: typeof Building2
  label: string
  value: string | null | undefined
}) {
  return (
    <div>
      <dt className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-slate-400">
        <Icon className="size-3.5" />
        {label}
      </dt>
      <dd className="mt-1 text-sm text-slate-700">{value || '—'}</dd>
    </div>
  )
}

interface EditCardProps {
  app: Application
  onCancel: () => void
  onSaved: () => void
}

function EditCard({ app, onCancel, onSaved }: EditCardProps) {
  const [companyName, setCompanyName] = useState(app.companyName)
  const [position, setPosition] = useState(app.position)
  const [location, setLocation] = useState(app.location ?? '')
  const [jobUrl, setJobUrl] = useState(app.jobUrl ?? '')
  const [platform, setPlatform] = useState(app.platform ?? '')
  const [notes, setNotes] = useState(app.notes ?? '')
  const [status, setStatus] = useState<ApplicationStatus>(app.status)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const payload: UpdateApplicationRequest = {
        companyName: companyName.trim(),
        position: position.trim(),
        status,
      }
      if (location.trim()) payload.location = location.trim()
      if (jobUrl.trim()) payload.jobUrl = jobUrl.trim()
      if (platform.trim()) payload.platform = platform.trim()
      if (notes.trim()) payload.notes = notes.trim()
      await api(`/api/applications/${app.id}`, { method: 'PUT', body: JSON.stringify(payload) })
      onSaved()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Falha ao salvar alterações')
      setSubmitting(false)
    }
  }

  return (
    <section className="rounded-2xl bg-white p-6 shadow-sm">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">Editar candidatura</h2>
        <button onClick={onCancel} className="rounded-lg p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600">
          <X className="size-5" />
        </button>
      </div>

      {error && (
        <div className="mb-4 rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700 ring-1 ring-inset ring-rose-200">
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field label="Empresa *">
            <input required value={companyName} onChange={(e) => setCompanyName(e.target.value)} className={INPUT_CLASS} />
          </Field>
          <Field label="Cargo *">
            <input required value={position} onChange={(e) => setPosition(e.target.value)} className={INPUT_CLASS} />
          </Field>
          <Field label="Localização">
            <input value={location} onChange={(e) => setLocation(e.target.value)} className={INPUT_CLASS} />
          </Field>
          <Field label="Plataforma">
            <input value={platform} onChange={(e) => setPlatform(e.target.value)} className={INPUT_CLASS} />
          </Field>
          <div className="sm:col-span-2">
            <Field label="Link da vaga">
              <input type="url" value={jobUrl} onChange={(e) => setJobUrl(e.target.value)} className={INPUT_CLASS} />
            </Field>
          </div>
          <div className="sm:col-span-2">
            <Field label="Status">
              <select value={status} onChange={(e) => setStatus(e.target.value as ApplicationStatus)} className={INPUT_CLASS}>
                {STATUS_ORDER.map((s) => (
                  <option key={s} value={s}>
                    {STATUS_META[s].label}
                  </option>
                ))}
              </select>
            </Field>
          </div>
          <div className="sm:col-span-2">
            <Field label="Notas">
              <textarea rows={3} value={notes} onChange={(e) => setNotes(e.target.value)} className={INPUT_CLASS} />
            </Field>
          </div>
        </div>

        <div className="flex justify-end gap-3 pt-2">
          <button
            type="button"
            onClick={onCancel}
            className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            Cancelar
          </button>
          <button
            type="submit"
            disabled={submitting}
            className="flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-60"
          >
            {submitting ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />}
            Salvar
          </button>
        </div>
      </form>
    </section>
  )
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <label className="mb-1 block text-sm font-medium text-slate-700">{label}</label>
      {children}
    </div>
  )
}

function HistoryCard({ history }: { history: ApplicationHistoryItem[] }) {
  return (
    <section className="rounded-2xl bg-white p-6 shadow-sm">
      <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500">Histórico</h2>
      {history.length === 0 ? (
        <p className="text-sm text-slate-500">Sem registros de histórico.</p>
      ) : (
        <ol className="relative ml-3 space-y-5 border-l border-slate-200 pl-6">
          {history.map((item) => (
            <li key={item.id} className="relative">
              <span className="absolute -left-[1.85rem] top-1 size-2.5 rounded-full bg-indigo-400 ring-4 ring-white" />
              <p className="text-xs text-slate-400">{formatDateTime(item.changedAt)}</p>
              <div className="mt-0.5 flex flex-wrap items-center gap-2 text-sm">
                {item.previousStatus ? (
                  <>
                    <StatusBadge status={item.previousStatus} />
                    <span className="text-slate-400">→</span>
                  </>
                ) : null}
                <StatusBadge status={item.newStatus} />
              </div>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}

function EmailsCard({ emails }: { emails: Email[] }) {
  return (
    <section className="rounded-2xl bg-white p-6 shadow-sm">
      <h2 className="mb-4 flex items-center gap-2 text-sm font-semibold uppercase tracking-wide text-slate-500">
        <Mail className="size-4" />
        E-mails vinculados
      </h2>
      {emails.length === 0 ? (
        <p className="text-sm text-slate-500">Nenhum e-mail vinculado a esta candidatura.</p>
      ) : (
        <ul className="space-y-3">
          {emails.map((email) => (
            <li key={email.id} className="rounded-lg border border-slate-100 bg-slate-50 p-3">
              <p className="truncate text-sm font-medium text-slate-800">{email.subject}</p>
              <p className="truncate text-xs text-slate-500">{email.fromAddress}</p>
              <p className="mt-1 text-xs text-slate-400">{formatDateTime(email.receivedAt)}</p>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function DeleteButton({ id, onDeleted }: { id: number; onDeleted: () => void }) {
  const [confirming, setConfirming] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleConfirm = async () => {
    setDeleting(true)
    setError(null)
    try {
      await api(`/api/applications/${id}`, { method: 'DELETE' })
      onDeleted()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Falha ao excluir candidatura')
      setDeleting(false)
    }
  }

  if (!confirming) {
    return (
      <button
        onClick={() => setConfirming(true)}
        className="flex items-center gap-2 rounded-lg border border-rose-200 bg-white px-3 py-2 text-sm font-medium text-rose-600 hover:bg-rose-50"
      >
        <Trash2 className="size-4" />
        Excluir
      </button>
    )
  }

  return (
    <div className="flex items-center gap-2">
      {error && <span className="text-xs text-rose-600">{error}</span>}
      <span className="text-sm text-slate-500">Confirmar exclusão?</span>
      <button
        onClick={handleConfirm}
        disabled={deleting}
        className="flex items-center gap-2 rounded-lg bg-rose-600 px-3 py-2 text-sm font-medium text-white hover:bg-rose-700 disabled:opacity-60"
      >
        {deleting && <Loader2 className="size-4 animate-spin" />}
        Confirmar
      </button>
      <button
        onClick={() => setConfirming(false)}
        className="rounded-lg border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
      >
        Cancelar
      </button>
    </div>
  )
}