export const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

const TOKEN_KEY = 'jobtracker.token'
const USER_KEY = 'jobtracker.user'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function getUser(): unknown | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as unknown
  } catch {
    return null
  }
}

export function setAuth(token: string, user: unknown): void {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

const UNAUTHORIZED_EVENT = 'jobtracker:unauthorized'

export function onUnauthorized(listener: () => void): () => void {
  const handler = () => {
    clearAuth()
    listener()
  }
  window.addEventListener(UNAUTHORIZED_EVENT, handler)
  return () => window.removeEventListener(UNAUTHORIZED_EVENT, handler)
}

export function fireUnauthorized(): void {
  window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
}

export interface ApiErrorBody {
  error?: string
  message?: string
  details?: string[]
}

export class ApiError extends Error {
  readonly status: number
  readonly error: string
  readonly details?: string[]

  constructor(status: number, error: string, message: string, details?: string[]) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.error = error
    this.details = details
  }
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  headers.set('Content-Type', 'application/json')
  const token = getToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(`${API_URL}${path}`, { ...options, headers })

  if (response.status === 401) {
    fireUnauthorized()
    throw new ApiError(401, 'Unauthorized', 'Sessão expirada, faça login novamente')
  }

  if (!response.ok) {
    let error = 'Erro'
    let message = `Requisição falhou (${response.status})`
    let details: string[] | undefined
    try {
      const body = (await response.json()) as ApiErrorBody
      error = body.error ?? error
      message = body.message ?? message
      details = body.details
    } catch {
      // corpo não-JSON; mantém mensagem padrão
    }
    throw new ApiError(response.status, error, message, details)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}