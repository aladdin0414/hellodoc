<script setup lang="ts">
import { ref, nextTick, watch } from 'vue'
import { message } from '../../utils/message'
import { useAiModels } from '../../composables/useAiModels'
import { aiCompletionStream } from '../../api/ai'

const props = defineProps<{
  visible: boolean
  documentContext?: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'insert-to-doc', content: string): void
}>()

const { activeModels, currentModelId, fetchActiveModels, selectModel, selectedModel } = useAiModels()

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  modelName?: string
  loading?: boolean
}

const messages = ref<ChatMessage[]>([])
const inputPrompt = ref('')
const isGenerating = ref(false)
const messagesContainerRef = ref<HTMLDivElement | null>(null)

// 快捷开场白提问建议
const quickSuggestions = [
  '帮我润色这段文字，使其更加专业优雅',
  '针对当前文档主题生成一份结构大纲',
  '为系统撰写一段严谨的 API 设计说明',
  '帮我排查代码中的潜在边界缺陷'
]

// 滚动到底部
const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainerRef.value) {
    messagesContainerRef.value.scrollTop = messagesContainerRef.value.scrollHeight
  }
}

const handleClose = () => {
  emit('update:visible', false)
}

const clearHistory = () => {
  if (isGenerating.value) return
  messages.value = []
}

// 发送消息
const handleSend = async () => {
  const prompt = inputPrompt.value.trim()
  if (!prompt || isGenerating.value) return

  // 放入用户问题
  const userMsgId = Date.now().toString()
  messages.value.push({
    id: userMsgId,
    role: 'user',
    content: prompt
  })
  inputPrompt.value = ''

  // 放入 AI 占位消息
  const aiMsgId = (Date.now() + 1).toString()
  const currentModelName = selectedModel.value?.name || 'AI'
  messages.value.push({
    id: aiMsgId,
    role: 'assistant',
    content: '',
    modelName: currentModelName,
    loading: true
  })
  isGenerating.value = true
  await scrollToBottom()

  try {
    const aiMsg = messages.value.find(m => m.id === aiMsgId)
    await aiCompletionStream({
      modelId: currentModelId.value || undefined,
      context: props.documentContext || '',
      prompt: prompt
    }, {
      onModel: (model) => {
        if (aiMsg && model) {
          aiMsg.modelName = model
        }
      },
      onChunk: (chunk) => {
        if (aiMsg) {
          aiMsg.loading = false
          aiMsg.content += chunk
          scrollToBottom()
        }
      },
      onDone: () => {
        if (aiMsg) {
          aiMsg.loading = false
        }
      }
    })
  } catch (err: any) {
    message.error(err?.message || 'AI 回复生成中断')
    const aiMsg = messages.value.find(m => m.id === aiMsgId)
    if (aiMsg && !aiMsg.content) {
      aiMsg.content = `[生成出错: ${err?.message || '网络异常'}]`
      aiMsg.loading = false
    }
  } finally {
    isGenerating.value = false
    await scrollToBottom()
  }
}

// 快速应用建议
const applySuggestion = (suggestion: string) => {
  inputPrompt.value = suggestion
  handleSend()
}

// 复制消息
const copyContent = async (text: string) => {
  try {
    await navigator.clipboard.writeText(text)
    message.success('已复制到剪贴板')
  } catch {
    message.error('复制失败')
  }
}

// 插入到文档中
const insertToDoc = (text: string) => {
  emit('insert-to-doc', text)
  message.success('已插入到当前文档光标处')
}

watch(() => props.visible, (val) => {
  if (val) {
    fetchActiveModels()
    scrollToBottom()
  }
})
</script>

