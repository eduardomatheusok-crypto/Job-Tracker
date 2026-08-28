import { Briefcase, Inbox, LogOut, Mail } from 'lucide-react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../lib/auth.tsx'

const NAV_LINKS = [
  { to: '/applications', label: 'Candidaturas', icon: Briefcase },
  { to: '/emails', label: 'E-mails', icon: Inbox },
  { to: '/gmail', label: 'Gmail', icon: Mail },
]

export default function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="min-h-screen bg-slate-100">
      <aside className="fixed inset-y-0 left-0 flex w-60 flex-col bg-slate-900">
        <div className="flex items-center gap-2.5 px-5 py-5">
          <span className="flex size-9 items-center justify-center rounded-lg bg-indigo-500">
            <Briefcase className="size-5 text-white" />
          </span>
          <span className="text-lg font-semibold text-white">JobTracker</span>
        </div>

        <nav className="mt-2 flex-1 space-y-1 px-3">
          {NAV_LINKS.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-indigo-500 text-white'
                    : 'text-slate-300 hover:bg-slate-800 hover:text-white'
                }`
              }
            >
              <Icon className="size-4.5" />
              {label}
            </NavLink>
          ))}
        </nav>

        <div className="border-t border-slate-800 px-5 py-4">
          <p className="truncate text-sm font-medium text-white">{user?.name ?? 'Usuário'}</p>
          <p className="truncate text-xs text-slate-400">{user?.email}</p>
          <button
            onClick={handleLogout}
            className="mt-3 flex items-center gap-2 rounded-lg px-2 py-1.5 text-sm font-medium text-slate-300 transition-colors hover:bg-slate-800 hover:text-white"
          >
            <LogOut className="size-4.5" />
            Sair
          </button>
        </div>
      </aside>

      <main className="ml-60 p-6">
        <div className="mx-auto max-w-6xl">
          <Outlet />
        </div>
      </main>
    </div>
  )
}