export interface DocItem {
    id: number
    __key?: number
    name: string
    type: string
    parentId: number | null
    orderNum: number
    status?: string
    isCover?: boolean
    extraMeta?: Record<string, any>
    [key: string]: any
}
