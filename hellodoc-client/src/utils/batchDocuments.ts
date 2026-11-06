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
