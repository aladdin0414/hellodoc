<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { message } from '../../utils/message'
import { getComments, addComment, replyComment, deleteComment, resolveComment, unresolveComment, type DocComment } from '../../api/comment'

const props = defineProps<{
    docId?: number
    currentUserId: number | null
    currentUser?: any
    isGuestbookEnabled: boolean
    isLoggedIn: boolean
    canEdit: boolean
}>()

const { t } = useI18n()

const comments = ref<DocComment[]>([])
const newComment = ref('')
const replyingTo = ref<number | null>(null)
const replyContent = ref('')
const commentLoading = ref(false)

const fetchComments = async () => {
    if (!props.docId) return
    try {
        const res: any = await getComments(props.docId)
        comments.value = res
    } catch (e) {
        console.error('Fetch comments failed:', e)
    }
}

watch(() => props.docId, (newDocId) => {
    if (newDocId) {
        fetchComments()
    } else {
        comments.value = []
    }
}, { immediate: true })

const handleAddComment = async () => {
    if (!newComment.value.trim() || !props.docId) return
    commentLoading.value = true
    try {
        await addComment(props.docId, { content: newComment.value })
        newComment.value = ''
        await fetchComments()
        message.success(t('kbView.comments.published'))
    } catch (e) {
        console.error('Add comment failed:', e)
        message.error(t('kbView.comments.publishFailed'))
    } finally {
        commentLoading.value = false
    }
}

const handleReply = async (parentId: number) => {
    if (!replyContent.value.trim()) return
    commentLoading.value = true
    try {
        await replyComment(parentId, replyContent.value)
        replyContent.value = ''
        replyingTo.value = null
        await fetchComments()
        message.success(t('kbView.comments.replyPublished'))
    } catch (e) {
        console.error('Reply failed:', e)
        message.error(t('kbView.comments.replyFailed'))
    } finally {
        commentLoading.value = false
    }
}

const handleToggleResolve = async (comment: DocComment) => {
    try {
        if (comment.isResolved) {
            await unresolveComment(comment.id)
            message.success(t('kbView.comments.resolveCanceled'))
        } else {
            await resolveComment(comment.id)
            message.success(t('kbView.comments.markResolved'))
        }
        await fetchComments()
    } catch (e) {
        console.error('Toggle resolve failed:', e)
        message.error(t('kbView.comments.actionFailed'))
    }
}

const handleDeleteComment = async (id: number) => {
    try {
        await deleteComment(id)
        message.success(t('kbView.comments.deleted'))
        await fetchComments()
    } catch (e) {
        console.error('Delete failed:', e)
        message.error(t('kbView.comments.deleteFailed'))
    }
}

const rootComments = computed(() => {
    return comments.value.filter(c => !c.parentId).sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
})

const getReplies = (parentId: number) => {
    return comments.value.filter(c => c.parentId === parentId).sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime())
}

const formatDate = (dateStr?: string) => {
    if (!dateStr) return ''
    try {
        const d = new Date(dateStr)
        return d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    } catch {
        return dateStr
    }
}
</script>

