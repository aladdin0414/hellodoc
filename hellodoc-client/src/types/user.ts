export interface UserProfile {
    id: number
    username: string
    nickname?: string
    email?: string
    avatar?: string
    role: 'admin' | 'user' | string
    status?: 'active' | 'disabled' | string
    createdAt?: string
}

export interface LoginParams {
    username: string
    password?: string
}

export interface AuthResponse {
    accessToken: string
    refreshToken: string
    user: UserProfile
}
