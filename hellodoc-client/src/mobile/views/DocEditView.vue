<template>
  <div class="h-screen overflow-y-auto bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 flex flex-col transition-colors no-scrollbar">
    <!-- 顶部 Navigation：极简沉浸规范顶栏 -->
    <HeaderNav :show-back="true" @back="handleGoBack">
      <template #right>
        <!-- 只读或极简文字保存状态指示器 -->
        <div v-if="isReadOnly" class="text-xs flex items-center gap-1 text-slate-400 dark:text-slate-500 select-none">
          <Eye class="w-3.5 h-3.5" />
          <span>{{ t('mobile.docDetail.readOnly') }}</span>
        </div>
        <div v-else class="text-xs flex items-center gap-1 select-none transition-all">
          <template v-if="saving || uploadingImage">
            <Loader2 class="w-3 h-3 text-blue-500 animate-spin" />
            <span class="text-slate-400">{{ uploadingImage ? t('editor.uploadingImage') : t('editor.saving') }}</span>
          </template>
          <template v-else-if="isDirty">
            <span class="w-1.5 h-1.5 rounded-full bg-rose-500 animate-pulse"></span>
            <span class="text-rose-500 font-medium">{{ t('editor.unsaved') }}</span>
            </template>
            <template v-else-if="showSavedIndicator">
            <Check class="w-3 h-3 text-emerald-500" />
            <span class="text-slate-400 dark:text-slate-500">{{ t('editor.saved') }}</span>
          </template>
        </div>

        <div class="h-3 w-[1px] bg-slate-200 dark:bg-slate-800"></div>

        <!-- 预览模式切换按钮 (只读状态下隐去) -->
        <button
          v-if="!isReadOnly"
          @click="isEditing = !isEditing"
          :title="isEditing ? t('editor.switchToPreview') : t('editor.switchToEdit')"
          :class="[isEditing ? 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800' : 'bg-blue-50 dark:bg-blue-500/20 text-blue-600 dark:text-blue-400']"
          class="p-1.5 rounded-xl transition-colors active:scale-95 flex items-center gap-1 text-xs font-semibold"
        >
          <component :is="isEditing ? Eye : Edit3" class="w-4 h-4" />
          <span>{{ isEditing ? t('editor.preview') : t('editor.edit') }}</span>
        </button>

        <!-- 日夜切换 -->
        <button
          @click="toggleTheme"
          :title="isDark ? t('nav.lightMode') : t('nav.darkMode')"
          class="p-1.5 rounded-xl text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors active:scale-95"
        >
          <Sun v-if="isDark" class="w-4 h-4 text-amber-400" />
          <Moon v-else class="w-4 h-4 text-slate-600" />
        </button>
      </template>
    </HeaderNav>

    <!-- 沉浸式正文区 -->
    <main class="flex-1 flex flex-col px-5 py-4 w-full space-y-3 pb-[calc(5.5rem+env(safe-area-inset-bottom))]">
      <!-- 加载中骨架屏 Skeleton Loading (避免权限确定前闪烁) -->
      <div v-if="loadingDoc" class="space-y-4 animate-pulse pt-2 flex-1">
        <div class="h-8 bg-slate-200/80 dark:bg-slate-800/80 rounded-xl w-2/5"></div>
        <div class="space-y-3 pt-4">
          <div class="h-4 bg-slate-200/60 dark:bg-slate-800/60 rounded-md w-full"></div>
          <div class="h-4 bg-slate-200/60 dark:bg-slate-800/60 rounded-md w-5/6"></div>
          <div class="h-4 bg-slate-200/60 dark:bg-slate-800/60 rounded-md w-4/6"></div>
          <div class="h-4 bg-slate-200/60 dark:bg-slate-800/60 rounded-md w-3/4"></div>
        </div>
      </div>

      <template v-else>
        <!-- 文档标题：大字号视觉层级高于 H1，预览状态下只读不可修改 -->
        <div class="flex items-center gap-1.5 pt-1 pb-1">
          <span v-if="isDirty && isEditing && !isReadOnly" class="text-rose-500 text-3xl font-extrabold shrink-0 select-none" :title="t('editor.unsavedChangesTitle')">*</span>
          <input
            v-model="docTitle"
            @input="markDirty"
            type="text"
            :readonly="isReadOnly || !isEditing"
            :placeholder="t('editor.untitled')"
            :class="[
              (!isEditing || isReadOnly) ? 'cursor-default select-text pointer-events-none' : '',
              'w-full text-3xl font-extrabold tracking-tight bg-transparent text-slate-900 dark:text-white focus:outline-none placeholder-slate-300 dark:placeholder-slate-700 transition-colors'
            ]"
          />
        </div>

      <!-- 编辑模式 -->
      <div v-if="isEditing" class="flex-1 flex flex-col space-y-2">
        <!-- 移动端 Markdown 辅助快捷工具条：包含撤销/重做与图片上传 -->
        <div class="flex items-center gap-1.5 overflow-x-auto py-1.5 text-xs no-scrollbar">
          <button
            @click="handleUndo"
            :disabled="!canUndo"
            :title="t('editor.undo')"
            class="px-2.5 py-1 rounded-lg bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 disabled:opacity-40 disabled:pointer-events-none active:scale-95 transition-all shrink-0 flex items-center gap-1"
          >
            <Undo2 class="w-3.5 h-3.5" />
          </button>
          <button
            @click="handleRedo"
            :disabled="!canRedo"
            :title="t('editor.redo')"
            class="px-2.5 py-1 rounded-lg bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 disabled:opacity-40 disabled:pointer-events-none active:scale-95 transition-all shrink-0 flex items-center gap-1"
          >
            <Redo2 class="w-3.5 h-3.5" />
          </button>
          <button @click="applyBlockSyntax('# ')" class="px-2.5 py-1 rounded-lg bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 font-semibold active:scale-95 transition-all shrink-0">H1</button>
          <button @click="applyBlockSyntax('## ')" class="px-2.5 py-1 rounded-lg bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 font-semibold active:scale-95 transition-all shrink-0">H2</button>
          <button @click="applyInlineSyntax('**', '**', t('toolbar.bold'))" class="px-2.5 py-1 rounded-lg bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 font-bold active:scale-95 transition-all shrink-0">B</button>
          <button @click="applyBlockSyntax('- ')" class="px-2.5 py-1 rounded-lg bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 active:scale-95 transition-all shrink-0">• {{ t('toolbar.list') }}</button>
          <button @click="applyBlockSyntax('> ')" class="px-2.5 py-1 rounded-lg bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 active:scale-95 transition-all shrink-0">{{ t('toolbar.quote') }}</button>
          <button @click="applyInlineSyntax('`', '`', t('toolbar.code'))" class="px-2.5 py-1 rounded-lg bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 active:scale-95 transition-all shrink-0">&lt;&gt; {{ t('toolbar.code') }}</button>
          <button
            @click="triggerImageSelect"
            :disabled="uploadingImage"
            class="px-2.5 py-1 rounded-lg bg-blue-50 dark:bg-blue-500/20 text-blue-600 dark:text-blue-400 font-semibold active:scale-95 transition-all flex items-center gap-1 shrink-0"
          >
            <ImageIcon class="w-3.5 h-3.5" />
            <span>{{ uploadingImage ? t('editor.uploadingImage') : t('toolbar.image') }}</span>
          </button>
        </div>

        <!-- 隐藏式文件选择框 -->
        <input
          ref="fileInputRef"
          type="file"
          accept="image/*"
          @change="handleImageFileChange"
          class="hidden"
        />

        <!-- 正文文本域：16px 原生高品质打字体验 -->
        <textarea
          ref="editorRef"
          v-model="docContent"
          @input="handleContentInput"
          :placeholder="t('editor.startWriting')"
          class="flex-1 min-h-[500px] w-full pt-2 pb-12 bg-transparent text-base leading-relaxed text-slate-800 dark:text-slate-200 placeholder-slate-300 dark:placeholder-slate-700 focus:outline-none resize-none font-sans no-scrollbar"
        ></textarea>
      </div>

      <!-- 预览模式：直接复用 Web 端原装 VisualEditor 只读组件，保证 100% 与 Web 版 Edit 状态样式一致 -->
      <div v-else class="flex-1 min-h-[500px] py-2 pb-12">
        <VisualEditor
          v-if="docContent"
          :model-value="docContent"
          :is-read-only="true"
          :hide-toolbar="true"
          :pure-mode="true"
        />
        <p v-else class="text-slate-400 italic py-4">{{ t('editor.noContent') }}</p>
      </div>
      </template>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import HeaderNav from '../components/HeaderNav.vue'

