import request from '../utils/request'

export const login = (data: any) => request.post('/api/auth/login', data)
export const register = (data: any) => request.post('/api/auth/register', data)
export const refreshTokenApi = (token: string) => request.post('/api/auth/refresh-token', { refreshToken: token })
