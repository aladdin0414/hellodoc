<template>
  <div ref="containerRef" class="h-screen overflow-y-auto bg-gray-50 dark:bg-slate-900 text-gray-900 dark:text-slate-100 pb-20 transition-colors no-scrollbar">
    <!-- 顶部 Navigation -->
    <HeaderNav :title="kbInfo?.title || kbInfo?.name || t('mobile.kbDetail.title')" :show-back="true" @back="handleBackToHome">
      <template #right>
        <button
          v-if="canEdit"
          @click="openCreateModal(null, 'file')"
          :title="t('mobile.kbDetail.createAction')"
          class="p-1.5 rounded-xl text-slate-600 dark:text-slate-300 hover:text-blue-600 dark:hover:text-blue-400 hover:bg-slate-100 dark:hover:bg-slate-800 active:scale-95 transition-all"
        >
          <Plus class="w-5 h-5 stroke-[2.5]" />
        </button>
      </template>
    </HeaderNav>

    <main class="p-4 w-full space-y-3 pb-[calc(5.5rem+env(safe-area-inset-bottom))]">
      <!-- 树节点骨架屏 Loading -->
      <div v-if="loading" class="space-y-2 animate-pulse">
        <div v-for="i in 5" :key="i" class="p-2.5 bg-white dark:bg-slate-800/60 border border-gray-200/50 dark:border-slate-700/40 rounded-xl flex items-center justify-between">
          <div class="flex items-center gap-2 flex-1">
            <div class="w-4 h-4 rounded bg-gray-200 dark:bg-slate-700/60"></div>
            <div class="h-3.5 bg-gray-200 dark:bg-slate-700/60 rounded-md w-1/3"></div>
          </div>
        </div>
      </div>

      <!-- 树形节点树结构 -->
      <div v-else-if="displayDocTree.length > 0" class="space-y-1">
        <DocTreeNode
          v-for="node in displayDocTree"
          :key="node.id"
          :node="node"
          :depth="0"
          :expanded-map="expandedMap"
            :selected-id="previewSelectedDocId ?? selectedDocId"
          :is-searching="!!searchQuery.trim()"
          :can-edit="canEdit"
          @select="handleSelectDoc"
            @preview-select="handlePreviewSelect"
            @preview-clear="handlePreviewClear"
          @toggle-expand="handleToggleExpand"
          @create-child="openCreateModal($event, 'file')"
          @delete="handleDeleteNode"
          @more-action="handleMoreAction"
        />
      </div>

      <!-- 空白占位或无搜索结果提示区 -->
      <div v-else class="py-12 text-center space-y-2">
        <p v-if="searchQuery.trim()" class="text-xs text-slate-400 dark:text-slate-500">{{ t('mobile.kbDetail.noSearchMatch', { query: searchQuery }) }}</p>
        <template v-else>
          <p class="text-xs text-gray-400 dark:text-slate-500">{{ t('mobile.kbDetail.empty') }}</p>
          <button
            v-if="canEdit"
            @click="openCreateModal(null, 'file')"
            class="px-4 py-2 bg-blue-600 text-white rounded-xl text-xs font-semibold shadow-md active:scale-95 transition-all"
          >
            {{ t('mobile.kbDetail.createFirstDoc') }}
          </button>
        </template>
      </div>

      <!-- iOS 底部 Safe Area 物理占位块，保障在 iPhone 真机滑动到底部时目录树最后一项完全脱离搜索框遮挡 -->
      <div class="h-24 w-full shrink-0 pointer-events-none pb-[env(safe-area-inset-bottom)]"></div>
    </main>

    <!-- 底部 iOS 原生备忘录风格悬浮搜索工具栏 -->
    <div class="fixed bottom-0 inset-x-0 z-30 bg-slate-50/85 dark:bg-slate-900/85 backdrop-blur-2xl border-t border-slate-200/50 dark:border-slate-800/50 px-4 py-2.5 pb-[calc(0.625rem+env(safe-area-inset-bottom))] transition-colors">
      <div class="flex items-center gap-3 max-w-md mx-auto">
        <!-- 胶囊搜索框 -->
        <div class="relative flex-1 flex items-center transition-all">
          <Search class="w-4 h-4 absolute left-3.5 text-slate-400 dark:text-slate-500 pointer-events-none stroke-[2]" />
          <input
            ref="searchInputRef"
            v-model="searchQuery"
            @focus="isSearchFocused = true"
            type="text"
            :placeholder="t('mobile.kbDetail.searchPlaceholder')"
            class="w-full pl-9 pr-8 py-2 bg-slate-200/60 dark:bg-slate-800/70 rounded-full text-[14px] text-slate-900 dark:text-slate-100 placeholder-slate-400/90 dark:placeholder-slate-500 focus:outline-none focus:bg-slate-200/90 dark:focus:bg-slate-800 transition-all"
          />
          <button
            v-if="searchQuery"
            @click="searchQuery = ''"
            class="absolute right-2.5 w-4 h-4 rounded-full bg-slate-400/40 dark:bg-slate-600/50 text-white flex items-center justify-center active:scale-95 transition-all"
          >
            <X class="w-3 h-3 stroke-[2.5]" />
          </button>
        </div>

        <!-- iOS 风格动态取消按钮 -->
        <button
          v-if="isSearchFocused || searchQuery"
          @click="handleCancelSearch"
          class="text-[15px] text-blue-500 hover:text-blue-600 active:opacity-60 transition-all font-normal shrink-0 px-0.5"
        >
          {{ t('nav.cancel') }}
        </button>
      </div>
    </div>

    <!-- 移动端 ActionSheet 操作弹出选择框 -->
    <div
      v-if="showActionSheet"
      class="fixed inset-0 z-50 bg-slate-950/60 backdrop-blur-sm flex flex-col justify-end transition-opacity"
      @click="showActionSheet = false"
    >
      <div
        class="bg-white dark:bg-slate-800 rounded-t-3xl p-4 space-y-2 shadow-2xl transition-transform transform translate-y-0 pb-[calc(1rem+env(safe-area-inset-bottom))]"
        @click.stop
      >
        <!-- iOS 顶部 HandleBar 指示条 -->
        <div class="w-9 h-1 rounded-full bg-slate-300 dark:bg-slate-600 mx-auto mb-1.5 opacity-80"></div>

        <!-- 弹窗顶部标头 -->
        <div class="px-2 py-1.5 border-b border-slate-100 dark:border-slate-700/60 flex items-center justify-between">
          <div class="flex items-center gap-2 truncate pr-2">
            <Folder v-if="actionNode?.type === 'folder'" class="w-4 h-4 text-amber-500 shrink-0" />
            <FileText v-else class="w-4 h-4 text-blue-500 shrink-0" />
            <span class="text-xs font-bold text-gray-700 dark:text-slate-300 truncate">
              {{ actionNode?.title || actionNode?.name }}
            </span>
          </div>
          <span class="text-[10px] px-2 py-0.5 rounded bg-gray-100 dark:bg-slate-700 text-gray-500 dark:text-slate-400">
            {{ actionNode?.type === 'folder' ? t('editorMenu.folderTag') : t('editorMenu.docTag') }}
          </span>
        </div>

        <!-- 针对文件夹/目录的选择项 -->
        <template v-if="actionNode?.type === 'folder'">
          <button
            @click="handleActionSheetCreate('file')"
            class="w-full py-3 px-4 rounded-xl flex items-center gap-3 text-sm font-medium text-gray-800 dark:text-slate-200 hover:bg-gray-50 dark:hover:bg-slate-700/50 active:bg-gray-100 dark:active:bg-slate-700 transition-colors"
          >
            <FilePlus class="w-4 h-4 text-blue-500" />
            <span>{{ t('editorMenu.newSubDoc') }}</span>
          </button>
          <button
            @click="handleActionSheetCreate('folder')"
            class="w-full py-3 px-4 rounded-xl flex items-center gap-3 text-sm font-medium text-gray-800 dark:text-slate-200 hover:bg-gray-50 dark:hover:bg-slate-700/50 active:bg-gray-100 dark:active:bg-slate-700 transition-colors"
          >
            <FolderPlus class="w-4 h-4 text-amber-500" />
            <span>{{ t('editorMenu.newSubFolder') }}</span>
          </button>
          <button
            @click="handleActionSheetStartRename"
            class="w-full py-3 px-4 rounded-xl flex items-center gap-3 text-sm font-medium text-gray-800 dark:text-slate-200 hover:bg-gray-50 dark:hover:bg-slate-700/50 active:bg-gray-100 dark:active:bg-slate-700 transition-colors"
          >
            <Edit3 class="w-4 h-4 text-emerald-500" />
            <span>{{ t('editorMenu.renameFolder') }}</span>
          </button>
          <button
            @click="handleMoveDirection('up')"
            class="w-full py-3 px-4 rounded-xl flex items-center gap-3 text-sm font-medium text-gray-800 dark:text-slate-200 hover:bg-gray-50 dark:hover:bg-slate-700/50 active:bg-gray-100 dark:active:bg-slate-700 transition-colors"
          >
            <ArrowUp class="w-4 h-4 text-blue-500" />
            <span>{{ t('editorMenu.moveUp') }}</span>
          </button>
          <button
            @click="handleMoveDirection('down')"
            class="w-full py-3 px-4 rounded-xl flex items-center gap-3 text-sm font-medium text-gray-800 dark:text-slate-200 hover:bg-gray-50 dark:hover:bg-slate-700/50 active:bg-gray-100 dark:active:bg-slate-700 transition-colors"
          >
            <ArrowDown class="w-4 h-4 text-blue-500" />
            <span>{{ t('editorMenu.moveDown') }}</span>
          </button>
          <button
            @click="handleActionSheetDuplicate"
            class="w-full py-3 px-4 rounded-xl flex items-center gap-3 text-sm font-medium text-gray-800 dark:text-slate-200 hover:bg-gray-50 dark:hover:bg-slate-700/50 active:bg-gray-100 dark:active:bg-slate-700 transition-colors"
          >
            <Copy class="w-4 h-4 text-indigo-500" />
            <span>{{ t('editorMenu.duplicate') }}</span>
          </button>
          <button
            @click="handleActionSheetFolderStatus('published')"
            class="w-full py-3 px-4 rounded-xl flex items-center gap-3 text-sm font-medium text-gray-800 dark:text-slate-200 hover:bg-gray-50 dark:hover:bg-slate-700/50 active:bg-gray-100 dark:active:bg-slate-700 transition-colors"
          >
            <FileCheck class="w-4 h-4 text-emerald-500" />
            <span>{{ t('editorMenu.batchPublish') }}</span>
          </button>
          <button
            @click="handleActionSheetFolderStatus('draft')"
            class="w-full py-3 px-4 rounded-xl flex items-center gap-3 text-sm font-medium text-gray-800 dark:text-slate-200 hover:bg-gray-50 dark:hover:bg-slate-700/50 active:bg-gray-100 dark:active:bg-slate-700 transition-colors"
          >
            <FileX class="w-4 h-4 text-amber-500" />
            <span>{{ t('editorMenu.setAllDraft') }}</span>
          </button>
          <button
            @click="handleActionSheetDelete"
            class="w-full py-3 px-4 rounded-xl flex items-center gap-3 text-sm font-semibold text-rose-600 dark:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-500/10 active:bg-rose-100 dark:active:bg-rose-500/20 transition-colors"
          >
            <Trash2 class="w-4 h-4 text-rose-500" />
            <span>{{ t('editorMenu.deleteFolder') }}</span>
          </button>
        </template>

        <!-- 针对文档的选择项 -->
        <template v-else>
          <button
            @click="handleActionSheetEdit"
            class="w-full py-3 px-4 rounded-xl flex items-center gap-3 text-sm font-medium text-gray-800 dark:text-slate-200 hover:bg-gray-50 dark:hover:bg-slate-700/50 active:bg-gray-100 dark:active:bg-slate-700 transition-colors"
          >
            <FileText class="w-4 h-4 text-blue-500" />
            <span>{{ t('editorMenu.editDoc') }}</span>
          </button>
          <button
            @click="handleActionSheetStartRename"
            class="w-full py-3 px-4 rounded-xl flex items-center gap-3 text-sm font-medium text-gray-800 dark:text-slate-200 hover:bg-gray-50 dark:hover:bg-slate-700/50 active:bg-gray-100 dark:active:bg-slate-700 transition-colors"
          >
            <Edit3 class="w-4 h-4 text-emerald-500" />
            <span>{{ t('editorMenu.renameDoc') }}</span>
          </button>
          <button
            @click="handleMoveDirection('up')"
            class="w-full py-3 px-4 rounded-xl flex items-center gap-3 text-sm font-medium text-gray-800 dark:text-slate-200 hover:bg-gray-50 dark:hover:bg-slate-700/50 active:bg-gray-100 dark:active:bg-slate-700 transition-colors"
          >
            <ArrowUp class="w-4 h-4 text-blue-500" />
            <span>{{ t('editorMenu.moveUp') }}</span>
          </button>
          <button
            @click="handleMoveDirection('down')"
            class="w-full py-3 px-4 rounded-xl flex items-center gap-3 text-sm font-medium text-gray-800 dark:text-slate-200 hover:bg-gray-50 dark:hover:bg-slate-700/50 active:bg-gray-100 dark:active:bg-slate-700 transition-colors"
          >
            <ArrowDown class="w-4 h-4 text-blue-500" />
            <span>{{ t('editorMenu.moveDown') }}</span>
          </button>
          <button
            @click="handleActionSheetDuplicate"
            class="w-full py-3 px-4 rounded-xl flex items-center gap-3 text-sm font-medium text-gray-800 dark:text-slate-200 hover:bg-gray-50 dark:hover:bg-slate-700/50 active:bg-gray-100 dark:active:bg-slate-700 transition-colors"
          >
            <Copy class="w-4 h-4 text-indigo-500" />
            <span>{{ t('editorMenu.duplicate') }}</span>
          </button>
          <button
            @click="handleActionSheetToggleDocStatus"
            class="w-full py-3 px-4 rounded-xl flex items-center gap-3 text-sm font-medium text-gray-800 dark:text-slate-200 hover:bg-gray-50 dark:hover:bg-slate-700/50 active:bg-gray-100 dark:active:bg-slate-700 transition-colors"
          >
            <component :is="actionNode?.status === 'published' ? FileX : FileCheck" class="w-4 h-4 text-amber-500" />
            <span>{{ actionNode?.status === 'published' ? t('editorMenu.toDraft') : t('editorMenu.publishDoc') }}</span>
          </button>
          <button
            @click="handleActionSheetDelete"
            class="w-full py-3 px-4 rounded-xl flex items-center gap-3 text-sm font-semibold text-rose-600 dark:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-500/10 active:bg-rose-100 dark:active:bg-rose-500/20 transition-colors"
          >
            <Trash2 class="w-4 h-4 text-rose-500" />
            <span>{{ t('editorMenu.deleteDoc') }}</span>
          </button>
        </template>

        <!-- 取消按键 -->
        <div class="pt-1">
          <button
            @click="showActionSheet = false"
            class="w-full py-3 rounded-xl bg-gray-100 dark:bg-slate-700 text-sm font-bold text-gray-700 dark:text-slate-300 active:scale-[0.99] transition-all"
          >
            {{ t('nav.cancel') }}
          </button>
        </div>
      </div>
    </div>

    <!-- 重命名模态框 -->
    <div v-if="showRenameModal" class="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4">
      <div class="bg-white dark:bg-slate-800 border border-gray-200 dark:border-slate-700 rounded-2xl w-full max-w-sm p-5 space-y-4 shadow-2xl">
        <h3 class="text-base font-bold text-gray-900 dark:text-slate-100">
            {{ renameTargetNode?.type === 'folder' ? t('mobile.kbDetail.renameFolderTitle') : t('mobile.kbDetail.renameDocTitle') }}
        </h3>

        <div class="space-y-3 text-sm">
          <div>
              <label class="block text-xs text-gray-500 dark:text-slate-400 mb-1">{{ t('mobile.kbDetail.newNameLabel') }}</label>
            <input
              v-model="renameTitle"
              type="text"
                :placeholder="t('mobile.kbDetail.newNamePlaceholder')"
              class="w-full px-3 py-2 bg-gray-50 dark:bg-slate-900 border border-gray-200 dark:border-slate-700 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/40"
            />
          </div>
        </div>

        <div class="flex items-center justify-end gap-2 pt-2">
          <button
            @click="showRenameModal = false"
            class="px-4 py-2 text-xs font-medium text-gray-600 dark:text-slate-400 hover:bg-gray-100 dark:hover:bg-slate-700 rounded-xl transition-colors"
          >
            {{ t('nav.cancel') }}
          </button>
          <button
            @click="handleConfirmRename"
            :disabled="submittingRename || !renameTitle.trim()"
            class="px-4 py-2 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-500 disabled:opacity-50 rounded-xl shadow-md active:scale-95 transition-all"
          >
            {{ submittingRename ? t('editor.saving') : t('mobile.kbDetail.saveChanges') }}
          </button>
        </div>
      </div>
    </div>

    <!-- 挂载移动端通用 ConfirmDialog 确认对话框组件 -->
    <ConfirmDialog
      v-model:show="showDeleteModal"
      :title="t('mobile.kbDetail.confirmDeleteTitle')"
      :confirm-text="t('mobile.kbDetail.confirmDeleteAction')"
      confirm-type="danger"
      :loading="submittingDelete"
      @confirm="handleConfirmDelete"
    >
      {{
        deleteTargetNode?.type === 'folder'
          ? t('mobile.kbDetail.confirmDeleteFolder', { name: deleteTargetNode?.title || deleteTargetNode?.name })
          : t('mobile.kbDetail.confirmDeleteDoc', { name: deleteTargetNode?.title || deleteTargetNode?.name })
      }}
      <template v-if="deleteTargetNode?.type === 'folder'">
        {{ t('mobile.kbDetail.confirmDeleteFolderExtra') }}
      </template>
    </ConfirmDialog>

    <!-- 新建节点（文档或目录）模态框 -->
    <div v-if="showCreateModal" class="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4">
      <div class="bg-white dark:bg-slate-800 border border-gray-200 dark:border-slate-700 rounded-2xl w-full max-w-sm p-5 space-y-4 shadow-2xl">
        <h3 class="text-base font-bold text-gray-900 dark:text-slate-100">
            {{ targetParentId ? t('mobile.kbDetail.createChildTitle') : t('mobile.kbDetail.createRootTitle') }}
        </h3>

        <div class="space-y-3">
          <div>
            <label class="block text-xs text-gray-700 dark:text-slate-300 mb-1">{{ t('mobile.kbDetail.typeLabel') }}</label>
            <div class="grid grid-cols-2 gap-2">
              <button
                @click="newDocType = 'file'"
                :class="[newDocType === 'file' ? 'bg-blue-600 text-white' : 'bg-gray-100 dark:bg-slate-900 text-gray-600 dark:text-slate-400']"
                class="py-1.5 rounded-xl text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors"
              >
                <FileText class="w-3.5 h-3.5" />
                <span>{{ t('mobile.kbDetail.docType') }}</span>
              </button>
              <button
                @click="newDocType = 'folder'"
                :class="[newDocType === 'folder' ? 'bg-amber-600 text-white' : 'bg-gray-100 dark:bg-slate-900 text-gray-600 dark:text-slate-400']"
                class="py-1.5 rounded-xl text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors"
              >
                <Folder class="w-3.5 h-3.5" />
                <span>{{ t('mobile.kbDetail.folderType') }}</span>
              </button>
            </div>
          </div>

          <div>
            <label class="block text-xs text-gray-700 dark:text-slate-300 mb-1">{{ t('mobile.kbDetail.nameLabel') }}</label>
            <input
              v-model="newDocName"
              type="text"
              :placeholder="newDocType === 'folder' ? t('mobile.kbDetail.folderNamePlaceholder') : t('mobile.kbDetail.docNamePlaceholder')"
              class="w-full px-3 py-2 bg-gray-50 dark:bg-slate-900 border border-gray-200 dark:border-slate-700 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/40"
            />
          </div>
        </div>

        <div class="flex items-center justify-end gap-2 pt-2">
          <button @click="showCreateModal = false" class="px-3 py-1.5 text-xs text-gray-500 dark:text-slate-400">{{ t('nav.cancel') }}</button>
          <button @click="handleCreateNode" class="px-4 py-1.5 bg-blue-600 text-white text-xs font-semibold rounded-xl">{{ t('common.create') }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, onActivated, onDeactivated, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import HeaderNav from '../components/HeaderNav.vue'
import DocTreeNode from '../components/DocTreeNode.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import { Plus, Folder, FileText, FilePlus, FolderPlus, Trash2, Edit3, Copy, FileCheck, FileX, ArrowUp, ArrowDown, Search, X } from 'lucide-vue-next'
import { getKbDetail, getAuthDocuments } from '../../api/kb'
import { createDocument, updateDocument, deleteDocument, duplicateDocument } from '../../api/document'
import type { KnowledgeBase } from '../../types/kb'

const { t } = useI18n()

interface DocNode {
  id: number
  kbId: number
  parentId?: number | null
  name?: string
  title?: string
  type?: 'folder' | 'file' | 'doc' | string
  status?: string
  orderNum?: number
  sortOrder?: number
  children?: DocNode[]
}

defineOptions({
  name: 'KbDetailView'
})

const route = useRoute()
const router = useRouter()
const kbId = Number(route.params.kbId)

const handleBackToHome = () => {
  const from = route.query.from as string
  if (from) {
    router.push(from)
  } else {
    router.push('/m')
  }
}

const loading = ref(false)
const kbInfo = ref<KnowledgeBase | null>(null)
const containerRef = ref<HTMLDivElement | null>(null)
const lastFetchedAt = ref(0)
const returnSnapshotStorageKey = `m_return_snapshot_${kbId}`

const persistScrollPosition = () => {
  if (!containerRef.value) return
  try {
    sessionStorage.setItem(`m_scroll_${kbId}`, String(containerRef.value.scrollTop))
  } catch (e) {
    // ignore
  }
}

const waitForSelectionPaint = async () => {
  await nextTick()
  await new Promise<void>((resolve) => {
    requestAnimationFrame(() => {
      requestAnimationFrame(() => resolve())
    })
  })
  await new Promise<void>((resolve) => {
    window.setTimeout(() => resolve(), 120)
  })
}

const restoreScrollPosition = () => {
  try {
    const savedScroll = sessionStorage.getItem(`m_scroll_${kbId}`)
    if (savedScroll && containerRef.value) {
      const scrollTop = Number(savedScroll)
      nextTick(() => {
        if (containerRef.value) {
          containerRef.value.scrollTop = scrollTop
        }
        requestAnimationFrame(() => {
          if (containerRef.value) {
            containerRef.value.scrollTop = scrollTop
          }
        })
      })
    }
  } catch (e) {}
}

const getDocNodeElement = (docId: number) => {
  if (!containerRef.value) return null
  return containerRef.value.querySelector<HTMLElement>(`[data-doc-node-id="${docId}"]`)
}

const captureReturnSnapshot = (docId: number) => {
  const container = containerRef.value
  const targetNode = getDocNodeElement(docId)
  if (!container || !targetNode) return

  const containerRect = container.getBoundingClientRect()
  const targetRect = targetNode.getBoundingClientRect()

  try {
    sessionStorage.setItem(returnSnapshotStorageKey, JSON.stringify({
      docId,
      scrollTop: container.scrollTop,
      relativeTop: targetRect.top - containerRect.top
    }))
  } catch (e) {
    // ignore
  }
}

const restoreReturnSnapshot = (docId: number) => {
  const container = containerRef.value
  if (!container) return false

  try {
    const rawSnapshot = sessionStorage.getItem(returnSnapshotStorageKey)
    if (!rawSnapshot) return false

    const snapshot = JSON.parse(rawSnapshot) as {
      docId?: number
      scrollTop?: number
      relativeTop?: number
    }

    if (snapshot.docId !== docId) return false

    nextTick(() => {
      requestAnimationFrame(() => {
        const activeContainer = containerRef.value
        const targetNode = getDocNodeElement(docId)
        if (!activeContainer || !targetNode) return

        const activeContainerRect = activeContainer.getBoundingClientRect()
        const targetRect = targetNode.getBoundingClientRect()
        const targetTop = targetRect.top - activeContainerRect.top + activeContainer.scrollTop
        const relativeTop = typeof snapshot.relativeTop === 'number' ? snapshot.relativeTop : 0
        const nextScrollTop = Math.max(targetTop - relativeTop, 0)

        activeContainer.scrollTo({
          top: nextScrollTop,
          behavior: 'auto'
        })
      })
    })

    sessionStorage.removeItem(returnSnapshotStorageKey)
    return true
  } catch (e) {
    return false
  }
}

const canEdit = computed(() => {
  if (!kbInfo.value || !kbInfo.value.role) return true
  return (kbInfo.value.role || '').toUpperCase() !== 'VIEWER'
})

const docTree = ref<DocNode[]>([])
const searchQuery = ref('')
const isSearchFocused = ref(false)
const searchInputRef = ref<HTMLInputElement | null>(null)

const handleCancelSearch = () => {
  searchQuery.value = ''
  isSearchFocused.value = false
  if (searchInputRef.value) {
    searchInputRef.value.blur()
  }
}

const filterTreeRecursively = (nodes: DocNode[], query: string): DocNode[] => {
  const q = query.trim().toLowerCase()
  if (!q) return nodes

  const result: DocNode[] = []
  for (const node of nodes) {
    const titleMatch = (node.title || node.name || '').toLowerCase().includes(q)
    const filteredChildren = node.children ? filterTreeRecursively(node.children, q) : []

    if (titleMatch || filteredChildren.length > 0) {
      result.push({
        ...node,
        children: filteredChildren
      })
    }
  }
  return result
}

const displayDocTree = computed(() => {
  if (!searchQuery.value.trim()) {
    return docTree.value
  }
  return filterTreeRecursively(docTree.value, searchQuery.value)
})

const expandedMap = ref<Record<string, boolean>>({})

const persistExpandedMap = () => {
  try {
    sessionStorage.setItem(`m_expanded_${kbId}`, JSON.stringify(expandedMap.value))
  } catch (e) {
    // ignore
  }
}

const getInitialSelectedDocId = (): number | null => {
  try {
    const activeDocId = route.query.activeDocId
      ? String(route.query.activeDocId)
      : sessionStorage.getItem('active_doc_back')
    return activeDocId ? Number(activeDocId) : null
  } catch (e) {
    return null
  }
}
const selectedDocId = ref<number | null>(getInitialSelectedDocId())
const previewSelectedDocId = ref<number | null>(null)

const showCreateModal = ref(false)
const targetParentId = ref<number | null>(null)
const newDocType = ref<'file' | 'folder'>('file')
const newDocName = ref('')

const showActionSheet = ref(false)
const actionNode = ref<DocNode | null>(null)

const showRenameModal = ref(false)
const renameTargetNode = ref<DocNode | null>(null)
const renameTitle = ref('')
const submittingRename = ref(false)

const showDeleteModal = ref(false)
const deleteTargetNode = ref<DocNode | null>(null)
const submittingDelete = ref(false)

const handleMoreAction = (node: DocNode) => {
  if (!canEdit.value) return
  actionNode.value = node
  showActionSheet.value = true
}

const handleActionSheetCreate = (type: 'file' | 'folder') => {
  showActionSheet.value = false
  if (actionNode.value) {
    openCreateModal(actionNode.value.id, type)
  }
}

const handleActionSheetEdit = () => {
  showActionSheet.value = false
  if (actionNode.value) {
    handleSelectDoc(actionNode.value.id)
  }
}

const handleActionSheetDelete = () => {
  showActionSheet.value = false
  if (actionNode.value) {
    handleDeleteNode(actionNode.value)
  }
}

const handleActionSheetStartRename = () => {
  showActionSheet.value = false
  if (!actionNode.value) return
  renameTargetNode.value = actionNode.value
  renameTitle.value = actionNode.value.title || actionNode.value.name || ''
  showRenameModal.value = true
}

const handleConfirmRename = async () => {
  if (!renameTargetNode.value || !renameTitle.value.trim()) return
  submittingRename.value = true
  try {
    const title = renameTitle.value.trim()
    await updateDocument(kbId, renameTargetNode.value.id, {
      name: title,
      title: title
    })
    showRenameModal.value = false
    await fetchData()
  } catch (err) {
    // ignore
  } finally {
    submittingRename.value = false
  }
}

const handleActionSheetDuplicate = async () => {
  showActionSheet.value = false
  if (!actionNode.value) return
  try {
    await duplicateDocument(kbId, actionNode.value.id)
    await fetchData()
  } catch (err) {
    // ignore
  }
}

const handleActionSheetToggleDocStatus = async () => {
  showActionSheet.value = false
  if (!actionNode.value) return
  try {
    const nextStatus = actionNode.value.status === 'published' ? 'draft' : 'published'
    await updateDocument(kbId, actionNode.value.id, {
      status: nextStatus
    })
    await fetchData()
  } catch (err) {
    // ignore
  }
}

const handleActionSheetFolderStatus = async (status: 'published' | 'draft') => {
  showActionSheet.value = false
  if (!actionNode.value) return
  try {
    await updateDocument(kbId, actionNode.value.id, {
      status: status
    })
    await fetchData()
  } catch (err) {
    // ignore
  }
}

const swapNodesInLocalTree = (nodes: DocNode[], targetNode: DocNode, direction: 'up' | 'down'): boolean => {
  const targetParentId = targetNode.parentId ?? null
  const isDirectSibling = targetParentId === null
    ? nodes.some(n => n.id === targetNode.id)
    : false

  if (isDirectSibling) {
    const idx = nodes.findIndex(n => n.id === targetNode.id)
    const swapIdx = direction === 'up' ? idx - 1 : idx + 1
    if (idx !== -1 && swapIdx >= 0 && swapIdx < nodes.length) {
      const temp = nodes[idx]
      const swapNode = nodes[swapIdx]
      if (temp && swapNode) {
        nodes[idx] = swapNode
        nodes[swapIdx] = temp
        return true
      }
    }
  }

  for (const n of nodes) {
    if (n.children && n.children.length > 0) {
      if (n.id === targetParentId) {
        const idx = n.children.findIndex(c => c.id === targetNode.id)
        const swapIdx = direction === 'up' ? idx - 1 : idx + 1
        if (idx !== -1 && swapIdx >= 0 && swapIdx < n.children.length) {
          const temp = n.children[idx]
          const swapNode = n.children[swapIdx]
          if (temp && swapNode) {
            n.children[idx] = swapNode
            n.children[swapIdx] = temp
            return true
          }
        }
      } else {
        if (swapNodesInLocalTree(n.children, targetNode, direction)) return true
      }
    }
  }
  return false
}

const handleMoveDirection = async (direction: 'up' | 'down') => {
  showActionSheet.value = false
  if (!actionNode.value) return
  const node = actionNode.value
  const targetParentId = node.parentId ?? null

  const findSiblings = (nodes: DocNode[]): DocNode[] | null => {
    if (targetParentId === null) {
      return nodes.some(n => n.id === node.id) ? nodes : null
    }
    for (const n of nodes) {
      if (n.id === targetParentId && n.children) {
        return n.children
      }
      if (n.children && n.children.length > 0) {
        const found = findSiblings(n.children)
        if (found) return found
      }
    }
    return null
  }

  const siblings = findSiblings(docTree.value) || docTree.value.filter(d => (d.parentId ?? null) === targetParentId)
  if (!siblings || siblings.length <= 1) return

  const index = siblings.findIndex(s => s.id === node.id)
  if (index === -1) return

  const targetIndex = direction === 'up' ? index - 1 : index + 1
  if (targetIndex < 0 || targetIndex >= siblings.length) return

  const swapTarget = siblings[targetIndex]
  if (!swapTarget) return

  const currentOrder = node.orderNum ?? node.sortOrder ?? index
  const targetOrder = swapTarget.orderNum ?? swapTarget.sortOrder ?? targetIndex

  // 1. 乐观更新：本地内存数据同步交换
  node.orderNum = targetOrder
  node.sortOrder = targetOrder
  swapTarget.orderNum = currentOrder
  swapTarget.sortOrder = currentOrder

  // 2. 本地响应式树结构交换，UI 瞬间无缝上移/下移
  swapNodesInLocalTree(docTree.value, node, direction)

  // 3. 后台并发静默请求接口，绝不上锁全屏刷新
  void Promise.all([
    updateDocument(kbId, node.id, { orderNum: targetOrder, sortOrder: targetOrder }),
    updateDocument(kbId, swapTarget.id, { orderNum: currentOrder, sortOrder: currentOrder })
  ])
}

const openCreateModal = (parentId: number | null, type: 'file' | 'folder' = 'file') => {
  targetParentId.value = parentId
  newDocType.value = type
  newDocName.value = ''
  showCreateModal.value = true
}

const handleToggleExpand = (id: number | string) => {
  const key = String(id)
  expandedMap.value = {
    ...expandedMap.value,
    [key]: !expandedMap.value[key]
  }
  persistExpandedMap()
}

const handlePreviewSelect = (docId: number) => {
  previewSelectedDocId.value = docId
}

const handlePreviewClear = (docId: number) => {
  if (previewSelectedDocId.value === docId) {
    previewSelectedDocId.value = null
  }
}

const handleSelectDoc = async (docId: number, options?: { mode?: 'edit' | 'preview'; autoFocus?: boolean }) => {
  // 乐观高亮：点击节点的瞬间立即响应高亮，无需等待页面返回
  previewSelectedDocId.value = docId
  selectedDocId.value = docId
  try {
    sessionStorage.setItem(`m_selected_doc_${kbId}`, String(docId))
  } catch (e) {}

  captureReturnSnapshot(docId)
  persistScrollPosition()
  sessionStorage.setItem(`last_doc_${kbId}`, String(docId))
  sessionStorage.setItem('active_doc_back', String(docId))
  const query: Record<string, string> = {}
  if (route.query.from) {
    query.from = String(route.query.from)
  }
  if (options?.mode) {
    query.mode = options.mode
  }
  if (options?.autoFocus) {
    query.autoFocus = 'true'
  }

  await waitForSelectionPaint()

  await router.push({
    path: `/m/kb/${kbId}/doc/${docId}`,
    query
  })
}

const sortNodes = (a: DocNode, b: DocNode) => {
  const orderA = a.orderNum ?? a.sortOrder ?? 0
  const orderB = b.orderNum ?? b.sortOrder ?? 0
  if (orderA !== orderB) return orderA - orderB
  const typeA = a.type === 'folder' ? 0 : 1
  const typeB = b.type === 'folder' ? 0 : 1
  if (typeA !== typeB) return typeA - typeB
  const nameA = a.title || a.name || ''
  const nameB = b.title || b.name || ''
  return nameA.localeCompare(nameB, 'zh-CN')
}

const findAndInsertChild = (nodes: DocNode[], parentId: number, newNode: DocNode): boolean => {
  for (const node of nodes) {
    if (node.id === parentId) {
      if (!node.children) node.children = []
      node.children.push(newNode)
      node.children.sort(sortNodes)
      return true
    }
    if (node.children && node.children.length > 0) {
      if (findAndInsertChild(node.children, parentId, newNode)) return true
    }
  }
  return false
}

const removeNodeFromTree = (nodes: DocNode[], id: number): DocNode[] => {
  return nodes
    .filter(n => n.id !== id)
    .map(n => {
      if (n.children && n.children.length > 0) {
        n.children = removeNodeFromTree(n.children, id)
      }
      return n
    })
}

const buildTreeFromFlatList = (flatList: DocNode[]): DocNode[] => {
  const nodeMap = new Map<number, DocNode>()
  const rootNodes: DocNode[] = []

  flatList.forEach(item => {
    nodeMap.set(item.id, { ...item, children: [] })
  })

  flatList.forEach(item => {
    const node = nodeMap.get(item.id)!
    if (item.parentId && nodeMap.has(item.parentId)) {
      const parent = nodeMap.get(item.parentId)!
      if (!parent.children) parent.children = []
      parent.children.push(node)
    } else {
      rootNodes.push(node)
    }
  })

  const sortTreeRecursively = (nodes: DocNode[]) => {
    nodes.sort(sortNodes)
    nodes.forEach(node => {
      if (node.children && node.children.length > 0) {
        sortTreeRecursively(node.children)
      }
    })
  }

  const finalTree = rootNodes.length > 0 ? rootNodes : flatList
  sortTreeRecursively(finalTree)
  return finalTree
}

const fetchData = async () => {
  // 乐观更新原则：优先读取缓存快照渲染，防止返回页面时骨架屏抖动
  let hasCache = false
  try {
    const cachedTree = sessionStorage.getItem(`m_cache_tree_${kbId}`)
    const cachedInfo = sessionStorage.getItem(`m_cache_info_${kbId}`)
    const savedExpanded = sessionStorage.getItem(`m_expanded_${kbId}`)

    if (savedExpanded) {
      expandedMap.value = { ...JSON.parse(savedExpanded) }
    }

    if (cachedInfo) {
      kbInfo.value = JSON.parse(cachedInfo)
    }

    if (cachedTree) {
      const parsedTree = JSON.parse(cachedTree)
      if (Array.isArray(parsedTree) && parsedTree.length > 0) {
        docTree.value = parsedTree
        hasCache = true
      }
    }
  } catch (e) {
    // ignore
  }

  const activeDocId = route.query.activeDocId
    ? String(route.query.activeDocId)
    : sessionStorage.getItem('active_doc_back')

  if (activeDocId) {
    sessionStorage.removeItem('active_doc_back')
    selectedDocId.value = Number(activeDocId)
      previewSelectedDocId.value = null
    if (hasCache) {
      autoExpandParentsOfDoc(docTree.value, Number(activeDocId))
      persistExpandedMap()
    }
  } else {
    selectedDocId.value = null
      previewSelectedDocId.value = null
  }

    if (hasCache) {
      if (activeDocId) {
        restoreReturnSnapshot(Number(activeDocId))
      } else {
        restoreScrollPosition()
      }
    }

  // 若无缓存则展示骨架屏；若有缓存则保持 loading=false 静默更新
  if (!hasCache) {
    loading.value = true
  }

  try {
    const kbRes = await getKbDetail(kbId).catch(() => null)
    if (kbRes) {
      kbInfo.value = kbRes
      try {
        sessionStorage.setItem(`m_cache_info_${kbId}`, JSON.stringify(kbRes))
      } catch (e) {}
    }

    const docRes: any = await getAuthDocuments(kbId)
    const list = Array.isArray(docRes) ? docRes : (Array.isArray(docRes?.data) ? docRes.data : [])
    const newTree = buildTreeFromFlatList(list)
    docTree.value = newTree

    try {
      sessionStorage.setItem(`m_cache_tree_${kbId}`, JSON.stringify(newTree))
    } catch (e) {}

    if (activeDocId) {
      autoExpandParentsOfDoc(docTree.value, Number(activeDocId))
      persistExpandedMap()
    }

      if (activeDocId) {
        restoreReturnSnapshot(Number(activeDocId))
      } else {
        restoreScrollPosition()
      }
    lastFetchedAt.value = Date.now()
  } catch (err) {
    if (!hasCache) {
      docTree.value = []
    }
  } finally {
    loading.value = false
      if (activeDocId) {
        restoreReturnSnapshot(Number(activeDocId))
      } else {
        restoreScrollPosition()
      }
  }
}

const autoExpandParentsOfDoc = (nodes: DocNode[], targetId: number): boolean => {
  for (const node of nodes) {
    if (node.id === targetId) {
      if (node.children && node.children.length > 0) {
        expandedMap.value[String(node.id)] = true
      }
      return true
    }
    if (node.children && node.children.length > 0) {
      const foundInChild = autoExpandParentsOfDoc(node.children, targetId)
      if (foundInChild) {
        expandedMap.value[String(node.id)] = true
        return true
      }
    }
  }
  return false
}

const syncActiveDocState = () => {
  const activeDocId = route.query.activeDocId
    ? String(route.query.activeDocId)
    : sessionStorage.getItem('active_doc_back')

  if (!activeDocId) {
    selectedDocId.value = null
    previewSelectedDocId.value = null
    return false
  }

  const nextSelectedId = Number(activeDocId)
  if (!Number.isFinite(nextSelectedId)) {
    selectedDocId.value = null
    previewSelectedDocId.value = null
    return false
  }

  selectedDocId.value = nextSelectedId
  previewSelectedDocId.value = null
  try {
    sessionStorage.setItem(`m_selected_doc_${kbId}`, String(nextSelectedId))
  } catch (e) {
    // ignore
  }

  autoExpandParentsOfDoc(docTree.value, nextSelectedId)
  persistExpandedMap()

  if (sessionStorage.getItem('active_doc_back')) {
    sessionStorage.removeItem('active_doc_back')
  }

  return nextSelectedId
}

const handleCreateNode = async () => {
  if (!newDocName.value.trim()) return
  try {
    const newTitle = newDocName.value.trim()
    const res: any = await createDocument(kbId, {
      title: newTitle,
      name: newTitle,
      type: newDocType.value,
      parentId: targetParentId.value
    })

    showCreateModal.value = false

    if (res && res.id) {
      const newNode: DocNode = {
        id: res.id,
        kbId,
        parentId: targetParentId.value,
        title: res.title || res.name || newTitle,
        name: res.name || res.title || newTitle,
        type: newDocType.value,
        children: []
      }

      if (targetParentId.value) {
        findAndInsertChild(docTree.value, targetParentId.value, newNode)
        expandedMap.value[String(targetParentId.value)] = true
      } else {
        docTree.value.push(newNode)
        docTree.value.sort(sortNodes)
      }

      if (newDocType.value === 'file') {
        handleSelectDoc(res.id, { mode: 'edit', autoFocus: true })
      }
    } else {
      await fetchData()
    }
  } catch (err) {
    await fetchData()
  }
}

const handleDeleteNode = (node: DocNode) => {
  deleteTargetNode.value = node
  showDeleteModal.value = true
}

const handleConfirmDelete = async () => {
  if (!deleteTargetNode.value) return
  submittingDelete.value = true
  const targetId = deleteTargetNode.value.id
  try {
    await deleteDocument(kbId, targetId)
    docTree.value = removeNodeFromTree(docTree.value, targetId)
    showDeleteModal.value = false
    deleteTargetNode.value = null
  } catch (err) {
    await fetchData()
  } finally {
    submittingDelete.value = false
  }
}

const handlePageShow = (event: PageTransitionEvent) => {
  if (event.persisted) {
    void fetchData()
  }
}

onMounted(() => {
  fetchData()
  window.addEventListener('pageshow', handlePageShow)
})

onUnmounted(() => {
  window.removeEventListener('pageshow', handlePageShow)
})

onActivated(() => {
  const restoredDocId = syncActiveDocState()
  if (restoredDocId) {
    restoreReturnSnapshot(restoredDocId)
  } else {
    restoreScrollPosition()
  }

  if (loading.value) return

  if (!docTree.value.length || Date.now() - lastFetchedAt.value > 60_000) {
    void fetchData()
  }
})

onDeactivated(() => {
  previewSelectedDocId.value = null
  persistScrollPosition()
})
</script>