const { t } = useI18n()
import { Eye, Edit3, Loader2, Check, Sun, Moon, Image as ImageIcon, Undo2, Redo2 } from 'lucide-vue-next'
import VisualEditor from '../../components/editor/VisualEditor.vue'
import '../../components/editor/setupMdEditor'
import { getAuthDocumentDetail, getKbDetail } from '../../api/kb'
import { updateDocument } from '../../api/document'
import { uploadAsset } from '../../api/asset'
import { message } from '../../utils/message'
import { useTheme } from '../composables/useTheme'

const route = useRoute()
const router = useRouter()
const { isDark, toggleTheme } = useTheme()

const kbId = Number(route.params.kbId)
const docId = Number(route.params.docId)

const isReadOnly = ref(false)
const isEditing = ref(route.query.mode === 'edit' || route.query.autoFocus === 'true')
const loadingDoc = ref(true)
const saving = ref(false)
const isDirty = ref(false)
const uploadingImage = ref(false)
const showSavedIndicator = ref(false)

const docTitle = ref('')
const docContent = ref('')
const editorRef = ref<HTMLTextAreaElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)

// 历史撤销重做栈 Undo / Redo
const historyStack = ref<string[]>([])
const historyIndex = ref<number>(-1)
let isHistoryAction = false
let historyDebounceTimer: ReturnType<typeof setTimeout> | null = null
let focusTimer: ReturnType<typeof setTimeout> | null = null
let savedIndicatorTimer: ReturnType<typeof setTimeout> | null = null

