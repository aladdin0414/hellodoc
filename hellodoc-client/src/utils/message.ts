import { ref } from 'vue'

export type MessageType = 'success' | 'error' | 'info' | 'warning'

export interface MessageItem {
    id: number
    type: MessageType
    content: string
}

const messages = ref<MessageItem[]>([])
let msgId = 0

export const message = {
    success(content: string) {
        this.add('success', content)
    },
    error(content: string) {
        this.add('error', content)
    },
    info(content: string) {
        this.add('info', content)
    },
    warning(content: string) {
        this.add('warning', content)
    },
    add(type: MessageType, content: string) {
        const id = ++msgId
        messages.value.push({ id, type, content })
        setTimeout(() => {
            this.remove(id)
        }, 3000)
    },
    remove(id: number) {
        const index = messages.value.findIndex(m => m.id === id)
        if (index > -1) {
            messages.value.splice(index, 1)
        }
    }
}

export { messages }
