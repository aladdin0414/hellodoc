<template>
  <div class="space-y-0.5">
    <!-- 当前节点行 -->
    <div
      @click="handleClick"
      @touchstart.passive="handleTouchStart"
      @touchmove.passive="handleTouchMove"
      @touchend="handleTouchEnd"
      @touchcancel="handleTouchCancel"
      @mousedown="handleMouseDown"
      @mouseup="clearPressedState"
      @mouseleave="clearPressedState"
      :data-doc-node-id="node.id"
      :class="[
        isSelected
          ? 'bg-blue-500/10 text-blue-600 dark:text-blue-400 font-semibold'
          : isPressed
            ? 'bg-slate-200/80 dark:bg-slate-800 text-slate-800 dark:text-slate-200'
            : 'text-slate-800 dark:text-slate-200'
      ]"
      class="mobile-tree-node p-2.5 flex items-center justify-between rounded-xl transition-all cursor-pointer select-none touch-manipulation"
      :style="{ paddingLeft: `${depth * 1.15 + 0.625}rem` }"
    >
      <div class="flex items-center gap-2 min-w-0 flex-1">
        <!-- 文件夹可折叠 Icon -->
        <button
          v-if="node.type === 'folder' || (node.children && node.children.length > 0)"
          @click.stop="toggleExpand"
          class="p-1 rounded-md text-slate-400 dark:text-slate-500 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-200/60 dark:hover:bg-slate-700/60 transition-colors"
        >
          <component :is="isExpanded ? ChevronDown : ChevronRight" class="w-3.5 h-3.5 stroke-[2.5]" />
        </button>
        <span v-else class="w-5"></span>

        <!-- 类型 Icon -->
        <Folder v-if="node.type === 'folder'" class="w-4 h-4 text-amber-500 shrink-0 stroke-[2]" />
        <FileText v-else class="w-4 h-4 text-blue-500 shrink-0 stroke-[2]" />

        <!-- 节点名称 -->
        <span class="text-[14px] truncate leading-tight">
          {{ node.title || node.name }}
        </span>
      </div>

      <!-- 右侧操作栏：极简 ... 按钮 (仅具备编辑权限时显示，阻止冒泡触发父行跳转) -->
      <div v-if="canEdit" @click.stop class="flex items-center opacity-70 hover:opacity-100 transition-opacity shrink-0">
        <button
          @click.stop="$emit('more-action', node)"
          :title="t('editor.moreOptions')"
          class="p-2 -mr-1 text-slate-400 dark:text-slate-500 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-200/60 dark:hover:bg-slate-700/60 active:bg-slate-300/60 dark:active:bg-slate-700 rounded-lg transition-colors active:scale-95 touch-manipulation"
        >
          <MoreHorizontal class="w-4 h-4" />
        </button>
      </div>
    </div>

    <!-- 递归子节点列表 (如果展开或处于搜索状态) -->
    <div v-if="(isExpanded || isSearching) && node.children && node.children.length > 0" class="relative">
      <DocTreeNode
        v-for="child in node.children"
        :key="child.id"
        :node="child"
        :depth="depth + 1"
        :expanded-map="expandedMap"
        :selected-id="selectedId"
        :is-searching="isSearching"
        :can-edit="canEdit"
        @select="$emit('select', $event)"
          @preview-select="$emit('preview-select', $event)"
          @preview-clear="$emit('preview-clear', $event)"
        @toggle-expand="$emit('toggle-expand', $event)"
        @create-child="$emit('create-child', $event)"
        @delete="$emit('delete', $event)"
        @more-action="$emit('more-action', $event)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Folder, FileText, ChevronRight, ChevronDown, MoreHorizontal } from 'lucide-vue-next'

const { t } = useI18n()

interface DocNode {
  id: number
  kbId: number
  parentId?: number | null
  name?: string
  title?: string
  type?: 'folder' | 'file' | 'doc' | string
  orderNum?: number
  sortOrder?: number
  children?: DocNode[]
}

const props = withDefaults(
  defineProps<{
    node: DocNode
    depth?: number
    expandedMap?: Record<string, boolean>
    selectedId?: number | null
    isSearching?: boolean
    canEdit?: boolean
  }>(),
  {
    depth: 0,
    expandedMap: () => ({}),
    selectedId: null,
    isSearching: false,
    canEdit: true
  }
)

const emit = defineEmits<{
  select: [docId: number]
    'preview-select': [docId: number]
    'preview-clear': [docId: number]
  'toggle-expand': [nodeId: number]
  'create-child': [parentId: number]
  delete: [node: DocNode]
  'more-action': [node: DocNode]
}>()

const isFolder = computed(() => {
  const t = String(props.node.type || '').toLowerCase()
  return t === 'folder' || t === 'dir' || t === 'directory'
})

const isExpanded = computed(() => {
  if (props.isSearching) return true
  return !!props.expandedMap[String(props.node.id)]
})

const isSelected = computed(() => {
  return !isFolder.value && props.selectedId === props.node.id
})

const isPressed = ref(false)
const touchStartPoint = ref<{ x: number; y: number } | null>(null)
const TOUCH_MOVE_THRESHOLD = 8

const clearPressedState = () => {
  isPressed.value = false
  touchStartPoint.value = null
}

const handleTouchStart = (event: TouchEvent) => {
  const touch = event.touches[0]
  if (!touch) return
  touchStartPoint.value = { x: touch.clientX, y: touch.clientY }
  isPressed.value = true
  if (!isFolder.value) {
    emit('preview-select', props.node.id)
  }
}

const handleTouchMove = (event: TouchEvent) => {
  const touch = event.touches[0]
  if (!touchStartPoint.value || !touch) return

  const deltaX = Math.abs(touch.clientX - touchStartPoint.value.x)
  const deltaY = Math.abs(touch.clientY - touchStartPoint.value.y)
  if (deltaX > TOUCH_MOVE_THRESHOLD || deltaY > TOUCH_MOVE_THRESHOLD) {
    isPressed.value = false
    if (!isFolder.value) {
      emit('preview-clear', props.node.id)
    }
  }
}

const handleTouchEnd = () => {
  clearPressedState()
}

const handleTouchCancel = () => {
  clearPressedState()
  if (!isFolder.value) {
    emit('preview-clear', props.node.id)
  }
}

const handleMouseDown = () => {
  isPressed.value = true
  if (!isFolder.value) {
    emit('preview-select', props.node.id)
  }
}

const toggleExpand = () => {
  emit('toggle-expand', props.node.id)
}

const handleClick = () => {
  if (isFolder.value) {
    toggleExpand()
  } else {
    emit('select', props.node.id)
  }
}
</script>

<style scoped>
.mobile-tree-node {
  -webkit-tap-highlight-color: transparent;
}

@media (hover: hover) and (pointer: fine) {
  .mobile-tree-node:hover {
    background-color: rgb(226 232 240 / 0.5);
  }

  .dark .mobile-tree-node:hover {
    background-color: rgb(30 41 59 / 0.6);
  }
}
</style>
