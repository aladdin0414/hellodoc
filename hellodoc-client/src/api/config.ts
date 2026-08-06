import request from '../utils/request'

// 获取所有配置项 (管理员)
export const listConfigs = () => request.get('/api/system/configs')

// 更新配置项 (管理员)
export const updateConfig = (data: any) => request.put('/api/system/configs', data)

// 创建配置项 (管理员)
export const createConfig = (data: any) => request.post('/api/system/configs', data)

// 刷新配置缓存 (管理员)
export const refreshConfigCache = () => request.post('/api/system/configs/refresh')

// 获取前端公开配置 (公开)
export const getFrontendConfigs = () => request.get('/api/public/configs/frontend')
