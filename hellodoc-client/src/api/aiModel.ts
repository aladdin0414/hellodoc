import request from '../utils/request'

export interface AiModelItem {
    id: string
    name: string
    provider: string
    baseUrl?: string
    apiKey?: string
    modelName: string
    temperature: number
    agentPrompt?: string
    isDefault: boolean
    isEnabled: boolean
    disableThinking: boolean
    createdAt?: string
    updatedAt?: string
}

export interface AiModelCreateData {
    name: string
    provider?: string
    baseUrl: string
    apiKey: string
    modelName: string
    temperature?: number
    agentPrompt?: string
    isDefault?: boolean
    isEnabled?: boolean
    disableThinking?: boolean
}

export interface AiModelUpdateData {
    name?: string
    provider?: string
    baseUrl?: string
    apiKey?: string
    modelName?: string
    temperature?: number
    agentPrompt?: string
    isDefault?: boolean
    isEnabled?: boolean
    disableThinking?: boolean
}

export interface AiModelTestReq {
    id?: string
    baseUrl?: string
    apiKey?: string
    modelName?: string
    prompt?: string
}

export interface AiModelTestResp {
    success: boolean
    latencyMs: number
    reply: string
    model: string
}

// 获取所有模型列表 (管理员)
export const listAllAiModels = (): Promise<AiModelItem[]> => {
    return request.get('/api/admin/ai/models')
}

// 获取所有已激活模型列表 (普通用户 & 编辑器)
export const getActiveAiModels = (): Promise<AiModelItem[]> => {
    return request.get('/api/ai/models/active')
}

// 创建模型 (管理员)
export const createAiModel = (data: AiModelCreateData): Promise<AiModelItem> => {
    return request.post('/api/admin/ai/models', data)
}

// 更新模型 (管理员)
export const updateAiModel = (id: string, data: AiModelUpdateData): Promise<AiModelItem> => {
    return request.put(`/api/admin/ai/models/${id}`, data)
}

// 删除模型 (管理员)
export const deleteAiModel = (id: string): Promise<void> => {
    return request.delete(`/api/admin/ai/models/${id}`)
}

// 设为默认模型 (管理员)
export const setDefaultAiModel = (id: string): Promise<void> => {
    return request.put(`/api/admin/ai/models/${id}/default`)
}

// 切换激活状态 (管理员)
export const toggleActiveAiModel = (id: string): Promise<void> => {
    return request.put(`/api/admin/ai/models/${id}/toggle-active`)
}

// 连通性测试 (管理员)
export const testAiModelConnection = (data: AiModelTestReq): Promise<AiModelTestResp> => {
    return request.post('/api/admin/ai/models/test', data)
}