<template>
    <div v-if="isGuestbookEnabled"
        class="mt-12 border-t border-slate-100 dark:border-gray-700 pt-12 max-w-4xl mx-auto w-full px-4 md:px-8 pb-20">
        <div class="flex items-center justify-between mb-8">
            <h3 class="text-xl font-black text-slate-900 dark:text-gray-100 tracking-tight flex items-center gap-2">
                <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5 text-indigo-500" fill="none"
                    viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
                    <path stroke-linecap="round" stroke-linejoin="round"
                        d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
                </svg>
                {{ t('kbView.comments.title') }}
                <span v-if="comments.length > 0"
                    class="ml-2 px-2 py-0.5 bg-slate-100 text-slate-500 text-xs font-bold rounded-full">{{
                        comments.length }}</span>
            </h3>
        </div>

        <!-- New Comment Input -->
        <div v-if="isLoggedIn"
            class="p-6 bg-[#f8fafc] dark:bg-[#161b22]/50 rounded-3xl border border-slate-200/50 dark:border-gray-800 shadow-sm mb-12">
            <div class="flex gap-4">
                <img :src="currentUser?.avatar || 'https://api.dicebear.com/7.x/avataaars/svg?seed=' + currentUserId"
                    class="w-10 h-10 rounded-xl bg-slate-100 object-cover flex-shrink-0">
                <div class="flex-1 space-y-3">
                    <textarea v-model="newComment" :placeholder="t('kbView.comments.placeholder')" rows="3"
                        class="w-full bg-slate-50 dark:bg-gray-700 border border-slate-100 dark:border-gray-600 rounded-xl p-3 text-sm text-slate-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:bg-white dark:focus:bg-gray-600 focus:border-indigo-500/30 transition-all resize-none"></textarea>
                    <div class="flex justify-end">
                        <button @click="handleAddComment"
                            :disabled="!newComment.trim() || commentLoading"
                            class="px-5 py-2 bg-indigo-600 text-white text-xs font-black rounded-xl hover:bg-indigo-700 transition-all disabled:opacity-50 active:scale-95 shadow-lg shadow-indigo-100 dark:shadow-none">
                            {{ commentLoading ? t('kbView.comments.publishing') : t('kbView.comments.publishBtn') }}
                        </button>
                    </div>
                </div>
            </div>
        </div>
        <div v-else
            class="bg-slate-50 dark:bg-[#161b22] border border-slate-100 dark:border-gray-700 rounded-2xl p-8 text-center mb-12">
            <p class="text-slate-500 text-sm font-medium">
                {{ t('kbView.comments.loginRequiredPrefix') }}<router-link :to="`/login?redirect=${encodeURIComponent($route.fullPath)}`"
                    class="text-indigo-600 font-extrabold hover:text-indigo-700 hover:underline mx-1 transition-all">{{ t('kbView.comments.loginRequiredLink') }}</router-link>{{ t('kbView.comments.loginRequiredSuffix') }}
            </p>
        </div>

        <!-- Comments List -->
        <div class="space-y-8">
            <div v-for="comment in rootComments" :key="comment.id"
                class="group border-b border-slate-50 pb-8 last:border-0">
                <div class="flex gap-4">
                    <img :src="comment.user?.avatar || 'https://api.dicebear.com/7.x/avataaars/svg?seed=' + comment.userId"
                        class="w-10 h-10 rounded-xl bg-slate-100 object-cover flex-shrink-0">
                    <div class="flex-1 space-y-2">
                        <div class="flex items-center justify-between">
                            <div class="flex items-center gap-2">
                                <span class="text-sm font-bold text-slate-800 dark:text-gray-200">{{
                                    comment.user?.nickname
                                    || t('kbView.comments.anonymous') }}</span>
                                <span class="text-[10px] text-slate-400 font-medium">{{
                                    formatDate(comment.createdAt) }}</span>
                                <span v-if="comment.isResolved"
                                    class="inline-flex items-center gap-1 px-2 py-0.5 bg-emerald-50 text-emerald-600 text-[10px] font-bold rounded-full">
                                    <svg xmlns="http://www.w3.org/2000/svg" class="w-3 h-3" fill="none"
                                        viewBox="0 0 24 24" stroke="currentColor" stroke-width="3">
                                        <path stroke-linecap="round" stroke-linejoin="round"
                                            d="M5 13l4 4L19 7" />
                                    </svg>
                                    {{ t('kbView.comments.resolved') }}
                                </span>
                            </div>

                            <div
                                class="opacity-0 group-hover:opacity-100 transition-opacity flex items-center gap-2">
                                <button v-if="isLoggedIn" @click="replyingTo = comment.id"
                                    class="text-[10px] font-bold text-slate-400 hover:text-indigo-600 transition-colors">{{ t('kbView.comments.reply') }}</button>
                                <button v-if="canEdit" @click="handleToggleResolve(comment)"
                                    class="text-[10px] font-bold text-slate-400 hover:text-emerald-600 transition-colors">
                                    {{ comment.isResolved ? t('kbView.comments.cancelResolve') : t('kbView.comments.markResolved') }}
                                </button>
                                <button v-if="comment.userId === currentUserId || canEdit"
                                    @click="handleDeleteComment(comment.id)"
                                    class="text-[10px] font-bold text-slate-400 hover:text-rose-500 transition-colors">{{ t('kbView.comments.delete') }}</button>
                            </div>
                        </div>
                        <p class="text-sm text-slate-600 dark:text-gray-300 leading-relaxed">{{
                            comment.content }}
                        </p>

                        <!-- Reply Input -->
                        <div v-if="replyingTo === comment.id"
                            class="mt-4 bg-slate-50 dark:bg-[#161b22]/50 rounded-xl p-3 border border-slate-100 dark:border-gray-700">
                            <textarea v-model="replyContent" :placeholder="t('kbView.comments.replyPlaceholder')" rows="2"
                                class="w-full bg-white dark:bg-gray-700 border border-slate-100 dark:border-gray-600 rounded-lg p-2 text-sm text-slate-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-indigo-500/10 transition-all resize-none"></textarea>
                            <div class="flex justify-end gap-2 mt-2">
                                <button @click="replyingTo = null"
                                    class="px-3 py-1 text-[10px] font-bold text-slate-500 hover:text-slate-700">{{ t('nav.cancel') }}</button>
                                <button @click="handleReply(comment.id)"
                                    :disabled="!replyContent.trim() || commentLoading"
                                    class="px-4 py-1.5 bg-indigo-600 text-white text-[10px] font-bold rounded-lg hover:bg-indigo-700 transition-all shadow-sm dark:shadow-none">
                                    {{ t('kbView.comments.submitReply') }}
                                </button>
                            </div>
                        </div>

                        <!-- Replies -->
                        <div v-if="getReplies(comment.id).length > 0"
                            class="mt-4 space-y-4 pl-4 border-l-2 border-slate-50 dark:border-gray-700">
                            <div v-for="reply in getReplies(comment.id)" :key="reply.id"
                                class="group/reply">
                                <div class="flex gap-3">
                                    <img :src="reply.user?.avatar || 'https://api.dicebear.com/7.x/avataaars/svg?seed=' + reply.userId"
                                        class="w-7 h-7 rounded-lg bg-slate-100 object-cover flex-shrink-0">
                                    <div class="flex-1 space-y-1">
                                        <div class="flex items-center justify-between">
                                            <div class="flex items-center gap-2">
                                                <span
                                                    class="text-xs font-bold text-slate-700 dark:text-gray-300">{{
                                                        reply.user?.nickname || t('kbView.comments.anonymous') }}</span>
                                                <span class="text-[9px] text-slate-400 font-medium">{{
                                                    formatDate(reply.createdAt) }}</span>
                                            </div>
                                            <div
                                                class="opacity-0 group-hover/reply:opacity-100 transition-opacity flex items-center gap-2">
                                                <button v-if="reply.userId === currentUserId || canEdit"
                                                    @click="handleDeleteComment(reply.id)"
                                                    class="text-[9px] font-bold text-slate-300 hover:text-rose-500 transition-colors">{{ t('kbView.comments.delete') }}</button>
                                            </div>
                                        </div>
                                        <p
                                            class="text-xs text-slate-500 dark:text-gray-400 leading-relaxed">
                                            {{
                                                reply.content }}</p>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <div v-if="comments.length === 0" class="py-12 text-center">
            <div
                class="w-16 h-16 bg-slate-50 dark:bg-[#161b22] rounded-2xl flex items-center justify-center mx-auto mb-4 border border-slate-100/50 dark:border-gray-700">
                <svg xmlns="http://www.w3.org/2000/svg" class="w-8 h-8 text-slate-200" fill="none"
                    viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
                    <path stroke-linecap="round" stroke-linejoin="round"
                        d="M7 8h10M7 12h4m1 8l-4-4H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-3l-4 4z" />
                </svg>
            </div>
            <p class="text-slate-400 text-sm">{{ t('kbView.comments.noCommentsText') }}</p>
        </div>
    </div>
</template>
