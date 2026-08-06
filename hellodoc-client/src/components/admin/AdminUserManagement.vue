<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { message } from '../../utils/message'
import { listUsers, createUser, updateUser, deleteUser, initPassword } from '../../api/user'
import BaseDialog from '../shared/BaseDialog.vue'

interface User {
  id?: number
  username: string
  nickname: string
  password?: string
  realName?: string
  email?: string
  phone?: string
  avatar?: string
  role: string
  status: number
  createTime?: string
}

const { t } = useI18n()

// Pagination and List state
const userList = ref<User[]>([])
const loading = ref(false)
const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

// Modal states
const showUserModal = ref(false)
const modalTitle = ref('')
const submitting = ref(false)
const userForm = reactive<User>({
  id: undefined,
  username: '',
  nickname: '',
  realName: '',
  email: '',
  phone: '',
  password: '',
  role: 'user',
  status: 0
} as any)

// Confirm Dialog states
const showConfirmModal = ref(false)
const confirmTitle = ref('')
const confirmMessage = ref('')
const confirmLoading = ref(false)
let onConfirmCallback: (() => Promise<void>) | null = null

const openConfirm = (title: string, msg: string, action: () => Promise<void>) => {
  confirmTitle.value = title
  confirmMessage.value = msg
  onConfirmCallback = action
  showConfirmModal.value = true
}

const handleConfirm = async () => {
  if (!onConfirmCallback) return
  confirmLoading.value = true
  try {
    await onConfirmCallback()
    showConfirmModal.value = false
  } catch (err) {
    console.error('Action failed:', err)
  } finally {
    confirmLoading.value = false
  }
}

const fetchUsers = async () => {
  loading.value = true
  try {
    const res: any = await listUsers({}, pagination.pageNum, pagination.pageSize)
    userList.value = res.content
    pagination.total = res.totalElements
  } catch (err) {
    console.error('Fetch users failed:', err)
  } finally {
    loading.value = false
  }
}

const openAddModal = () => {
  modalTitle.value = t('admin.user.addUser')
  Object.assign(userForm, {
    id: undefined,
    username: '',
    nickname: '',
    realName: '',
    email: '',
    phone: '',
    avatar: '',
    password: '',
    role: 'user',
    status: 0
  })
  showUserModal.value = true
}

const openEditModal = (user: User) => {
  modalTitle.value = t('admin.user.editUser')
  Object.assign(userForm, {
    id: user.id,
    username: user.username,
    nickname: user.nickname,
    realName: user.realName,
    email: user.email,
    phone: user.phone,
    avatar: user.avatar || '',
    password: '',
    role: user.role || 'user',
    status: user.status
  })
  showUserModal.value = true
}

const handleSubmit = async () => {
  submitting.value = true
  try {
    if (userForm.id) {
      await updateUser(userForm.id, userForm)
      message.success(t('admin.msg.updateSuccess'))
    } else {
      await createUser(userForm)
      message.success(t('admin.msg.createSuccess'))
    }
    showUserModal.value = false
    fetchUsers()
  } catch (err) {
    console.error('Submit user failed:', err)
  } finally {
    submitting.value = false
  }
}

const handleToggleStatus = async (user: User) => {
  if (!user.id) return
  try {
    const newStatus = user.status === 0 ? 1 : 0
    await updateUser(user.id, { ...user, status: newStatus })
    user.status = newStatus
    message.success(newStatus === 0 ? t('admin.msg.userEnabled') : t('admin.msg.userDisabled'))
  } catch (err) {
    console.error('Toggle status failed:', err)
  }
}

const handleInitPwd = async (id?: number) => {
  if (!id) return
  openConfirm(
    t('admin.confirm.resetPwdTitle'),
    t('admin.confirm.resetPwdMsg'),
    async () => {
      await initPassword(id)
      message.success(t('admin.msg.passwordResetDone'))
    }
  )
}

const handleDelete = async (id?: number) => {
  if (!id) return
  openConfirm(
    t('admin.confirm.deleteUserTitle'),
    t('admin.confirm.deleteUserMsg'),
    async () => {
      await deleteUser(id)
      message.success(t('admin.msg.userDeleted'))
      fetchUsers()
    }
  )
}

const formatDate = (dateStr?: string) => {
  if (!dateStr) return t('common.noData')
  return new Date(dateStr).toLocaleString()
}

onMounted(() => {
  fetchUsers()
})
</script>

