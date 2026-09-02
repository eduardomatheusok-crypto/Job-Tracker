import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { ArrowLeft, ArrowRight, Loader2, Plus, Search, X } from 'lucide-react'
import { Link } from 'react-router-dom'
import { ApiError, api, cachedApi } from '../lib/api.ts'
import { formatDate } from '../lib/date.ts'
import { STATUS_META, STATUS_ORDER } from '../lib/status.ts'
import type { Application, ApplicationRequest, ApplicationStatus, ApplicationSummary, Page } from '../lib/types.ts'
import { StatusBadge } from '../components/StatusBadge.tsx'

const PAGE_SIZE = 20

const INPUT_CLASS =
  'w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100'

export function ApplicationsPage() {
  const [applications, setApplications] = useState<Application[]>([])
  const [summary, setSummary] = useState<ApplicationSummary | null>(null)
  const [pageNumber, setPageNumber] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showCreate, setShowCreate] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')

  const filteredApplications = useMemo(() => {
    const q = searchQuery.trim().toLowerCase()
    if (!q) return applications
    return applications.filter(
      (app) =>
        app.companyName.toLowerCase().includes(q) ||
        app.position.toLowerCase().includes(q) ||
        (app.platform ?? '').toLowerCase().includes(q) ||
        (app.location ?? '').toLowerCase().includes(q),
    )
  }, [applications, searchQuery])

  const load = useCallback(async (page: number) => {
    setLoading(true)
    setError(null)
    try {
      const [summaryData, pageData] = await Promise.all([
        cachedApi<ApplicationSummary>('/api/applications/summary'),
        cachedApi<Page<Application>>(`/api/applications?page=${page}&size=${PAGE_SIZE}`),
      ])
      setSummary(summaryData)
      setApplications(pageData.content)
      setPageNumber(pageData.number)
      setTotalPages(pageData.totalPages)
      setTotalElements(pageData.totalElements)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Falha ao carregar candidaturas')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load(0)
  }, [load])

  const handleCreated = async () => {
    setShowCreate(false)
    await load(0)
  }

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Candidaturas</h1>
          <p className="text-sm text-slate-500">{totalElements} no total</p>
        </div>
        <button
          onClick={() => setShowCreate(true)}
          className="flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-indigo-700"
        >
          <Plus className="size-4" />
          Nova candidatura
        </button>
      </div>

      {summary && (
        <div className="mb-6 grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-7">
          <div className="rounded-xl bg-white p-3 shadow-sm">
            <p className="text-xs font-medium uppercase tracking-wide text-slate-500">Total</p>
            <p className="mt-1 text-2xl font-semibold text-slate-900">{summary.total}</p>
          </div>
          {STATUS_ORDER.map((status) => (
            <div key={status} className="rounded-xl bg-white p-3 shadow-sm">
              <p className="text-xs font-medium uppercase tracking-wide text-slate-500">
                {STATUS_META[status].label}
              </p>
              <p className="mt-1 text-2xl font-semibold text-slate-900">{summary.byStatus[status] ?? 0}</p>
            </div>
          ))}
        </div>
      )}

      {error && (
        <div className="mb-4 rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700 ring-1 ring-inset ring-rose-200">
          {error}
        </div>
      )}

      <div className="relative mb-4">
        <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
        <input
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Buscar por empresa, cargo, plataforma ou localização…"
          className="w-full rounded-lg border border-slate-300 py-2 pl-9 pr-9 text-sm outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-100"
        />
        {searchQuery && (
          <button
            onClick={() => setSearchQuery('')}
            className="absolute right-3 top-1/2 -translate-y-1/2 rounded p-0.5 text-slate-400 hover:text-slate-600"
          >
            <X className="size-4" />
          </button>
        )}
      </div>

      <div className="overflow-hidden rounded-2xl bg-white shadow-sm">
        {loading ? (
          <div className="flex items-center justify-center gap-2 py-16 text-slate-400">
            <Loader2 className="size-5 animate-spin" />
            Carregando…
          </div>
        ) : filteredApplications.length === 0 ? (
          <div className="py-16 text-center">
            <p className="text-slate-500">
              {searchQuery ? 'Nenhuma candidatura encontrada para essa busca.' : 'Nenhuma candidatura ainda.'}
            </p>
            {!searchQuery && (
              <button
                onClick={() => setShowCreate(true)}
                className="mt-3 inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700"
              >
                <Plus className="size-4" />
                Criar a primeira
              </button>
            )}
          </div>
        ) : (
          <ul className="divide-y divide-slate-100">
            {filteredApplications.map((app) => (
              <li key={app.id}>
                <Link
                  to={`/applications/${app.id}`}
                  className="flex items-center justify-between gap-4 px-5 py-4 transition-colors hover:bg-slate-50"
                >
                  <div className="min-w-0">
                    <p className="truncate font-medium text-slate-900">{app.companyName}</p>
                    <p className="truncate text-sm text-slate-500">
                      {app.position}
                      {app.platform ? ` · ${app.platform}` : ''}
                      {app.location ? ` · ${app.location}` : ''}
                    </p>
                  </div>
                  <div className="flex shrink-0 items-center gap-4">
                    <span className="hidden text-sm text-slate-500 sm:block">
                      {formatDate(app.appliedAt ?? app.createdAt)}
                    </span>
                    <StatusBadge status={app.status} />
                    <ArrowRight className="size-4 text-slate-300" />
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>

      {totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between">
          <button
            onClick={() => void load(pageNumber - 1)}
            disabled={pageNumber === 0 || loading}
            className="flex items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
          >
            <ArrowLeft className="size-4" />
            Anterior
          </button>
          <span className="text-sm text-slate-500">
            Página {pageNumber + 1} de {totalPages}
          </span>
          <button
            onClick={() => void load(pageNumber + 1)}
            disabled={pageNumber >= totalPages - 1 || loading}
            className="flex items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
          >
            Próxima
            <ArrowRight className="size-4" />
          </button>
        </div>
      )}

      {showCreate && <CreateApplicationModal onClose={() => setShowCreate(false)} onCreated={handleCreated} />}
    </div>
  )
}

interface CreateApplicationModalProps {
  onClose: () => void
  onCreated: () => void
}

function CreateApplicationModal({ onClose, onCreated }: CreateApplicationModalProps) {
  const [companyName, setCompanyName] = useState('')
  const [position, setPosition] = useState('')
  const [location, setLocation] = useState('')
  const [jobUrl, setJobUrl] = useState('')
  const [platform, setPlatform] = useState('')
  const [notes, setNotes] = useState('')
  const [status, setStatus] = useState<ApplicationStatus>('SAVED')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const payload: ApplicationRequest = {
        companyName: companyName.trim(),
        position: position.trim(),
        status,
      }
      if (location.trim()) payload.location = location.trim()
      if (jobUrl.trim()) payload.jobUrl = jobUrl.trim()
      if (platform.trim()) payload.platform = platform.trim()
      if (notes.trim()) payload.notes = notes.trim()
      await api('/api/applications', { method: 'POST', body: JSON.stringify(payload) })
      onCreated()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Falha ao criar candidatura')
      setSubmitting(false)
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4"
      onClick={onClose}
    >
      <div
        className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-2xl bg-white p-6 shadow-lg"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-slate-900">Nova candidatura</h2>
          <button
            onClick={onClose}
            className="rounded-lg p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
          >
            <X className="size-5" />
          </button>
        </div>

        {error && (
          <div className="mb-4 rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700 ring-1 ring-inset ring-rose-200">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <Field label="Empresa *">
            <input required value={companyName} onChange={(e) => setCompanyName(e.target.value)} className={INPUT_CLASS} />
          </Field>
          <Field label="Cargo *">
            <input required value={position} onChange={(e) => setPosition(e.target.value)} className={INPUT_CLASS} />
          </Field>
          <div className="grid grid-cols-2 gap-4">
            <Field label="Localização">
              <input value={location} onChange={(e) => setLocation(e.target.value)} className={INPUT_CLASS} />
            </Field>
            <Field label="Plataforma">
              <input value={platform} onChange={(e) => setPlatform(e.target.value)} className={INPUT_CLASS} />
            </Field>
          </div>
          <Field label="Link da vaga">
            <input type="url" value={jobUrl} onChange={(e) => setJobUrl(e.target.value)} className={INPUT_CLASS} />
          </Field>
          <Field label="Status">
            <select value={status} onChange={(e) => setStatus(e.target.value as ApplicationStatus)} className={INPUT_CLASS}>
              {STATUS_ORDER.map((s) => (
                <option key={s} value={s}>
                  {STATUS_META[s].label}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Notas">
            <textarea rows={3} value={notes} onChange={(e) => setNotes(e.target.value)} className={INPUT_CLASS} />
          </Field>

          <div className="flex justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-60"
            >
              {submitting && <Loader2 className="size-4 animate-spin" />}
              Salvar
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

interface FieldProps {
  label: string
  children: ReactNode
}

function Field({ label, children }: FieldProps) {
  return (
    <div>
      <label className="mb-1 block text-sm font-medium text-slate-700">{label}</label>
      {children}
    </div>
  )
}