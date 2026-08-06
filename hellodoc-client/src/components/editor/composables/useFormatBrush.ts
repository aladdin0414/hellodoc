import { ref, onUnmounted, type Ref } from 'vue'
import { Editor } from '@tiptap/vue-3'

interface CapturedFormat {
    bold: boolean
    italic: boolean
    underline: boolean
    strike: boolean
    code: boolean
    subscript: boolean
    superscript: boolean
}

export function useFormatBrush(editor: Ref<Editor | undefined>) {
    const isFormatBrushActive = ref(false)
    const capturedFormat = ref<CapturedFormat | null>(null)
    let formatBrushCleanup: (() => void) | null = null

    const toggleFormatBrush = () => {
        if (isFormatBrushActive.value) {
            // 取消格式刷
            exitFormatBrush()
            return
        }

        if (!editor.value) return

        // 捕获当前光标处的格式
        capturedFormat.value = {
            bold: editor.value.isActive('bold'),
            italic: editor.value.isActive('italic'),
            underline: editor.value.isActive('underline'),
            strike: editor.value.isActive('strike'),
            code: editor.value.isActive('code'),
            subscript: editor.value.isActive('subscript'),
            superscript: editor.value.isActive('superscript'),
        }

        isFormatBrushActive.value = true

        // 使用 mouseup 事件等待用户完成选区后再应用格式
        const onMouseUp = () => {
            // 延迟一帧确保选区已更新
            requestAnimationFrame(() => {
                if (!isFormatBrushActive.value || !editor.value || !capturedFormat.value) return
                
                const { from, to } = editor.value.state.selection
                if (from === to) return // 无选区则等待下一次

                const fmt = capturedFormat.value
                let chain = editor.value.chain()

                // 先清除所有行内格式
                chain = chain.unsetBold().unsetItalic().unsetUnderline().unsetStrike().unsetCode().unsetSubscript().unsetSuperscript()

                // 再应用捕获的格式
                if (fmt.bold) chain = chain.setBold()
                if (fmt.italic) chain = chain.setItalic()
                if (fmt.underline) chain = chain.setUnderline()
                if (fmt.strike) chain = chain.setStrike()
                if (fmt.code) chain = chain.setCode()
                if (fmt.subscript) chain = chain.setSubscript()
                if (fmt.superscript) chain = chain.setSuperscript()

                chain.run()

                // 应用后退出格式刷模式
                exitFormatBrush()
            })
        }

        const editorDom = editor.value.view.dom
        editorDom.addEventListener('mouseup', onMouseUp)
        formatBrushCleanup = () => {
            editorDom.removeEventListener('mouseup', onMouseUp)
        }
    }

    const exitFormatBrush = () => {
        isFormatBrushActive.value = false
        capturedFormat.value = null
        formatBrushCleanup?.()
        formatBrushCleanup = null
    }

    onUnmounted(() => {
        exitFormatBrush()
    })

    return {
        isFormatBrushActive,
        toggleFormatBrush,
        exitFormatBrush
    }
}
