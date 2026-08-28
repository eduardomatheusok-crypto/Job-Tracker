import { useCallback, useEffect, useRef, useState } from 'react'
import { Loader2, Mail, RefreshCw, Trash2, Unplug } from 'lucide-react'
import { ApiError, api } from '../lib/api.ts'
import { formatDateTime } from '../lib/date.ts'
import type { GmailStatus, SyncResponse } from '../lib/types.ts'

export function GmailPage() {
  const [status, setStatus] = useState<GmailStatus | null>(null)
  const [loading, setLoading] = useState(true)
  const [connecting, setConnecting] = useState(false)
  const [syncing, setSyncing] = useState(false)
  const [disconnecting, setDisconnecting] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const pollRef = useRef<number | undefined>(undefined)

  const loadStatus = useCallback(async () => {
    setError(null)
    try {
      setStatus(await api<GmailStatus>('/api/gmail/status'))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Falha ao verificar status do Gmail')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadStatus()
    return () => window.clearInterval(pollRef.current)
  }, [loadStatus])

  const stopPolling = () => {
    if (pollRef.current) {
      window.clearInterval(pollRef.current)
      pollRef.current = undefined
    }
    setConnecting(false)
  }

  const handleConnect = async () => {
    setError(null)
    setMessage(null)
    try {
      const { authUrl } = await api<{ authUrl: string }>('/api/gmail/auth-url')
      window.open(authUrl, '_blank', 'noopener,noreferrer')
      setConnecting(true)
      setMessage('Autorize o acesso na janela aberta. Aguardando confirmação…')

      let attempts = 0
      pollRef.current = window.setInterval(async () => {
        attempts += 1
        try {
          const freshStatus = await api<GmailStatus>('/api/gmail/status')
          setStatus(freshStatus)
          if (freshStatus.connected) {
            setMessage('Gmail conectado com sucesso!')
            stopPolling()
          } else if (attempts >= 20) {
            setMessage('Não detectamos a conexão. Se aprovou o acesso, clique em "Verificar agora".')
            stopPolling()
          }
        } catch {
          // mantém a sondagem até o limite de tentativas
        }
      }, 3000)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Falha ao iniciar conexão com o Gmail')
    }
  }

  const handleSync = async () => {
    setError(null)
    setMessage(null)
    setSyncing(true)
    try {
      const result = await api<SyncResponse>('/api/gmail/sync', { method: 'POST' })
      setMessage(`${result.emailsProcessed} e-mail(ns) processado(s).`)
      await loadStatus()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Falha na sincronização')
    } finally {
      setSyncing(false)
    }
  }

  const handleDisconnect = async () => {
    setError(null)
    setMessage(null)
    setDisconnecting(true)
    try {
      const result = await api<{ message: string }>('/api/gmail/disconnect', { method: 'POST' })
      setMessage(result.message)
      await loadStatus()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Falha ao desconectar o Gmail')
    } finally {
      setDisconnecting(false)
    }
  }

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-slate-900">Integração Gmail</h1>
        <p className="text-sm text-slate-500">Sincronize e-mails de processos seletivos e atualize suas candidaturas.</p>
      </div>

      {error && (
        <div className="mb-4 rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700 ring-1 ring-inset ring-rose-200">
          {error}
        </div>
      )}
      {message && (
        <div className="mb-4 rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-700 ring-1 ring-inset ring-emerald-200">
          {message}
        </div>
      )}

      <div className="max-w-xl rounded-2xl bg-white p-6 shadow-sm">
        {loading ? (
          <div className="flex items-center justify-center gap-2 py-8 text-slate-400">
            <Loader2 className="size-5 animate-spin" />
            Carregando…
          </div>
        ) : !status?.connected ? (
          <div className="text-center">
            <span className="mx-auto flex size-12 items-center justify-center rounded-xl bg-slate-100">
              <Mail className="size-6 text-slate-500" />
            </span>
            <h2 className="mt-4 text-lg font-semibold text-slate-900">Conectar sua conta Gmail</h2>
            <p className="mt-1 text-sm text-slate-500">
              Ao conectar, o JobTracker poderá ler e-mails sobre candidaturas para criar e atualizar suas vagas.
            </p>
            <button
              onClick={handleConnect}
              disabled={connecting}
              className="mt-5 inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-60"
            >
              {connecting ? <Loader2 className="size-4 animate-spin" /> : <Unplug className="size-4" />}
              {connecting ? 'Aguardando autorização…' : 'Conectar Gmail'}
            </button>
            {connecting && (
              <button
                onClick={() => { stopPolling(); void loadStatus() }}
                className="ml-2 inline-flex items-center gap-2 rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
              >
                <RefreshCw className="size-4" />
                Verificar agora
              </button>
            )}
          </div>
        ) : (
          <div>
            <div className="flex items-center gap-3">
              <span className="flex size-12 items-center justify-center rounded-xl bg-emerald-100">
                <Mail className="size-6 text-emerald-600" />
              </span>
              <div>
                <p className="font-semibold text-slate-900">{status.gmailAddress || 'Conta conectada'}</p>
                <p className="text-sm text-slate-500">
                  {status.lastSyncedAt ? `Última sincronização: ${formatDateTime(status.lastSyncedAt)}` : 'Nunca sincronizado'}
                </p>
              </div>
            </div>

            <div className="mt-5 flex flex-wrap items-center gap-2 border-t border-slate-100 pt-5">
              <button
                onClick={handleSync}
                disabled={syncing}
                className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-60"
              >
                {syncing ? <Loader2 className="size-4 animate-spin" /> : <RefreshCw className="size-4" />}
                {syncing ? 'Sincronizando…' : 'Sincronizar agora'}
              </button>
              <button
                onClick={handleDisconnect}
                disabled={disconnecting}
                className="inline-flex items-center gap-2 rounded-lg border border-rose-200 px-4 py-2 text-sm font-medium text-rose-600 hover:bg-rose-50 disabled:opacity-60"
              >
                {disconnecting ? <Loader2 className="size-4 animate-spin" /> : <Trash2 className="size-4" />}
                Desconectar
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}