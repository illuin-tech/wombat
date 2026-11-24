export interface AppUser {
  authenticated: boolean
  name: string | null
  picture?: string | null
  roles: string[]
  permissions: string[]
}

export interface AppInfo {
  auth: { enabled: boolean }
  user: AppUser
  ui: { maxDateRange: { millis: number; label: string } }
}
