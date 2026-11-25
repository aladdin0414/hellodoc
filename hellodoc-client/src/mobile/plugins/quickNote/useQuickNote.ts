import { ref } from 'vue'
import type { Router } from 'vue-router'
import { createDocument } from '../../../api/document'
import { i18n } from '../../../i18n'
import { getMe } from '../../../api/user'

export function useQuickNote() {
  const creatingQuickNote = ref(false)

  const formatTimestamp = (): string => {
    const now = new Date()
    const pad = (n: number) => String(n).padStart(2, '0')
    const yyyy = now.getFullYear()
    const mm = pad(now.getMonth() + 1)
    const dd = pad(now.getDate())
    const hh = pad(now.getHours())
    const min = pad(now.getMinutes())
    const ss = pad(now.getSeconds())
    return `${yyyy}${mm}${dd}_${hh}${min}${ss}`
  }

  const handleCreateQuickNote = async (options: {
    router: Router
    onSetupRequired: () => void
    destination?: 'mobile' | 'desktop'
    onSuccess?: () => void
  }) => {
    if (creatingQuickNote.value) return
    creatingQuickNote.value = true

    const docTitle = `${i18n.global.t('mobile.quickNote.docPrefix')}_${formatTimestamp()}`

    try {
      const user: any = await getMe()
      const kbId = user?.quickNoteKbId
      if (!kbId) {
        options.onSetupRequired()
        return
      }
      if (options.onSuccess) {
        options.onSuccess()
      }
      const res: any = await createDocument(kbId, {
        title: docTitle,
        name: docTitle,
        type: 'file',
        parentId: null
      })

      const docId = res?.id || res?.data?.id
      if (docId) {
        sessionStorage.setItem(`last_doc_${kbId}`, String(docId))
        sessionStorage.setItem('active_doc_back', String(docId))
        if (options.destination === 'desktop') {
          await options.router.push({ name: 'Editor', params: { kbId, docId } })
        } else {
          await options.router.push({
            path: `/m/kb/${kbId}/doc/${docId}`,
            query: { from: '/m', mode: 'edit', autoFocus: 'true' }
          })
        }
      }
    } catch (err) {
      console.error('[QuickNotePlugin] Failed to create quick note:', err)
    } finally {
      creatingQuickNote.value = false
    }
  }

  return {
    creatingQuickNote,
    handleCreateQuickNote
  }
}
