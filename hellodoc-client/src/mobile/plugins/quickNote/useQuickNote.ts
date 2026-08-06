import { ref } from 'vue'
import type { Router } from 'vue-router'
import { createDocument } from '../../../api/document'

export interface QuickNoteUserConfig {
  user: string
  kbid: number
}

/**
 * 全局插件总开关：设为 false 可一键关闭私人订制速记功能
 */
export const ENABLE_QUICK_NOTE_PLUGIN = true

/**
 * 目标定制用户及对应知识库 ID 配置列表 (JSON 数组格式)
 */
export const QUICK_NOTE_USER_CONFIGS: QuickNoteUserConfig[] = [
  { user: 'liyc', kbid: 38 }
]

export function useQuickNote() {
  const creatingQuickNote = ref(false)

  const getUserConfig = (username: string | null | undefined): QuickNoteUserConfig | null => {
    if (!ENABLE_QUICK_NOTE_PLUGIN) return null
    if (!username) return null
    const current = username.trim().toLowerCase()
    return QUICK_NOTE_USER_CONFIGS.find(cfg => cfg.user.trim().toLowerCase() === current) || null
  }

  const isTargetUser = (username: string | null | undefined): boolean => {
    return getUserConfig(username) !== null
  }

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
    username?: string
    kbId?: number
    onSuccess?: () => void
  }) => {
    if (creatingQuickNote.value) return
    creatingQuickNote.value = true

    const matchedConfig = options.username ? getUserConfig(options.username) : null
    const kbId = matchedConfig?.kbid ?? options.kbId ?? QUICK_NOTE_USER_CONFIGS[0]?.kbid ?? 38
    const docTitle = `速记_${formatTimestamp()}`

    try {
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
        options.router.push({
          path: `/m/kb/${kbId}/doc/${docId}`,
          query: { from: '/m', mode: 'edit', autoFocus: 'true' }
        })
      }
    } catch (err) {
      console.error('[QuickNotePlugin] Failed to create quick note:', err)
    } finally {
      creatingQuickNote.value = false
    }
  }

  return {
    ENABLE_QUICK_NOTE_PLUGIN,
    creatingQuickNote,
    getUserConfig,
    isTargetUser,
    handleCreateQuickNote
  }
}
