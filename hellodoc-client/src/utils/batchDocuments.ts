export function topLevelSelection<T extends { id: number; parentId: number | null }>(documents: T[], selectedIds: Set<number>): T[] {
    const byId = new Map(documents.map(doc => [doc.id, doc]))
    return documents.filter(doc => {
        if (!selectedIds.has(doc.id)) return false
        let parentId = doc.parentId
        const visited = new Set<number>([doc.id])
        while (parentId !== null && !visited.has(parentId)) {
            if (selectedIds.has(parentId)) return false
            visited.add(parentId)
            parentId = byId.get(parentId)?.parentId ?? null
        }
        return true
    })
}

export function summarizeBatch<T extends { name: string }>(items: T[], results: PromiseSettledResult<unknown>[]) {
    const failed = results.flatMap((result, index) => result.status === 'rejected' ? [items[index]!.name] : [])
    return { success: items.length - failed.length, failed }
}

export function selectedFiles<T extends { id: number; parentId: number | null; type: string }>(documents: T[], selectedIds: Set<number>): T[] {
    const byId = new Map(documents.map(doc => [doc.id, doc]))
    return documents.filter(doc => {
        if (doc.type !== 'file') return false
        let current: T | undefined = doc
        const visited = new Set<number>()
        while (current && !visited.has(current.id)) {
            if (selectedIds.has(current.id)) return true
            visited.add(current.id)
            current = current.parentId === null ? undefined : byId.get(current.parentId)
        }
        return false
    })
}