const canUndo = computed(() => historyIndex.value > 0)
const canRedo = computed(() => historyIndex.value >= 0 && historyIndex.value < historyStack.value.length - 1)

const showSavedFeedback = () => {
  showSavedIndicator.value = true
  if (savedIndicatorTimer) {
    clearTimeout(savedIndicatorTimer)
  }
  savedIndicatorTimer = setTimeout(() => {
    showSavedIndicator.value = false
    savedIndicatorTimer = null
  }, 1200)
}

const pushHistory = (content: string, immediate = false) => {
  if (isHistoryAction) return

  const save = () => {
    if (historyIndex.value < historyStack.value.length - 1) {
      historyStack.value = historyStack.value.slice(0, historyIndex.value + 1)
    }
    if (historyStack.value[historyStack.value.length - 1] !== content) {
      historyStack.value.push(content)
      if (historyStack.value.length > 50) {
        historyStack.value.shift()
      }
      historyIndex.value = historyStack.value.length - 1
    }
  }

  if (immediate) {
    if (historyDebounceTimer) clearTimeout(historyDebounceTimer)
    save()
  } else {
    if (historyDebounceTimer) clearTimeout(historyDebounceTimer)
    historyDebounceTimer = setTimeout(save, 400)
  }
}

const handleUndo = () => {
  if (!canUndo.value) return
  isHistoryAction = true
  historyIndex.value--
  docContent.value = historyStack.value[historyIndex.value] ?? ''
  markDirty()
  setTimeout(() => { isHistoryAction = false }, 50)
}

