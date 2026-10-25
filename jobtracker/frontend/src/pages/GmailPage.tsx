import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { ExternalLink, KeyRound, Loader2, Mail, RefreshCw, Trash2, Unplug } from 'lucide-react'
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
  const [gmailEmail, setGmailEmail] = useState('')
  const [appPassword, setAppPassword] = useState('')
  const [savingPassword, setSavingPassword] = useState(false)

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

  const handleConnectAppPassword = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setMessage(null)
    setSavingPassword(true)
    try {
      const result = await api<{ message: string }>('/api/gmail/connect-app-password', {
        method: 'POST',
        body: JSON.stringify({ email: gmailEmail.trim(), appPassword }),
      })
      setAppPassword('')
      setMessage(result.message)
      await loadStatus()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Falha ao conectar o Gmail')
    } finally {
      setSavingPassword(false)
    }
  }

  const handleSync = async () => {
    setError(null)
    setMessage(null)
    setSyncing(true)
    try {
      const result = await api<SyncResponse>('/api/gmail/sync', { method: 'POST' })
      if (result.connected === false) {
        setError(result.message || 'Gmail desconectado. Por favor, reconecte sua conta.')
      } else {
        setMessage(`${result.emailsProcessed} e-mail(ns) processado(s).`)
      }
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
          <div>
            <div className="text-center">
              <span className="mx-auto flex size-12 items-center justify-center rounded-xl bg-slate-100">
                <Mail className="size-6 text-slate-500" />
              </span>
              <h2 className="mt-4 text-lg font-semibold text-slate-900">Conectar sua conta Gmail</h2>
              <p className="mt-1 text-sm text-slate-500">
                Ao conectar, o JobTracker poderá ler e-mails sobre candidaturas para criar e atualizar suas vagas.
              </p>
            </div>

            <form onSubmit={handleConnectAppPassword} className="mt-5 space-y-3">
              <div>
                <label htmlFor="gmail-email" className="block text-sm font-medium text-slate-700">E-mail do Gmail</label>
                <input
                  id="gmail-email"
                  type="email"
                  required
                  autoComplete="email"
                  value={gmailEmail}
                  onChange={(e) => setGmailEmail(e.target.value)}
                  placeholder="voce@gmail.com"
                  className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                />
              </div>
              <div>
                <label htmlFor="gmail-app-password" className="block text-sm font-medium text-slate-700">Senha de app</label>
                <input
                  id="gmail-app-password"
                  type="password"
                  required
                  autoComplete="off"
                  value={appPassword}
                  onChange={(e) => setAppPassword(e.target.value)}
                  placeholder="xxxx xxxx xxxx xxxx"
                  className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 font-mono text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                />
                <p className="mt-1 text-xs text-slate-500">
                  Exige verificação em 2 etapas.{' '}
                  <a
                    href="https://myaccount.google.com/apppasswords"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-0.5 font-medium text-indigo-600 hover:underline"
                  >
                    Gerar senha de app <ExternalLink className="size-3" />
                  </a>
                  . Ela é guardada criptografada e não é a senha da sua conta.
                </p>
              </div>
              <button
                type="submit"
                disabled={savingPassword}
                className="inline-flex w-full items-center justify-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-60"
              >
                {savingPassword ? <Loader2 className="size-4 animate-spin" /> : <KeyRound className="size-4" />}
                {savingPassword ? 'Verificando…' : 'Conectar Gmail'}
              </button>
            </form>

            <div className="mt-5 border-t border-slate-100 pt-4 text-center">
              <button
                onClick={handleConnect}
                disabled={connecting}
                className="inline-flex items-center gap-2 text-sm font-medium text-slate-500 hover:text-slate-700 disabled:opacity-60"
              >
                {connecting ? <Loader2 className="size-4 animate-spin" /> : <Unplug className="size-4" />}
                {connecting ? 'Aguardando autorização…' : 'Ou conectar com login do Google (expira a cada 7 dias)'}
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