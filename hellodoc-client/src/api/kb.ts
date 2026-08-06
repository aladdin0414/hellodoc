import request from '../utils/request'
import type { KnowledgeBase, KbMember, CreateKbInput } from '../types/kb'

export const getKbList = () => request.get<never, KnowledgeBase[]>('/api/kb/listKnowledgeBases')
export const getKbDetail = (id: number) => request.get<never, KnowledgeBase>(`/api/kb/${id}`)
export const createKb = (data: CreateKbInput) => request.post<never, KnowledgeBase>('/api/kb/createKnowledgeBase', data)
export const updateKb = (id: number, data: Partial<CreateKbInput>) => request.put<never, KnowledgeBase>(`/api/kb/${id}`, data)
export const deleteKb = (id: number) => request.delete<never, void>(`/api/kb/${id}`)
export const pinKb = (id: number, pinned: boolean) => request.post<never, void>(`/api/kb/${id}/pin`, { pinned })

export const getKbMembers = (kbId: number) => request.get<never, KbMember[]>(`/api/kb/${kbId}/members`)
export const addKbMember = (kbId: number, data: { userId: number; role: string }) => request.post<never, KbMember>(`/api/kb/${kbId}/members`, data)
export const updateKbMember = (kbId: number, userId: number, data: { role: string }) => request.put<never, KbMember>(`/api/kb/${kbId}/members/${userId}`, data)
export const deleteKbMember = (kbId: number, userId: number) => request.delete<never, void>(`/api/kb/${kbId}/members/${userId}`)
export const leaveKbMember = (kbId: number) => request.post<never, void>(`/api/kb/${kbId}/members/leave`)


// Public access APIs
export const getPublicKbDetail = (id: number) => request.get<never, KnowledgeBase>(`/api/public/kb/${id}`)
export const getPublicDocuments = (kbId: number) => request.get<never, any[]>(`/api/public/kb/${kbId}/documents`)
export const getPublicDocumentDetail = (kbId: number, docId: number) => request.get<never, any>(`/api/public/kb/${kbId}/documents/${docId}`)
export const getFrontendConfigs = () => request.get<never, Record<string, string>>('/api/public/configs/frontend')

// Authenticated access APIs (for preview/editing)
export const getAuthDocuments = (kbId: number) => request.get<never, any[]>(`/api/kb/${kbId}/documents`)
export const getAuthDocumentDetail = (kbId: number, docId: number) => request.get<never, any>(`/api/kb/${kbId}/documents/${docId}`)
export const exportDocument = (kbId: number, docId: number) => request.get<never, Blob>(`/api/kb/${kbId}/documents/${docId}/export`, { responseType: 'blob' })
export const searchInKb = (kbId: number, query: string, config?: any) => request.get<never, any[]>(`/api/kb/${kbId}/search`, { ...config, params: { q: query } })
export const searchEditorInKb = (kbId: number, query: string, config?: any) => request.get<never, any[]>(`/api/kb/${kbId}/search-editor`, { ...config, params: { q: query } })
export const searchPublicInKb = (kbId: number, query: string, config?: any) => request.get<never, any[]>(`/api/public/kb/${kbId}/search`, { ...config, params: { q: query } })
export const searchAll = (query: string) => request.get<never, any[]>('/api/search', { params: { q: query } })
