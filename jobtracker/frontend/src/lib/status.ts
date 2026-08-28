import type { ApplicationStatus } from '../lib/types.ts'

export const STATUS_META: Record<ApplicationStatus, { label: string; badge: string; dot: string }> = {
  SAVED: {
    label: 'Salva',
    badge: 'bg-slate-100 text-slate-700 ring-slate-200',
    dot: 'bg-slate-400',
  },
  APPLIED: {
    label: 'Candidatada',
    badge: 'bg-blue-50 text-blue-700 ring-blue-200',
    dot: 'bg-blue-500',
  },
  IN_PROCESS: {
    label: 'Em processo',
    badge: 'bg-amber-50 text-amber-700 ring-amber-200',
    dot: 'bg-amber-500',
  },
  INTERVIEW: {
    label: 'Entrevista',
    badge: 'bg-violet-50 text-violet-700 ring-violet-200',
    dot: 'bg-violet-500',
  },
  REJECTED: {
    label: 'Rejeitada',
    badge: 'bg-rose-50 text-rose-700 ring-rose-200',
    dot: 'bg-rose-500',
  },
  ACCEPTED: {
    label: 'Aprovada',
    badge: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
    dot: 'bg-emerald-500',
  },
}

export const STATUS_ORDER: ApplicationStatus[] = [
  'SAVED',
  'APPLIED',
  'IN_PROCESS',
  'INTERVIEW',
  'REJECTED',
  'ACCEPTED',
]