const handleRedo = () => {
  if (!canRedo.value) return
  isHistoryAction = true
  historyIndex.value++
  docContent.value = historyStack.value[historyIndex.value] ?? ''
  markDirty()
  setTimeout(() => { isHistoryAction = false }, 50)
}

const handleContentInput = () => {
  markDirty()
  pushHistory(docContent.value, false)
}

let autoSaveTimer: ReturnType<typeof setTimeout> | null = null
const AUTO_SAVE_DELAY_MS = 1200

const markDirty = () => {
  if (isReadOnly.value) return
  isDirty.value = true
  scheduleAutoSave()
}

const scheduleAutoSave = () => {
  if (isReadOnly.value) return
  if (autoSaveTimer) clearTimeout(autoSaveTimer)
  autoSaveTimer = setTimeout(() => {
    if (isDirty.value) {
      void handleSave({ silent: true })
    }
  }, AUTO_SAVE_DELAY_MS)
}

const fetchDocDetail = async () => {
  loadingDoc.value = true
  try {
    const kbRes = await getKbDetail(kbId).catch(() => null)
    if (kbRes && (kbRes.role || '').toUpperCase() === 'VIEWER') {
      isReadOnly.value = true
      isEditing.value = false
    }

    const res: any = await getAuthDocumentDetail(kbId, docId)
    if (res) {
      docTitle.value = res.title || res.name || ''
      docContent.value = res.content || ''
    }
  } catch (err) {
    docTitle.value = ''
    docContent.value = ''
  } finally {
    historyStack.value = [docContent.value]
    historyIndex.value = 0
    loadingDoc.value = false

    if (route.query.autoFocus === 'true' && !isReadOnly.value && isEditing.value) {
      nextTick(() => {
        if (focusTimer) clearTimeout(focusTimer)
        focusTimer = setTimeout(() => {
          if (editorRef.value) {
            editorRef.value.focus()
            const len = editorRef.value.value.length
            editorRef.value.setSelectionRange(len, len)
          }
        }, 120)
      })
    }
  }
}

const insertTextAtCursor = (text: string) => {
  const textarea = editorRef.value
  if (!textarea) {
    docContent.value += text
    markDirty()
    pushHistory(docContent.value, true)
    return
  }

  const start = typeof textarea.selectionStart === 'number' ? textarea.selectionStart : docContent.value.length
  const end = typeof textarea.selectionEnd === 'number' ? textarea.selectionEnd : docContent.value.length

  const before = docContent.value.substring(0, start)
  const after = docContent.value.substring(end)

  docContent.value = before + text + after
  markDirty()
  pushHistory(docContent.value, true)

  setTimeout(() => {
    textarea.focus()
    const newPos = start + text.length
    textarea.setSelectionRange(newPos, newPos)
  }, 50)
}

const applyInlineSyntax = (prefix: string, suffix: string, defaultText: string = '') => {
  const textarea = editorRef.value
  if (!textarea) return

  const start = textarea.selectionStart ?? 0
  const end = textarea.selectionEnd ?? 0
  const text = docContent.value
  const selected = text.substring(start, end)

  if (selected) {
    const isWrapped = selected.startsWith(prefix) && selected.endsWith(suffix)
    let replacement = ''
    if (isWrapped) {
      replacement = selected.substring(prefix.length, selected.length - suffix.length)
    } else {
      replacement = `${prefix}${selected}${suffix}`
    }
    docContent.value = text.substring(0, start) + replacement + text.substring(end)
    markDirty()
    pushHistory(docContent.value, true)

    setTimeout(() => {
      textarea.focus()
      textarea.setSelectionRange(start, start + replacement.length)
    }, 50)
    return
  }

  const replacement = `${prefix}${defaultText}${suffix}`
  docContent.value = text.substring(0, start) + replacement + text.substring(end)
  markDirty()
  pushHistory(docContent.value, true)

  setTimeout(() => {
    textarea.focus()
    if (defaultText) {
      textarea.setSelectionRange(start + prefix.length, start + prefix.length + defaultText.length)
    } else {
      const newPos = start + prefix.length
      textarea.setSelectionRange(newPos, newPos)
    }
  }, 50)
}

