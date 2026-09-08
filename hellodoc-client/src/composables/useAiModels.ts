import { ref, computed } from 'vue'
import { getActiveAiModels, type AiModelItem } from '../api/aiModel'

const STORAGE_KEY = 'hellodoc_user_selected_ai_model_id'

// 全局单例共享状态，保证多组件间选中的模型保持同步
const activeModels = ref<AiModelItem[]>([])
const currentModelId = ref<string>('')
const loading = ref(false)
const hasLoaded = ref(false)

export function useAiModels() {
  const fetchActiveModels = async (force = false) => {
    if (hasLoaded.value && !force && activeModels.value.length > 0) {
      return
    }
    loading.value = true
    try {
      const list: any = await getActiveAiModels()
      activeModels.value = list || []
      hasLoaded.value = true

      // 从 localStorage 读取记忆的模型
      const savedId = localStorage.getItem(STORAGE_KEY)
      const matched = activeModels.value.find(m => m.id === savedId)

      if (matched) {
        currentModelId.value = matched.id
      } else {
        // 若没有记忆或已停用，优先使用默认模型，其次是第一个可用模型
        const defaultModel = activeModels.value.find(m => m.isDefault)
        if (defaultModel) {
          currentModelId.value = defaultModel.id
        } else if (activeModels.value.length > 0 && activeModels.value[0]) {
          currentModelId.value = activeModels.value[0].id
        } else {
          currentModelId.value = ''
        }
      }
    } catch (err) {
      console.error('Failed to fetch active AI models:', err)
    } finally {
      loading.value = false
    }
  }

  const selectModel = (id: string) => {
    currentModelId.value = id
    if (id) {
      localStorage.setItem(STORAGE_KEY, id)
    } else {
      localStorage.removeItem(STORAGE_KEY)
    }
  }

  const selectedModel = computed(() => {
    return activeModels.value.find(m => m.id === currentModelId.value) || null
  })

  return {
    activeModels,
    currentModelId,
    selectedModel,
    loading,
    fetchActiveModels,
    selectModel
  }
}
