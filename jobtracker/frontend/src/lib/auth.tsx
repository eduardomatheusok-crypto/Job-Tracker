import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { api, getUser, onUnauthorized, setAuth, clearAuth } from './api.ts'
import type { LoginRequest, RegisterRequest, User } from './types.ts'

interface AuthContextValue {
  user: User | null
  login: (payload: LoginRequest) => Promise<void>
  register: (payload: RegisterRequest) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => (getUser() as User | null))

  useEffect(() => onUnauthorized(() => setUser(null)), [])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      login: async (payload) => {
        const auth = await api<{ token: string; tokenType: string; user: User }>('/api/auth/login', {
          method: 'POST',
          body: JSON.stringify(payload),
        })
        setAuth(auth.token, auth.user)
        setUser(auth.user)
      },
      register: async (payload) => {
        const auth = await api<{ token: string; tokenType: string; user: User }>('/api/auth/register', {
          method: 'POST',
          body: JSON.stringify(payload),
        })
        setAuth(auth.token, auth.user)
        setUser(auth.user)
      },
      logout: async () => {
        try {
          await api<void>('/api/auth/logout', { method: 'POST' })
        } catch {
          // falha no logout no servidor não impede logout local
        }
        clearAuth()
        setUser(null)
      },
    }),
    [user],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth deve ser usado dentro de AuthProvider')
  }
  return ctx
}