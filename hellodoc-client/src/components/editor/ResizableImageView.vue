<script setup lang="ts">
import { ref, computed } from 'vue'
import { NodeViewWrapper } from '@tiptap/vue-3'

const props = defineProps<{
    node: any
    updateAttributes: (attrs: Record<string, any>) => void
    selected: boolean
    editor?: { isEditable?: boolean }
}>()

const imgRef = ref<HTMLImageElement | null>(null)
const isResizing = ref(false)

const imgSrc = computed(() => props.node.attrs.src || '')
const imgAlt = computed(() => props.node.attrs.alt || '')
const imgTitle = computed(() => props.node.attrs.title || '')
const displayWidth = computed(() => props.node.attrs.width || null)
const isEditable = computed(() => props.editor?.isEditable !== false)

const startResize = (event: MouseEvent) => {
    if (!isEditable.value) return
    event.preventDefault()
    event.stopPropagation()
    isResizing.value = true

    const startX = event.clientX
    const startWidth = imgRef.value?.offsetWidth || 200

    const onMouseMove = (e: MouseEvent) => {
        const delta = e.clientX - startX
        const newWidth = Math.max(50, startWidth + delta)
        if (imgRef.value) {
            imgRef.value.style.width = `${newWidth}px`
        }
    }

    const onMouseUp = () => {
        isResizing.value = false
        document.removeEventListener('mousemove', onMouseMove)
        document.removeEventListener('mouseup', onMouseUp)
        if (imgRef.value) {
            props.updateAttributes({ width: Math.round(imgRef.value.offsetWidth) })
        }
    }

    document.addEventListener('mousemove', onMouseMove)
    document.addEventListener('mouseup', onMouseUp)
}

const startResizeLeft = (event: MouseEvent) => {
    if (!isEditable.value) return
    event.preventDefault()
    event.stopPropagation()
    isResizing.value = true

    const startX = event.clientX
    const startWidth = imgRef.value?.offsetWidth || 200

    const onMouseMove = (e: MouseEvent) => {
        const delta = startX - e.clientX
        const newWidth = Math.max(50, startWidth + delta)
        if (imgRef.value) {
            imgRef.value.style.width = `${newWidth}px`
        }
    }

    const onMouseUp = () => {
        isResizing.value = false
        document.removeEventListener('mousemove', onMouseMove)
        document.removeEventListener('mouseup', onMouseUp)
        if (imgRef.value) {
            props.updateAttributes({ width: Math.round(imgRef.value.offsetWidth) })
        }
    }

    document.addEventListener('mousemove', onMouseMove)
    document.addEventListener('mouseup', onMouseUp)
}
</script>

<template>
    <NodeViewWrapper as="span" class="resizable-image-wrapper" :class="{ 'is-selected': selected, 'is-resizing': isResizing }">
        <img 
            ref="imgRef"
            :src="imgSrc" 
            :alt="imgAlt" 
            :title="imgTitle"
            :width="displayWidth || undefined"
            draggable="false"
        />
        <template v-if="isEditable && (selected || isResizing)">
            <div class="resize-handle handle-tl" @mousedown="startResizeLeft"></div>
            <div class="resize-handle handle-tr" @mousedown="startResize"></div>
            <div class="resize-handle handle-bl" @mousedown="startResizeLeft"></div>
            <div class="resize-handle handle-br" @mousedown="startResize"></div>
        </template>
    </NodeViewWrapper>
</template>

<style scoped>
.resizable-image-wrapper {
    position: relative;
    display: inline-block;
    line-height: 0;
    vertical-align: bottom;
    margin-top: 0;
    margin-bottom: 0;
}

/* 关键修复：覆盖 prose 默认给 img 加的 margin */
.resizable-image-wrapper img {
    display: block;
    max-width: 100%;
    height: auto;
    border-radius: 4px;
    margin: 0 !important;
    padding: 0 !important;
    background: transparent;
    user-select: none;
    -webkit-user-select: none;
    -webkit-user-drag: none;
    backface-visibility: hidden;
    transform: translateZ(0);
    will-change: width;
    transition: box-shadow 0.15s ease;
}

.resizable-image-wrapper.is-selected img {
    box-shadow: 0 0 0 2px #3b82f6;
}

.resizable-image-wrapper.is-resizing img {
    box-shadow: 0 0 0 2px #3b82f6;
    opacity: 1;
}

.resize-handle {
    position: absolute;
    width: 8px;
    height: 8px;
    background: #3b82f6;
    border: 1.5px solid #fff;
    border-radius: 1px;
    z-index: 10;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.25);
}

.handle-tl { top: -4px; left: -4px; cursor: nwse-resize; }
.handle-tr { top: -4px; right: -4px; cursor: nesw-resize; }
.handle-bl { bottom: -4px; left: -4px; cursor: nesw-resize; }
.handle-br { bottom: -4px; right: -4px; cursor: nwse-resize; }

.resize-handle:hover {
    background: #2563eb;
    transform: scale(1.3);
}
</style>