<template>
  <div>
    <!-- 侧边抽屉面板 -->
    <transition
      enter-active-class="transform transition ease-in-out duration-300"
      enter-from-class="translate-x-full"
      enter-to-class="translate-x-0"
      leave-active-class="transform transition ease-in-out duration-300"
      leave-from-class="translate-x-0"
      leave-to-class="translate-x-full"
    >
      <aside
        v-if="visible"
        class="fixed inset-y-0 right-0 z-50 w-full sm:w-[460px] bg-white dark:bg-[#161b22] shadow-2xl border-l border-gray-200 dark:border-gray-800 flex flex-col overflow-hidden"
      >
        <!-- 抽屉头部 -->
        <div class="h-16 px-4 border-b border-gray-200 dark:border-gray-800 flex items-center justify-between flex-shrink-0 bg-gray-50/80 dark:bg-gray-800/40 backdrop-blur-sm">
          <div class="flex items-center gap-2">
            <div class="w-8 h-8 rounded-xl bg-gradient-to-tr from-indigo-500 to-blue-600 flex items-center justify-center text-white shadow-sm">
              <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 2v8"/><path d="m4.93 10.93 1.41 1.41"/><path d="M2 18h2"/><path d="M20 18h2"/><path d="m19.07 10.93-1.41 1.41"/><path d="M22 22H2"/><path d="m8 22 4-10 4 10"/>
              </svg>
            </div>
            <div>
              <h3 class="text-sm font-bold text-gray-900 dark:text-white flex items-center gap-1.5">
                AI 知识助理
              </h3>
              <p class="text-[11px] text-gray-400">智能创作、内容问答与代码辅导</p>
            </div>
          </div>

          <div class="flex items-center gap-2">
            <!-- 模型切换选择器 -->
            <select
              :value="currentModelId"
              @change="selectModel(($event.target as HTMLSelectElement).value)"
              class="text-xs font-medium bg-white dark:bg-gray-800 text-gray-700 dark:text-gray-300 rounded-lg px-2.5 py-1.5 border border-gray-200 dark:border-gray-700 focus:outline-none focus:ring-1 focus:ring-blue-500 max-w-[150px] truncate shadow-sm"
              title="切换当前使用的大模型"
            >
              <option
                v-for="model in activeModels"
                :key="model.id"
                :value="model.id"
              >
                {{ model.name }}{{ model.isDefault ? ' (默认)' : '' }}
              </option>
            </select>

            <!-- 清空历史 -->
            <button
              @click="clearHistory"
              class="p-1.5 text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
              title="清空对话"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M3 6h18"/><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/>
              </svg>
            </button>

            <!-- 关闭抽屉 -->
            <button
              @click="handleClose"
              class="p-1.5 text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
        </div>

        <!-- 消息对话滚动区 -->
        <div ref="messagesContainerRef" class="flex-1 p-4 overflow-y-auto space-y-4">
          <!-- 初始空状态引导 -->
          <div v-if="messages.length === 0" class="py-12 px-4 text-center">
            <div class="w-14 h-14 rounded-2xl bg-blue-50 dark:bg-blue-900/20 text-blue-600 dark:text-blue-400 mx-auto flex items-center justify-center mb-3">
              <svg xmlns="http://www.w3.org/2000/svg" class="w-7 h-7" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
              </svg>
            </div>
            <h4 class="text-sm font-semibold text-gray-800 dark:text-gray-200 mb-1">您好！我是您的智能文档助手</h4>
            <p class="text-xs text-gray-400 max-w-xs mx-auto mb-6">
              已为您加载 <span class="font-medium text-blue-600 dark:text-blue-400">{{ selectedModel?.name || '默认大模型' }}</span>，您可以直接提问或选用以下快捷功能：
            </p>

            <div class="grid grid-cols-1 gap-2 text-left">
              <button
                v-for="s in quickSuggestions"
                :key="s"
                @click="applySuggestion(s)"
                class="p-2.5 rounded-xl border border-gray-200 dark:border-gray-800 text-xs text-gray-600 dark:text-gray-300 hover:border-blue-300 dark:hover:border-blue-700 hover:bg-blue-50/50 dark:hover:bg-blue-900/10 transition-colors flex items-center justify-between group"
              >
                <span>{{ s }}</span>
                <svg xmlns="http://www.w3.org/2000/svg" class="w-3.5 h-3.5 text-gray-400 group-hover:text-blue-500 transition-colors" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="m9 18 6-6-6-6"/>
                </svg>
              </button>
            </div>
          </div>

          <!-- 消息流 -->
          <div
            v-for="msg in messages"
            :key="msg.id"
            class="flex flex-col"
            :class="msg.role === 'user' ? 'items-end' : 'items-start'"
          >
            <!-- 角色与模型标识 -->
            <div class="text-[11px] text-gray-400 mb-1 px-1 flex items-center gap-1.5">
              <span>{{ msg.role === 'user' ? '您' : (msg.modelName || 'AI 助手') }}</span>
            </div>

            <!-- 消息气泡 -->
            <div
              class="max-w-[90%] rounded-2xl p-3.5 text-xs leading-relaxed transition-all"
              :class="[
                msg.role === 'user'
                  ? 'bg-blue-600 text-white rounded-tr-sm shadow-sm'
                  : 'bg-gray-100 dark:bg-gray-800 text-gray-800 dark:text-gray-100 rounded-tl-sm border border-gray-200/60 dark:border-gray-700/60'
              ]"
            >
              <!-- 加载中的脉冲状态 -->
              <div v-if="msg.loading && !msg.content" class="flex items-center gap-1 py-1">
                <span class="w-1.5 h-1.5 rounded-full bg-blue-500 animate-bounce"></span>
                <span class="w-1.5 h-1.5 rounded-full bg-blue-500 animate-bounce [animation-delay:0.2s]"></span>
                <span class="w-1.5 h-1.5 rounded-full bg-blue-500 animate-bounce [animation-delay:0.4s]"></span>
              </div>
              <!-- 正文展示（支持换行和纯净 Markdown 文本） -->
              <div v-else class="whitespace-pre-wrap break-words">{{ msg.content }}</div>

              <!-- AI 回复工具条 -->
              <div
                v-if="msg.role === 'assistant' && msg.content && !msg.loading"
                class="mt-2 pt-2 border-t border-gray-200 dark:border-gray-700/80 flex items-center justify-end gap-2 text-[11px]"
              >
                <button
                  @click="copyContent(msg.content)"
                  class="text-gray-500 hover:text-blue-600 dark:hover:text-blue-400 transition-colors flex items-center gap-1"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect width="14" height="14" x="8" y="8" rx="2" ry="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/>
                  </svg>
                  复制
                </button>
                <button
                  @click="insertToDoc(msg.content)"
                  class="text-blue-600 dark:text-blue-400 hover:underline flex items-center gap-1 font-medium"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M12 5v14"/><path d="m19 12-7 7-7-7"/>
                  </svg>
                  插入文档
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- 底部输入框 -->
        <div class="p-4 border-t border-gray-200 dark:border-gray-800 bg-white dark:bg-[#161b22] flex-shrink-0">
          <div class="relative rounded-2xl border border-gray-200 dark:border-gray-700 focus-within:border-blue-500 focus-within:ring-2 focus-within:ring-blue-500/20 bg-gray-50 dark:bg-gray-800/60 p-2 transition-all">
            <textarea
              v-model="inputPrompt"
              rows="3"
              :disabled="isGenerating"
              @keydown.enter.exact.prevent="handleSend"
              placeholder="输入问题或指令，Enter 发送，Shift+Enter 换行..."
              class="w-full bg-transparent text-xs text-gray-800 dark:text-gray-100 placeholder-gray-400 focus:outline-none resize-none px-1 py-1"
            ></textarea>
            <div class="flex items-center justify-between pt-1 px-1">
              <span class="text-[10px] text-gray-400">当前模型: {{ selectedModel?.name || '默认模型' }}</span>
              <button
                @click="handleSend"
                :disabled="!inputPrompt.trim() || isGenerating"
                class="inline-flex items-center justify-center px-3.5 py-1.5 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-xl shadow-sm transition-all disabled:opacity-40 disabled:cursor-not-allowed"
              >
                <svg v-if="isGenerating" class="animate-spin -ml-0.5 mr-1.5 h-3.5 w-3.5 text-white" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                <svg v-else xmlns="http://www.w3.org/2000/svg" class="w-3.5 h-3.5 mr-1" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/>
                </svg>
                {{ isGenerating ? '思考中...' : '发送' }}
              </button>
            </div>
          </div>
        </div>
      </aside>
    </transition>
  </div>
</template>
