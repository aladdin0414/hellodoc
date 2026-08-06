export interface KnowledgeBase {
    id: number
    title: string
    name?: string
    description: string
    icon: string | null
    color?: string
    ownerId: number
    ownerName: string
    isShared: boolean
    lastModified: string
    visibility: 'public' | 'private' | string
    ownerAvatar?: string
    role?: string
    isPinned: boolean
    pinnedAt?: string
    createdAt?: string
    sortOrder?: number
    docCount?: number
    memberCount?: number
}

export interface KbMember {
    userId: number
    username: string
    nickname?: string
    avatar?: string
    role: 'owner' | 'admin' | 'editor' | 'viewer' | string
    joinedAt?: string
}

export interface CreateKbInput {
    title: string
    description?: string
    icon?: string
    color?: string
    visibility?: string
}
