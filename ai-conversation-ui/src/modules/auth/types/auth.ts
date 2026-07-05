export interface LoginPayload {
  username: string
  password: string
  tenantId: string
  credentialType: 'PASSWORD'
}

export interface LoginResult {
  token?: string
  user?: unknown
}
