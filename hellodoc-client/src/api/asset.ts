import request from '../utils/request'

export interface AssetVO {
    id: number
    kbId: number
    docId: number | null
    storageFileId: number
    uploaderId: number
    fileName: string
    description: string | null
    rawUrl: string
    downloadUrl: string
    createdAt: string
}

export function uploadAsset(kbId: number, docId: number, file: FormData) {
    return request({
        url: `/api/kb/${kbId}/docs/${docId}/assets`,
        method: 'post',
        data: file
    })
}

export function uploadAssetFromUrl(kbId: number, docId: number, url: string) {
    return request({
        url: `/api/kb/${kbId}/docs/${docId}/assets/from-url`,
        method: 'post',
        data: { url }
    })
}
