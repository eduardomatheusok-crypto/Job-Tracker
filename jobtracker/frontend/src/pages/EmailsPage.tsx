import { useCallback, useEffect, useState } from 'react'
import { ArrowLeft, ArrowRight, Inbox, Loader2 } from 'lucide-react'
import { Link } from 'react-router-dom'
import { ApiError, api } from '../lib/api.ts'
import { formatDateTime } from '../lib/date.ts'
import type { Email, Page } from '../lib/types.ts'

const PAGE_SIZE = 20

export function EmailsPage() {
  const [emails, setEmails] = useState<Email[]>([])
  const [pageNumber, setPageNumber] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async (page: number) => {
    setLoading(true)
    setError(null)
    try {
      const pageData = await api<Page<Email>>(`/api/emails?page=${page}&size=${PAGE_SIZE}`)
      setEmails(pageData.content)
      setPageNumber(pageData.number)
      setTotalPages(pageData.totalPages)
      setTotalElements(pageData.totalElements)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Falha ao carregar e-mails')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load(0)
  }, [load])

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-slate-900">E-mails</h1>
        <p className="text-sm text-slate-500">{totalElements} sincronizados</p>
      </div>

      {error && (
        <div className="mb-4 rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700 ring-1 ring-inset ring-rose-200">
          {error}
        </div>
      )}

      <div className="overflow-hidden rounded-2xl bg-white shadow-sm">
        {loading ? (
          <div className="flex items-center justify-center gap-2 py-16 text-slate-400">
            <Loader2 className="size-5 animate-spin" />
            Carregando…
          </div>
        ) : emails.length === 0 ? (
          <div className="py-16 text-center">
            <span className="mx-auto flex size-12 items-center justify-center rounded-xl bg-slate-100">
              <Inbox className="size-6 text-slate-400" />
            </span>
            <p className="mt-3 text-slate-500">Nenhum e-mail sincronizado.</p>
            <p className="mt-1 text-sm text-slate-400">
              Conecte o Gmail em{' '}
              <Link to="/gmail" className="font-medium text-indigo-600 hover:text-indigo-700">
                Integração Gmail
              </Link>{' '}
              para começar.
            </p>
          </div>
        ) : (
          <ul className="divide-y divide-slate-100">
            {emails.map((email) => (
              <li key={email.id} className="px-5 py-4">
                <div className="flex items-center justify-between gap-4">
                  <div className="min-w-0">
                    <p className="truncate font-medium text-slate-900">{email.subject}</p>
                    <p className="truncate text-sm text-slate-500">{email.fromAddress}</p>
                  </div>
                  <span className="shrink-0 text-xs text-slate-400">{formatDateTime(email.receivedAt)}</span>
                </div>
                {email.snippet ? <p className="mt-2 truncate text-sm text-slate-500">{email.snippet}</p> : null}
                {email.applicationId ? (
                  <Link
                    to={`/applications/${email.applicationId}`}
                    className="mt-2 inline-flex items-center gap-1 text-xs font-medium text-indigo-600 hover:text-indigo-700"
                  >
                    Candidatura vinculada
                    <ArrowRight className="size-3.5" />
                  </Link>
                ) : (
                  <p className="mt-2 text-xs text-slate-400">Sem candidatura vinculada</p>
                )}
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
    </div>
  )
}