const applyBlockSyntax = (prefix: string) => {
  const textarea = editorRef.value
  if (!textarea) return

  const start = textarea.selectionStart ?? 0
  const end = textarea.selectionEnd ?? 0
  const text = docContent.value

  const lineStart = text.lastIndexOf('\n', start - 1) + 1
  let lineEnd = text.indexOf('\n', end)
  if (lineEnd === -1) lineEnd = text.length

  const linesText = text.substring(lineStart, lineEnd)
  const lines = linesText.split('\n')

  const blockRegex = /^(#+\s*|>+\s*|- \s*|\* \s*|\d+\.\s*)?/

  const newLines = lines.map(line => {
    const match = line.match(blockRegex)
    const existingPrefix = match ? match[0] : ''

    if (existingPrefix === prefix) {
      return line.substring(existingPrefix.length)
    }
    const content = line.substring(existingPrefix.length)
    return `${prefix}${content}`
  })

  const newText = newLines.join('\n')
  docContent.value = text.substring(0, lineStart) + newText + text.substring(lineEnd)
  markDirty()
  pushHistory(docContent.value, true)

  setTimeout(() => {
    textarea.focus()
    const newEnd = lineStart + newText.length
    textarea.setSelectionRange(lineStart, newEnd)
  }, 50)
}

const triggerImageSelect = () => {
  fileInputRef.value?.click()
}

const handleImageFileChange = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  uploadingImage.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)

    const res: any = await uploadAsset(kbId, docId, formData)

    const imgUrl =
      res?.rawUrl ||
      res?.downloadUrl ||
      res?.url ||
      res?.data?.rawUrl ||
      res?.data?.downloadUrl ||
      res?.data?.url ||
      (typeof res === 'string' ? res : '')

    if (imgUrl) {
      const fileNameClean = (file.name || '图片').replace(/[\[\]]/g, '')
      const markdownImg = `\n![${fileNameClean}](${imgUrl})\n`
      insertTextAtCursor(markdownImg)
    }
  } catch (err) {
    // 降级
  } finally {
    uploadingImage.value = false
    if (fileInputRef.value) fileInputRef.value.value = ''
  }
}

const handleSave = async (options?: { silent?: boolean }) => {
  if (isReadOnly.value) return
  if (autoSaveTimer) {
    clearTimeout(autoSaveTimer)
    autoSaveTimer = null
  }

  if (!options?.silent) {
    saving.value = true
  }

  try {
    await updateDocument(kbId, docId, {
      title: docTitle.value,
      name: docTitle.value,
      content: docContent.value
    })
    isDirty.value = false
    showSavedFeedback()
  } catch (err) {
    isDirty.value = true
    showSavedIndicator.value = false
    if (!options?.silent) {
      message.error('保存失败，请稍后重试')
    }
  } finally {
    saving.value = false
  }
}

const flushSave = async () => {
  if (autoSaveTimer) {
    clearTimeout(autoSaveTimer)
    autoSaveTimer = null
  }
  if (isDirty.value) {
    await handleSave({ silent: true })
  }
}

const handleGoBack = async () => {
  await flushSave()
  sessionStorage.setItem('active_doc_back', String(docId))
  router.back()
}

onMounted(() => {
  fetchDocDetail()
})

onBeforeUnmount(() => {
  if (historyDebounceTimer) {
    clearTimeout(historyDebounceTimer)
    historyDebounceTimer = null
  }
  if (focusTimer) {
    clearTimeout(focusTimer)
    focusTimer = null
  }
  if (savedIndicatorTimer) {
    clearTimeout(savedIndicatorTimer)
    savedIndicatorTimer = null
  }
  void flushSave()
})
</script>

<style scoped>
.no-scrollbar::-webkit-scrollbar {
  display: none;
}
.no-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
</style>
