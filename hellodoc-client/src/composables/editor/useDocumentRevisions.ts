import { ref, type Ref } from 'vue'
import { getRevisionHistory, getRevisionContent, restoreRevision } from '../../api/revision'
import { message } from '../../utils/message'
import { i18n } from '../../i18n'

interface UseDocumentRevisionsOptions<TDoc extends { id: number; content?: string }> {
    currentDoc: Ref<TDoc | null>
    loadDocumentById: (docId: number, options?: { navigate?: boolean; optimisticActive?: boolean }) => Promise<any>
    showRevisions: Ref<boolean>
    showDiffModal: Ref<boolean>
}

export const useDocumentRevisions = <TDoc extends { id: number; content?: string }>(
    options: UseDocumentRevisionsOptions<TDoc>
) => {
    const showRevisions = options.showRevisions
    const revisions = ref<any[]>([])
    const revisionsLoading = ref(false)
    const revisionsPage = ref(0)
    const hasMoreRevisions = ref(false)
    const showDiffModal = options.showDiffModal
    const diffLoading = ref(false)
    const diffCompareData = ref({ prev: '', current: '' })
    const selectedRevision = ref<any>(null)

    const fetchRevisions = async (page = 0) => {
        if (!options.currentDoc.value) return
        revisionsLoading.value = true
        revisionsPage.value = page
        try {
            const res: any = await getRevisionHistory(options.currentDoc.value.id, { page, size: 20 })
            const newRevisions = res.content || []
            if (page === 0) {
                revisions.value = newRevisions
            } else {
                revisions.value = [...revisions.value, ...newRevisions]
            }
            hasMoreRevisions.value = !res.last && newRevisions.length > 0
        } catch (error) {
            console.error('Fetch revisions failed:', error)
        } finally {
            revisionsLoading.value = false
        }
    }

    const openRevisions = async () => {
        showRevisions.value = true
        await fetchRevisions(0)
    }

    const loadMoreRevisions = () => {
        if (!revisionsLoading.value && hasMoreRevisions.value) {
            void fetchRevisions(revisionsPage.value + 1)
        }
    }

    const closeRevisions = () => {
        showRevisions.value = false
    }

    const handleViewDiff = async (rev: any) => {
        if (!options.currentDoc.value) return
        selectedRevision.value = rev
        showDiffModal.value = true
        diffLoading.value = true
        try {
            const res: any = await getRevisionContent(options.currentDoc.value.id, rev.version)
            diffCompareData.value = {
                prev: (typeof res === 'string' ? res : res?.content) || '',
                current: options.currentDoc.value.content || ''
            }
        } catch (error) {
            console.error('Fetch revision content failed:', error)
            message.error(i18n.global.t('editor.fetchRevisionContentFailed'))
            showDiffModal.value = false
        } finally {
            diffLoading.value = false
        }
    }

    const closeDiffModal = () => {
        showDiffModal.value = false
    }

    const handleRestoreRevision = async () => {
        if (!options.currentDoc.value || !selectedRevision.value) return
        try {
            await restoreRevision(options.currentDoc.value.id, selectedRevision.value.version)
            message.success(i18n.global.t('editor.restoredVersion', { version: selectedRevision.value.version }))
            showDiffModal.value = false
            showRevisions.value = false
            await options.loadDocumentById(options.currentDoc.value.id, { navigate: false, optimisticActive: true })
        } catch (error) {
            console.error('Restore revision failed:', error)
            message.error(i18n.global.t('editor.restoreVersionFailed'))
        }
    }

    return {
        showRevisions,
        revisions,
        revisionsLoading,
        revisionsPage,
        hasMoreRevisions,
        showDiffModal,
        diffLoading,
        diffCompareData,
        selectedRevision,
        fetchRevisions,
        openRevisions,
        loadMoreRevisions,
        closeRevisions,
        handleViewDiff,
        closeDiffModal,
        handleRestoreRevision
    }
}
