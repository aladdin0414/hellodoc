export interface TreeDocument {
    id: number
    name: string
    type: string
    parentId: number | null
    orderNum: number
}

export const sortDocuments = <T extends TreeDocument>(a: T, b: T) => {
    if (a.orderNum !== b.orderNum) {
        return a.orderNum - b.orderNum
    }
    if (a.type !== b.type) {
        return a.type === 'folder' ? -1 : 1
    }
    return a.name.localeCompare(b.name)
}

export const expandAncestorFolders = <T extends TreeDocument>(
    expandedFolders: Set<number>,
    documents: T[],
    targetDocId: number
) => {
    const map = new Map<number, T>()
    documents.forEach(doc => map.set(doc.id, doc))
    let current = map.get(targetDocId)
    while (current && current.parentId !== null) {
        expandedFolders.add(current.parentId)
        current = map.get(current.parentId)
    }
}

export const buildOrderedDocuments = <T extends TreeDocument>(
    documents: T[],
    expandedFolders: Set<number>,
    rawSearchQuery: string
): Array<T & { depth: number }> => {
    const result: Array<T & { depth: number }> = []
    const searchQuery = rawSearchQuery.toLowerCase().trim()
    const docMap = new Map<number, T>()
    documents.forEach(doc => docMap.set(doc.id, doc))
    const itemsToShow = new Set<number>()

    if (searchQuery) {
        documents.forEach(doc => {
            if (!doc.name.toLowerCase().includes(searchQuery)) return
            itemsToShow.add(doc.id)
            let parentId = doc.parentId
            while (parentId !== null) {
                itemsToShow.add(parentId)
                const parent = docMap.get(parentId)
                parentId = parent ? parent.parentId : null
            }
        })
    }

    const build = (parentId: number | null, depth: number) => {
        let children = documents.filter(d => d.parentId === parentId)
        if (searchQuery) {
            children = children.filter(child => itemsToShow.has(child.id))
        }
        children.sort(sortDocuments)

        children.forEach(child => {
            result.push({ ...child, depth })
            const shouldExpand = searchQuery
                ? itemsToShow.has(child.id)
                : expandedFolders.has(child.id)
            if (child.type === 'folder' && shouldExpand) {
                build(child.id, depth + 1)
            }
        })
    }

    build(null, 0)
    return result
}
