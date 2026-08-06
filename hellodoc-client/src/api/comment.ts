import request from '../utils/request'

export interface DocComment {
    id: number
    docId: number
    userId: number
    user: {
        nickname: string
        avatar: string
    }
    parentId: number | null
    content: string
    isResolved: boolean
    createdAt: string
    updatedAt: string
}

export function getComments(docId: number) {
    return request({
        url: `/api/docs/${docId}/comments`,
        method: 'get'
    })
}

export function addComment(docId: number, data: { content: string, anchorType?: string, anchorData?: string, anchorText?: string }) {
    return request({
        url: `/api/docs/${docId}/comments`,
        method: 'post',
        data
    })
}

export function replyComment(commentId: number, content: string) {
    return request({
        url: `/api/comments/${commentId}/reply`,
        method: 'post',
        data: { content }
    })
}

export function deleteComment(commentId: number) {
    return request({
        url: `/api/comments/${commentId}`,
        method: 'delete'
    })
}

export function resolveComment(commentId: number) {
    return request({
        url: `/api/comments/${commentId}/resolve`,
        method: 'post'
    })
}

export function unresolveComment(commentId: number) {
    return request({
        url: `/api/comments/${commentId}/unresolve`,
        method: 'post'
    })
}
