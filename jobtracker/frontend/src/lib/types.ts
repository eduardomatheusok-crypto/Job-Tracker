export type ApplicationStatus =
  | 'SAVED'
  | 'APPLIED'
  | 'IN_PROCESS'
  | 'INTERVIEW'
  | 'REJECTED'
  | 'ACCEPTED'

export interface User {
  id: number
  name: string
  email: string
  role: string
  createdAt: string
  updatedAt: string
}

export interface AuthResponse {
  token: string
  tokenType: string
  user: User
}

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  name: string
  email: string
  password: string
}

export interface Application {
  id: number
  userId: number
  companyName: string
  position: string
  location: string | null
  jobUrl: string | null
  platform: string | null
  notes: string | null
  status: ApplicationStatus
  appliedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface ApplicationRequest {
  companyName: string
  position: string
  location?: string
  jobUrl?: string
  platform?: string
  notes?: string
  status?: ApplicationStatus
}

export interface UpdateApplicationRequest {
  companyName?: string
  position?: string
  location?: string
  jobUrl?: string
  platform?: string
  notes?: string
  status?: ApplicationStatus
}

export interface ApplicationSummary {
  total: number
  byStatus: Record<ApplicationStatus, number>
}

export interface ApplicationHistoryItem {
  id: number
  applicationId: number
  changedByUserId: number | null
  previousStatus: ApplicationStatus | null
  newStatus: ApplicationStatus
  changedField: string | null
  note: string | null
  changedAt: string
}

export interface Email {
  id: number
  userId: number
  applicationId: number | null
  messageId: string
  subject: string
  fromAddress: string
  snippet: string
  rawContent: string
  receivedAt: string
  processedAt: string | null
  createdAt: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}

export interface GmailStatus {
  connected: boolean
  gmailAddress: string
  lastSyncedAt: string
}

export interface SyncResponse {
  message: string
  emailsProcessed: number
}