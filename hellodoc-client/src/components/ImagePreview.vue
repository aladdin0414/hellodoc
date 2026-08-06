<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
    show: boolean
    src: string
}>()

const emit = defineEmits(['close'])

const { t } = useI18n()

const scale = ref(1)
const translateX = ref(0)
const translateY = ref(0)
const isDragging = ref(false)
const startX = ref(0)
const startY = ref(0)
const isPinching = ref(false)
const pinchStartDistance = ref(0)
const pinchStartScale = ref(1)
const lastTouchX = ref(0)
const lastTouchY = ref(0)

const handleClose = () => {
    emit('close')
    resetView()
}

const resetView = () => {
    scale.value = 1
    translateX.value = 0
    translateY.value = 0
}

// 禁止滚动
const lockScroll = () => {
    document.body.style.overflow = 'hidden'
}

const unlockScroll = () => {
    document.body.style.overflow = ''
}

// 快捷键关闭
const handleKeyDown = (e: KeyboardEvent) => {
    if (e.key === 'Escape') {
        handleClose()
    }
}

// 缩放逻辑
const handleWheel = (e: WheelEvent) => {
    e.preventDefault()
    const delta = e.deltaY > 0 ? -0.1 : 0.1
    const newScale = Math.min(Math.max(0.2, scale.value + delta), 5)
    scale.value = newScale
}

// 拖拽逻辑
const onMouseDown = (e: MouseEvent) => {
    if (scale.value <= 1) return
    isDragging.value = true
    startX.value = e.clientX - translateX.value
    startY.value = e.clientY - translateY.value
}

const onMouseMove = (e: MouseEvent) => {
    if (!isDragging.value) return
    translateX.value = e.clientX - startX.value
    translateY.value = e.clientY - startY.value
}

const onMouseUp = () => {
    isDragging.value = false
}

const handleDoubleClick = () => {
    if (scale.value !== 1) {
        resetView()
    } else {
        scale.value = 2
    }
}

const getTouchDistance = (t1: Touch, t2: Touch) => {
    const dx = t1.clientX - t2.clientX
    const dy = t1.clientY - t2.clientY
    return Math.hypot(dx, dy)
}

const onTouchStart = (e: TouchEvent) => {
    if (e.touches.length === 2) {
        const t1 = e.touches.item(0)
        const t2 = e.touches.item(1)
        if (!t1 || !t2) return
        isPinching.value = true
        isDragging.value = false
        pinchStartDistance.value = getTouchDistance(t1, t2)
        pinchStartScale.value = scale.value
        return
    }

    if (e.touches.length === 1 && scale.value > 1) {
        const touch = e.touches.item(0)
        if (!touch) return
        isDragging.value = true
        isPinching.value = false
        lastTouchX.value = touch.clientX
        lastTouchY.value = touch.clientY
    }
}

const onTouchMove = (e: TouchEvent) => {
    if (isPinching.value && e.touches.length === 2) {
        const t1 = e.touches.item(0)
        const t2 = e.touches.item(1)
        if (!t1 || !t2) return
        e.preventDefault()
        const distance = getTouchDistance(t1, t2)
        if (!pinchStartDistance.value) return
        const ratio = distance / pinchStartDistance.value
        const newScale = Math.min(Math.max(1, pinchStartScale.value * ratio), 5)
        scale.value = newScale
        if (scale.value === 1) {
            translateX.value = 0
            translateY.value = 0
        }
        return
    }

    if (isDragging.value && e.touches.length === 1 && scale.value > 1) {
        const touch = e.touches.item(0)
        if (!touch) return
        e.preventDefault()
        const dx = touch.clientX - lastTouchX.value
        const dy = touch.clientY - lastTouchY.value
        translateX.value += dx
        translateY.value += dy
        lastTouchX.value = touch.clientX
        lastTouchY.value = touch.clientY
    }
}

const onTouchEnd = (e: TouchEvent) => {
    if (e.touches.length < 2) {
        isPinching.value = false
        pinchStartDistance.value = 0
    }
    if (e.touches.length === 0) {
        isDragging.value = false
    }
}

watch(() => props.show, (newVal) => {
    if (newVal) {
        lockScroll()
        resetView()
    } else {
        unlockScroll()
    }
})

onMounted(() => {
    if (props.show) lockScroll()
    window.addEventListener('keydown', handleKeyDown)
    window.addEventListener('mouseup', onMouseUp)
})

onUnmounted(() => {
    unlockScroll()
    window.removeEventListener('keydown', handleKeyDown)
    window.removeEventListener('mouseup', onMouseUp)
})
</script>

<template>
    <Transition name="fade">
        <div v-if="show" class="fixed inset-0 z-[999] flex items-center justify-center bg-black/80 backdrop-blur-md overflow-hidden"
            @click="handleClose" @wheel="handleWheel">
            
            <!-- Tooltip -->
            <div class="absolute bottom-8 left-1/2 -translate-x-1/2 px-4 py-2 bg-white/10 backdrop-blur-md rounded-full text-white/60 text-xs font-medium pointer-events-none z-50 transition-opacity"
                 :class="scale > 1 ? 'opacity-100' : 'opacity-0'">
                {{ t('imagePreview.tooltip') }}
            </div>

            <!-- Close Button -->
            <button
                class="absolute top-6 right-6 p-2 rounded-full bg-white/10 hover:bg-white/20 text-white transition-all z-[1000]"
                @click.stop="handleClose">
                <svg xmlns="http://www.w3.org/2000/svg" class="w-6 h-6" fill="none" viewBox="0 0 24 24"
                    stroke="currentColor" stroke-width="2">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
                </svg>
            </button>

            <!-- Image Container -->
            <div class="relative w-full h-full flex items-center justify-center pointer-events-none" @click.stop>
                <div class="transition-transform duration-200 ease-out pointer-events-auto cursor-zoom-in touch-none"
                     :class="{ 'cursor-grabbing': isDragging, 'cursor-zoom-out': scale > 1 && !isDragging }"
                     :style="{ transform: `translate(${translateX}px, ${translateY}px) scale(${scale})` }"
                     @mousedown="onMouseDown"
                     @mousemove="onMouseMove"
                     @touchstart="onTouchStart"
                     @touchmove="onTouchMove"
                     @touchend="onTouchEnd"
                     @touchcancel="onTouchEnd"
                     @dblclick="handleDoubleClick">
                    <img :src="src" 
                         class="max-w-[90vw] max-h-[90vh] object-contain rounded-lg shadow-2xl select-none"
                         :alt="t('imagePreview.alt')" />
                </div>
            </div>
        </div>
    </Transition>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
    transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
    opacity: 0;
}

.cursor-zoom-in {
    cursor: zoom-in;
}

.cursor-zoom-out {
    cursor: zoom-out;
}

.cursor-grabbing {
    cursor: grabbing !important;
}

.touch-none {
    touch-action: none;
}
</style>