<template>
  <div class="space-y-6">
    <div class="flex justify-between items-center">
      <div>
        <h2 class="text-2xl font-bold text-gray-900 dark:text-white">{{ t('admin.user.title') }}</h2>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">{{ t('admin.user.subtitle') }}</p>
      </div>
      <button @click="openAddModal"
        class="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg text-sm font-semibold transition-colors shadow-sm flex items-center space-x-2">
        <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
        </svg>
        <span>{{ t('admin.user.addUser') }}</span>
      </button>
    </div>

    <!-- User Table -->
    <div class="overflow-x-auto border border-gray-200 dark:border-gray-700 rounded-xl">
      <table class="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
        <thead class="bg-gray-50 dark:bg-gray-800/50 text-gray-500 dark:text-gray-400">
          <tr>
            <th class="px-6 py-4 text-left text-xs font-semibold uppercase tracking-wider">{{ t('admin.user.table.user') }}</th>
            <th class="px-6 py-4 text-left text-xs font-semibold uppercase tracking-wider">{{ t('admin.user.table.role') }}</th>
            <th class="px-6 py-4 text-left text-xs font-semibold uppercase tracking-wider">{{ t('admin.user.table.status') }}</th>
            <th class="px-6 py-4 text-left text-xs font-semibold uppercase tracking-wider">{{ t('admin.user.table.createdAt') }}</th>
            <th class="px-6 py-4 text-right text-xs font-semibold uppercase tracking-wider">{{ t('admin.user.table.action') }}</th>
          </tr>
        </thead>
        <tbody
          class="bg-white dark:bg-gray-800 divide-y divide-gray-100 dark:divide-gray-700 text-gray-700 dark:text-gray-300">
          <tr v-if="loading">
            <td colspan="5" class="px-6 py-12 text-center text-gray-500 dark:text-gray-400">{{ t('common.loading') }}</td>
          </tr>
          <tr v-else-if="userList.length === 0">
            <td colspan="5" class="px-6 py-12 text-center text-gray-500 dark:text-gray-400">{{ t('common.noData') }}</td>
          </tr>
          <tr v-for="user in userList" :key="user.id"
            class="hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors">
            <td class="px-6 py-4 whitespace-nowrap">
              <div class="flex items-center">
                <img :src="user.avatar || '/default-avatar.png'"
                  class="h-10 w-10 rounded-full bg-gray-100 object-cover mr-3" />
                <div>
                  <div class="text-sm font-bold text-gray-900 dark:text-white">{{ user.nickname }}</div>
                  <div class="text-xs text-gray-500 dark:text-gray-400">@{{ user.username }}</div>
                </div>
              </div>
            </td>
            <td class="px-6 py-4 whitespace-nowrap">
              <span
                :class="user.role === 'admin' ? 'bg-purple-50 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400' : 'bg-gray-50 text-gray-600 dark:bg-gray-700 dark:text-gray-400'"
                class="px-2 py-1 rounded-md text-xs font-medium">
                {{ user.role === 'admin' ? t('admin.user.roleAdmin') : t('admin.user.roleUser') }}
              </span>
            </td>
            <td class="px-6 py-4 whitespace-nowrap">
              <button @click="handleToggleStatus(user)"
                :class="user.status === 0 ? 'bg-green-50 text-green-700 dark:bg-green-900/30 dark:text-green-400' : 'bg-red-50 text-red-700 dark:bg-red-900/30 dark:text-red-400'"
                class="px-2 py-1 rounded-md text-xs font-medium flex items-center">
                <span class="w-1.5 h-1.5 rounded-full mr-1.5"
                  :class="user.status === 0 ? 'bg-green-500' : 'bg-red-500'"></span>
                {{ user.status === 0 ? t('admin.user.enabled') : t('admin.user.disabled') }}
              </button>
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm">{{ formatDate(user.createTime) }}</td>
            <td class="px-6 py-4 whitespace-nowrap text-right text-sm font-medium space-x-2">
              <button @click="openEditModal(user)"
                class="text-blue-600 dark:text-blue-400 hover:text-blue-700 transition-colors">{{ t('admin.user.editUser') }}</button>
              <button @click="handleInitPwd(user.id)"
                class="text-orange-600 dark:text-orange-400 hover:text-orange-700 transition-colors">{{ t('admin.user.resetPwd') }}</button>
              <button @click="handleDelete(user.id)"
                class="text-red-600 dark:text-red-400 hover:text-red-700 transition-colors">{{ t('admin.user.delete') }}</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Pagination -->
    <div class="flex items-center justify-between pt-4">
      <div class="text-sm text-gray-500 dark:text-gray-400">
        {{ t('admin.user.total', { total: pagination.total }) }}
      </div>
      <div class="flex space-x-2">
        <button :disabled="pagination.pageNum <= 1" @click="pagination.pageNum--; fetchUsers()"
          class="px-3 py-1 bg-white dark:bg-gray-700 border border-gray-300 dark:border-gray-600 rounded-md text-sm disabled:opacity-50 transition-colors">{{ t('admin.prev') }}</button>
        <button :disabled="pagination.pageNum * pagination.pageSize >= pagination.total"
          @click="pagination.pageNum++; fetchUsers()"
          class="px-3 py-1 bg-white dark:bg-gray-700 border border-gray-300 dark:border-gray-600 rounded-md text-sm disabled:opacity-50 transition-colors">{{ t('admin.next') }}</button>
      </div>
    </div>

    <!-- User Modal Overlay -->
    <BaseDialog :show="showUserModal" max-width-class="max-w-md" @close="showUserModal = false">
      <div
        class="px-6 py-4 border-b border-gray-100 dark:border-gray-700 flex justify-between items-center bg-gray-50 dark:bg-gray-800/50">
        <h3 class="text-lg font-bold text-gray-900 dark:text-white">{{ modalTitle }}</h3>
        <button @click="showUserModal = false" class="text-gray-400 hover:text-gray-600 transition-colors">
          <svg class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>
      <form @submit.prevent="handleSubmit" class="p-6 space-y-4">
        <div class="grid grid-cols-2 gap-4">
          <div class="col-span-2">
            <label class="block text-xs font-semibold text-gray-500 uppercase mb-1">{{ t('admin.user.form.username') }}</label>
            <input v-model="userForm.username" type="text" required :disabled="!!userForm.id" autocomplete="off"
              class="w-full px-4 py-2 bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 outline-none transition-all disabled:opacity-50" />
          </div>
          <div>
            <label class="block text-xs font-semibold text-gray-500 uppercase mb-1">{{ t('admin.user.form.nickname') }}</label>
            <input v-model="userForm.nickname" type="text" required
              class="w-full px-4 py-2 bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 outline-none transition-all" />
          </div>
          <div>
            <label class="block text-xs font-semibold text-gray-500 uppercase mb-1">{{ t('admin.user.form.realName') }}</label>
            <input v-model="userForm.realName" type="text"
              class="w-full px-4 py-2 bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 outline-none transition-all" />
          </div>
          <div class="col-span-2">
            <label class="block text-xs font-semibold text-gray-500 uppercase mb-1">{{ t('admin.user.form.email') }}</label>
            <input v-model="userForm.email" type="email"
              class="w-full px-4 py-2 bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 outline-none transition-all" />
          </div>
          <div v-if="!userForm.id" class="col-span-2">
            <label class="block text-xs font-semibold text-gray-500 uppercase mb-1">{{ t('admin.user.form.passwordHint') }}</label>
            <input v-model="userForm.password" type="password" autocomplete="new-password"
              class="w-full px-4 py-2 bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 outline-none transition-all" />
          </div>
          <div class="col-span-2">
            <label class="block text-xs font-semibold text-gray-500 uppercase mb-1">{{ t('admin.user.form.role') }}</label>
            <select v-model="userForm.role"
              class="w-full px-4 py-2 bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 outline-none transition-all">
              <option value="user">{{ t('admin.user.roleUser') }}</option>
              <option value="admin">{{ t('admin.user.roleAdmin') }}</option>
            </select>
          </div>
        </div>
        <div class="pt-4 flex space-x-3">
          <button type="button" @click="showUserModal = false"
            class="flex-1 px-5 py-2 border border-gray-200 dark:border-gray-700 rounded-xl text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition-all">{{ t('nav.cancel') }}</button>
          <button type="submit" :disabled="submitting"
            class="flex-1 px-5 py-2 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed text-white rounded-xl text-sm font-semibold transition-all active:scale-95">
            {{ submitting ? t('admin.submitting') : t('admin.submit') }}
          </button>
        </div>
      </form>
    </BaseDialog>

    <!-- Custom Confirm Modal -->
    <BaseDialog :show="showConfirmModal" z-index-class="z-[100]" max-width-class="max-w-sm"
      panel-class="animate-in fade-in zoom-in duration-200" :close-on-overlay="!confirmLoading"
      @close="showConfirmModal = false">
      <div class="p-6">
        <div class="flex items-center space-x-4 mb-4">
          <div
            class="w-12 h-12 rounded-full bg-orange-100 dark:bg-orange-900/30 flex-shrink-0 flex items-center justify-center">
            <svg class="h-6 w-6 text-orange-600 dark:text-orange-400" fill="none" viewBox="0 0 24 24"
              stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
          </div>
          <h3 class="text-lg font-bold text-gray-900 dark:text-white">{{ confirmTitle }}</h3>
        </div>
        <p class="text-sm text-gray-500 dark:text-gray-400">{{ confirmMessage }}</p>
      </div>
      <div
        class="px-6 py-4 bg-gray-50 dark:bg-gray-800/50 flex space-x-3 border-t border-gray-100 dark:border-gray-700">
        <button @click="showUserModal = false; showConfirmModal = false"
          class="flex-1 px-5 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-xl transition-all">{{ t('nav.cancel') }}</button>
        <button @click="handleConfirm" :disabled="confirmLoading"
          class="flex-1 px-5 py-2 bg-orange-600 hover:bg-orange-700 text-white text-sm font-semibold rounded-xl transition-all active:scale-95 disabled:opacity-50 disabled:cursor-not-allowed">
          {{ confirmLoading ? t('admin.processing') : t('admin.confirmText') }}
        </button>
      </div>
    </BaseDialog>
  </div>
</template>
