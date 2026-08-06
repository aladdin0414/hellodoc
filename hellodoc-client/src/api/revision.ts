import request from '../utils/request'

export const getRevisionHistory = (docId: number, params?: any) =>
    request.get(`/api/docs/${docId}/revisions`, { params })

export const getRevisionDetail = (docId: number, version: number) =>
    request.get(`/api/docs/${docId}/revisions/${version}`)

export const getRevisionContent = (docId: number, version: number) =>
    request.get(`/api/docs/${docId}/revisions/${version}/content`)

export const restoreRevision = (docId: number, version: number) =>
    request.post(`/api/docs/${docId}/revisions/${version}/restore`)
