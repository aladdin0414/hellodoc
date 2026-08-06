import request from '../utils/request'

export const getDocuments = (kbId: number) => request.get(`/api/kb/${kbId}/documents`)
export const getDocumentDetail = (kbId: number, docId: number) => request.get(`/api/kb/${kbId}/documents/${docId}`)
export const createDocument = (kbId: number, data: any) => request.post(`/api/kb/${kbId}/documents`, data)
export const updateDocument = (kbId: number, docId: number, data: any) => request.put(`/api/kb/${kbId}/documents/${docId}`, data)
export const deleteDocument = (kbId: number, docId: number) => request.delete(`/api/kb/${kbId}/documents/${docId}`)
export const duplicateDocument = (kbId: number, docId: number) => request.post(`/api/kb/${kbId}/documents/${docId}/duplicate`, {}, { timeout: 30000 })
export const copyDocumentToKb = (kbId: number, docId: number, targetKbId: number) => request.post(`/api/kb/${kbId}/documents/${docId}/copy-to/${targetKbId}`, {}, { timeout: 30000 })
export const unlockDocument = (kbId: number, docId: number, password: string) => request.post(`/api/kb/${kbId}/documents/${docId}/unlock`, { password })

export const getTrashDocuments = (kbId: number) => request.get(`/api/kb/${kbId}/trash`)
export const restoreDocument = (kbId: number, docId: number) => request.post(`/api/kb/${kbId}/documents/${docId}/restore`)
export const permanentlyDeleteDocument = (kbId: number, docId: number) => request.delete(`/api/kb/${kbId}/documents/${docId}/permanent`)
export const clearTrash = (kbId: number) => request.delete(`/api/kb/${kbId}/trash`)
