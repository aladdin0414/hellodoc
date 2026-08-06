import request from '../utils/request'

export const getMe = () => request.get('/api/users/me')
export const updateProfile = (data: any) => request.put('/api/users/profile', data)
export const updateLanguageMode = (data: { languageMode: 'AUTO' | 'zh-CN' | 'en-US' }) => request.put('/api/users/language', data)
export const uploadAvatar = (formData: FormData) => request.post('/api/users/avatar', formData)
export const changePassword = (data: any) => request.put('/api/users/change-pwd', data)
export const searchUsers = (keyword: string) => request.get('/api/users/search', { params: { keyword } })

// 管理员接口
export const listUsers = (paramsNoPage: any, pageNum: number = 1, pageSize: number = 10) => 
  request.get('/api/users', { params: { ...paramsNoPage, pageNum, pageSize } })
export const createUser = (data: any) => request.post('/api/users', data)
export const updateUser = (id: number, data: any) => request.put(`/api/users/${id}`, data)
export const deleteUser = (id: number) => request.delete(`/api/users/${id}`)
export const initPassword = (id: number) => request.put(`/api/users/${id}/init-pwd`)
