<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { message } from '../../utils/message'
import BaseDialog from '../shared/BaseDialog.vue'
import {
  listAllAiModels,
  createAiModel,
  updateAiModel,
  deleteAiModel,
  setDefaultAiModel,
  toggleActiveAiModel,
  testAiModelConnection,
  type AiModelItem,
  type AiModelCreateData,
  type AiModelUpdateData
} from '../../api/aiModel'

const { t } = useI18n()

// 数据与加载状态
const models = ref<AiModelItem[]>([])
const loading = ref(false)
const submitting = ref(false)

// 弹窗状态
const showModal = ref(false)
const isEditing = ref(false)
const editingId = ref<string | null>(null)
const showApiKey = ref(false)

// 删除确认
const showDeleteConfirm = ref(false)
const deletingModel = ref<AiModelItem | null>(null)

// 连通性测试状态
const testing = ref(false)
const testResult = ref<{ success: boolean; latencyMs?: number; message?: string } | null>(null)

// 表单对象
const form = reactive({
  name: '',
  provider: 'custom',
  baseUrl: '',
  apiKey: '',
  modelName: '',
  temperature: 0.7,
  agentPrompt: '',
  isDefault: false,
  isEnabled: true,
  disableThinking: true
})

// 常见提供商快捷选项
const providers = [
  { label: 'DeepSeek', value: 'deepseek', defaultUrl: 'https://api.deepseek.com/v1', defaultModel: 'deepseek-chat' },
  { label: 'OpenAI', value: 'openai', defaultUrl: 'https://api.openai.com/v1', defaultModel: 'gpt-4o' },
  { label: '阿里通义千问', value: 'qwen', defaultUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', defaultModel: 'qwen-plus' },
  { label: '月之暗面 (Kimi)', value: 'moonshot', defaultUrl: 'https://api.moonshot.cn/v1', defaultModel: 'moonshot-v1-8k' },
  { label: '自定义 / 兼容代理', value: 'custom', defaultUrl: '', defaultModel: '' }
]

// 统计数据
const totalCount = computed(() => models.value.length)
const activeCount = computed(() => models.value.filter(m => m.isEnabled).length)

// 获取模型列表
const fetchModels = async () => {
  loading.value = true
  try {
    const res: any = await listAllAiModels()
    models.value = res || []
  } catch (err: any) {
    message.error(err?.message || '获取 AI 模型列表失败')
  } finally {
    loading.value = false
  }
}

// 打开新增弹窗
const openCreateModal = () => {
  isEditing.value = false
  editingId.value = null
  showApiKey.value = false
  testResult.value = null

  form.name = ''
  form.provider = 'deepseek'
  form.baseUrl = 'https://api.deepseek.com/v1'
  form.apiKey = ''
  form.modelName = 'deepseek-chat'
  form.temperature = 0.70
  form.agentPrompt = ''
  form.isDefault = models.value.length === 0
  form.isEnabled = true
  form.disableThinking = true

  showModal.value = true
}

// 打开编辑弹窗
const openEditModal = (model: AiModelItem) => {
  isEditing.value = true
  editingId.value = model.id
  showApiKey.value = false
  testResult.value = null

  form.name = model.name
  form.provider = model.provider || 'custom'
  form.baseUrl = model.baseUrl || ''
  form.apiKey = '' // 留空表示不修改原 Key
  form.modelName = model.modelName
  form.temperature = model.temperature ?? 0.70
  form.agentPrompt = model.agentPrompt || ''
  form.isDefault = model.isDefault
  form.isEnabled = model.isEnabled
  form.disableThinking = model.disableThinking ?? true

  showModal.value = true
}

// 快速切换 Provider
const handleProviderChange = (e: Event) => {
  const target = e.target as HTMLSelectElement
  const p = providers.find(item => item.value === target.value)
  if (p && !isEditing.value) {
    if (p.defaultUrl) form.baseUrl = p.defaultUrl
    if (p.defaultModel) form.modelName = p.defaultModel
  }
}

// 提交表单（保存）
const handleSubmit = async () => {
  if (!form.name.trim()) {
    return message.error(t('admin.aiModel.form.name') + ' 不能为空')
  }
  if (!form.baseUrl.trim()) {
    return message.error(t('admin.aiModel.form.baseUrl') + ' 不能为空')
  }
  if (!isEditing.value && !form.apiKey.trim()) {
    return message.error(t('admin.aiModel.form.apiKey') + ' 不能为空')
  }
  if (!form.modelName.trim()) {
    return message.error(t('admin.aiModel.form.modelName') + ' 不能为空')
  }

  submitting.value = true
  try {
    if (isEditing.value && editingId.value) {
      const updateData: AiModelUpdateData = {
        name: form.name.trim(),
        provider: form.provider,
        baseUrl: form.baseUrl.trim(),
        modelName: form.modelName.trim(),
        temperature: form.temperature,
        agentPrompt: form.agentPrompt,
        isDefault: form.isDefault,
        isEnabled: form.isEnabled,
        disableThinking: form.disableThinking
      }
      if (form.apiKey.trim()) {
        updateData.apiKey = form.apiKey.trim()
      }
      await updateAiModel(editingId.value, updateData)
      message.success('AI 模型配置已更新')
    } else {
      const createData: AiModelCreateData = {
        name: form.name.trim(),
        provider: form.provider,
        baseUrl: form.baseUrl.trim(),
        apiKey: form.apiKey.trim(),
        modelName: form.modelName.trim(),
        temperature: form.temperature,
        agentPrompt: form.agentPrompt,
        isDefault: form.isDefault,
        isEnabled: form.isEnabled,
        disableThinking: form.disableThinking
      }
      await createAiModel(createData)
      message.success('AI 模型已创建')
    }
    showModal.value = false
    await fetchModels()
  } catch (err: any) {
    message.error(err?.message || '保存模型失败')
  } finally {
    submitting.value = false
  }
}

// 连通性测试
const handleTestConnection = async () => {
  if (!form.baseUrl.trim() || !form.modelName.trim()) {
    return message.error('请先填写 Base URL 和模型标识')
  }
  if (!isEditing.value && !form.apiKey.trim()) {
    return message.error('请先填写 API Key')
  }

  testing.value = true
  testResult.value = null
  try {
    const res = await testAiModelConnection({
      id: isEditing.value && editingId.value ? editingId.value : undefined,
      baseUrl: form.baseUrl.trim(),
      apiKey: form.apiKey.trim() || undefined,
      modelName: form.modelName.trim()
    })
    testResult.value = {
      success: true,
      latencyMs: res.latencyMs,
      message: res.reply ? `模型回复: "${res.reply}"` : '连接正常'
    }
    message.success(t('admin.aiModel.testSuccess', { ms: res.latencyMs }))
  } catch (err: any) {
    testResult.value = {
      success: false,
      message: err?.message || '测试失败'
    }
    message.error(t('admin.aiModel.testFailed', { msg: err?.message || '连接失败' }))
  } finally {
    testing.value = false
  }
}

// 卡片上单次连通测试
const handleCardTest = async (model: AiModelItem) => {
  try {
    message.info('正在测试连通性...')
    const res = await testAiModelConnection({ id: model.id })
    message.success(`${model.name} 连接成功！时延 ${res.latencyMs}ms`)
  } catch (err: any) {
    message.error(`${model.name} 测试失败: ${err?.message || '网络或认证异常'}`)
  }
}

// 一键设为默认
const handleSetDefault = async (model: AiModelItem) => {
  if (model.isDefault) return
  try {
    await setDefaultAiModel(model.id)
    message.success(`已将 ${model.name} 设为系统默认模型`)
    await fetchModels()
  } catch (err: any) {
    message.error(err?.message || '设为默认模型失败')
  }
}

// 一键切换激活状态
const handleToggleActive = async (model: AiModelItem) => {
  try {
    await toggleActiveAiModel(model.id)
    message.success(`${model.name} 已${model.isEnabled ? '停用' : '激活'}`)
    await fetchModels()
  } catch (err: any) {
    message.error(err?.message || '操作失败')
  }
}

// 打开删除确认
const confirmDelete = (model: AiModelItem) => {
  deletingModel.value = model
  showDeleteConfirm.value = true
}

// 执行删除
const handleDelete = async () => {
  if (!deletingModel.value) return
  try {
    await deleteAiModel(deletingModel.value.id)
    message.success('模型已删除')
    showDeleteConfirm.value = false
    await fetchModels()
  } catch (err: any) {
    message.error(err?.message || '删除失败')
  }
}

onMounted(() => {
  fetchModels()
})
</script>

<template>
  <div class="space-y-6">
    <!-- 头部区域 -->
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
      <div>
        <h2 class="text-xl font-bold text-gray-900 dark:text-white flex items-center gap-2">
          <svg xmlns="http://www.w3.org/2000/svg" class="w-6 h-6 text-blue-600 dark:text-blue-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect width="18" height="18" x="3" y="3" rx="2"/><path d="M9 3v18"/><path d="m14 9 3 3-3 3"/>
          </svg>
          {{ t('admin.aiModel.title') }}
        </h2>
        <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
          {{ t('admin.aiModel.subtitle') }}
          <span class="ml-2 inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-50 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300">
            {{ t('admin.aiModel.total', { total: totalCount, active: activeCount }) }}
          </span>
        </p>
      </div>
      <button
        @click="openCreateModal"
        class="inline-flex items-center justify-center px-4 py-2.5 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-600 rounded-xl shadow-sm transition-all focus:outline-none focus:ring-2 focus:ring-blue-500/20"
      >
        <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4 mr-2" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M5 12h14"/><path d="M12 5v14"/>
        </svg>
        {{ t('admin.aiModel.addModel') }}
      </button>
    </div>

    <!-- 加载中骨架屏 -->
    <div v-if="loading" class="grid grid-cols-1 md:grid-cols-2 gap-4">
      <div v-for="i in 4" :key="i" class="h-44 bg-gray-100 dark:bg-gray-800 rounded-2xl animate-pulse"></div>
    </div>

    <!-- 空状态 -->
    <div v-else-if="models.length === 0" class="text-center py-20 bg-gray-50 dark:bg-gray-900/40 rounded-2xl border border-dashed border-gray-200 dark:border-gray-800">
      <div class="inline-flex items-center justify-center w-16 h-16 rounded-full bg-blue-50 dark:bg-blue-900/20 text-blue-600 dark:text-blue-400 mb-4">
        <svg xmlns="http://www.w3.org/2000/svg" class="w-8 h-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
        </svg>
      </div>
      <h3 class="text-base font-semibold text-gray-900 dark:text-white mb-1">{{ t('admin.aiModel.empty') }}</h3>
      <p class="text-sm text-gray-500 dark:text-gray-400 max-w-sm mx-auto mb-4">
        配置您的大模型 API 密钥与地址，即可开启前台 AI 智能写作与问答服务。
      </p>
      <button
        @click="openCreateModal"
        class="inline-flex items-center px-4 py-2 text-sm font-medium text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-900/20 hover:bg-blue-100 dark:hover:bg-blue-900/40 rounded-xl transition-colors"
      >
        + {{ t('admin.aiModel.addModel') }}
      </button>
    </div>

    <!-- 模型卡片网格看板 -->
    <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-5">
      <div
        v-for="model in models"
        :key="model.id"
        class="group relative bg-white dark:bg-gray-800 rounded-2xl p-5 border transition-all duration-200 hover:shadow-md"
        :class="[
          model.isDefault 
            ? 'border-amber-300 dark:border-amber-500/50 shadow-sm ring-1 ring-amber-300/40 dark:ring-amber-500/30' 
            : 'border-gray-200 dark:border-gray-700 hover:border-blue-200 dark:hover:border-gray-600'
        ]"
      >
        <!-- 卡片头部 -->
        <div class="flex items-start justify-between gap-3 mb-3">
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2 flex-wrap">
              <h3 class="text-base font-bold text-gray-900 dark:text-white truncate">
                {{ model.name }}
              </h3>
              <!-- 默认模型金色徽章 -->
              <span
                v-if="model.isDefault"
                class="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-xs font-semibold bg-amber-50 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300 border border-amber-200 dark:border-amber-800"
              >
                <svg xmlns="http://www.w3.org/2000/svg" class="w-3.5 h-3.5 fill-amber-500 text-amber-500" viewBox="0 0 24 24">
                  <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/>
                </svg>
                {{ t('admin.aiModel.defaultBadge') }}
              </span>
              <!-- 激活状态徽章 -->
              <span
                class="inline-flex items-center px-2 py-0.5 rounded-md text-xs font-medium"
                :class="[
                  model.isEnabled
                    ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300'
                    : 'bg-gray-100 text-gray-500 dark:bg-gray-700 dark:text-gray-400'
                ]"
              >
                <span class="w-1.5 h-1.5 rounded-full mr-1.5" :class="model.isEnabled ? 'bg-emerald-500' : 'bg-gray-400'"></span>
                {{ model.isEnabled ? t('admin.aiModel.statusActive') : t('admin.aiModel.statusInactive') }}
              </span>
            </div>
            <p class="text-xs font-mono text-gray-500 dark:text-gray-400 mt-1 truncate">
              {{ model.modelName }}
            </p>
          </div>

          <!-- 激活 Switch -->
          <button
            @click="handleToggleActive(model)"
            :title="model.isEnabled ? '点击停用' : '点击激活'"
            class="relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none"
            :class="model.isEnabled ? 'bg-blue-600 dark:bg-blue-500' : 'bg-gray-200 dark:bg-gray-700'"
          >
            <span
              class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out"
              :class="model.isEnabled ? 'translate-x-5' : 'translate-x-0'"
            />
          </button>
        </div>

        <!-- 卡片元信息 -->
        <div class="grid grid-cols-2 gap-2 text-xs text-gray-500 dark:text-gray-400 py-2 border-y border-gray-100 dark:border-gray-700/60 my-3">
          <div>
            <span class="text-gray-400">端点:</span>
            <span class="ml-1 font-mono text-gray-700 dark:text-gray-300 truncate inline-block max-w-[140px] align-bottom" :title="model.baseUrl">
              {{ model.baseUrl?.replace(/^https?:\/\//, '') }}
            </span>
          </div>
          <div>
            <span class="text-gray-400">温度:</span>
            <span class="ml-1 font-medium text-gray-700 dark:text-gray-300">{{ model.temperature ?? 0.7 }}</span>
          </div>
          <div>
            <span class="text-gray-400">服务商:</span>
            <span class="ml-1 uppercase text-gray-700 dark:text-gray-300">{{ model.provider || 'CUSTOM' }}</span>
          </div>
          <div>
            <span class="text-gray-400">思考过滤:</span>
            <span class="ml-1 font-medium" :class="model.disableThinking !== false ? 'text-emerald-600 dark:text-emerald-400' : 'text-gray-400'">
              {{ model.disableThinking !== false ? '开启' : '关闭' }}
            </span>
          </div>
        </div>

        <!-- 底部快捷工具栏 -->
        <div class="flex items-center justify-between pt-1">
          <div class="flex items-center gap-1.5">
            <!-- 设为默认按钮 -->
            <button
              v-if="!model.isDefault"
              @click="handleSetDefault(model)"
              class="inline-flex items-center px-2.5 py-1 text-xs font-medium text-amber-700 dark:text-amber-300 bg-amber-50 hover:bg-amber-100 dark:bg-amber-900/20 dark:hover:bg-amber-900/40 rounded-lg transition-colors"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="w-3 h-3 mr-1 text-amber-600 dark:text-amber-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
              </svg>
              {{ t('admin.aiModel.setDefault') }}
            </button>
            <!-- 测试连接按钮 -->
            <button
              @click="handleCardTest(model)"
              class="inline-flex items-center px-2.5 py-1 text-xs font-medium text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700/60 rounded-lg transition-colors"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="w-3 h-3 mr-1" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M22 12h-4l-3 9L9 3l-3 9H2"/>
              </svg>
              {{ t('admin.aiModel.testConnection') }}
            </button>
          </div>

          <div class="flex items-center gap-1">
            <!-- 编辑按钮 -->
            <button
              @click="openEditModal(model)"
              class="p-1.5 text-gray-500 hover:text-blue-600 dark:hover:text-blue-400 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
              title="编辑模型"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/>
              </svg>
            </button>
            <!-- 删除按钮 -->
            <button
              @click="confirmDelete(model)"
              class="p-1.5 text-gray-500 hover:text-red-600 dark:hover:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition-colors"
              title="删除模型"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M3 6h18"/><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/>
              </svg>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 新建/编辑模型模态框 -->
    <BaseDialog
      :show="showModal"
      @close="showModal = false"
      max-width-class="max-w-[620px]"
    >
      <div class="p-6">
        <h3 class="text-lg font-bold text-gray-900 dark:text-white mb-1 flex items-center gap-2">
          <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5 text-blue-600 dark:text-blue-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect width="18" height="18" x="3" y="3" rx="2"/><path d="M9 3v18"/><path d="m14 9 3 3-3 3"/>
          </svg>
          {{ isEditing ? t('admin.aiModel.editModel') : t('admin.aiModel.addModel') }}
        </h3>
        <p class="text-xs text-gray-500 dark:text-gray-400 mb-5">
          配置 OpenAI 兼容标准接口参数，前台将通过后端安全代理发起调用。
        </p>

        <form @submit.prevent="handleSubmit" class="space-y-4">
          <!-- 模型别名与服务商 -->
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label class="block text-xs font-semibold text-gray-700 dark:text-gray-300 mb-1">
                {{ t('admin.aiModel.form.name') }} <span class="text-red-500">*</span>
              </label>
              <input
                v-model="form.name"
                type="text"
                required
                :placeholder="t('admin.aiModel.form.namePlaceholder')"
                class="w-full px-3.5 py-2 text-sm rounded-xl border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div>
              <label class="block text-xs font-semibold text-gray-700 dark:text-gray-300 mb-1">
                {{ t('admin.aiModel.form.provider') }}
              </label>
              <select
                v-model="form.provider"
                @change="handleProviderChange"
                class="w-full px-3.5 py-2 text-sm rounded-xl border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option v-for="p in providers" :key="p.value" :value="p.value">{{ p.label }}</option>
              </select>
            </div>
          </div>

          <!-- Base URL -->
          <div>
            <label class="block text-xs font-semibold text-gray-700 dark:text-gray-300 mb-1">
              {{ t('admin.aiModel.form.baseUrl') }} <span class="text-red-500">*</span>
            </label>
            <input
              v-model="form.baseUrl"
              type="url"
              required
              :placeholder="t('admin.aiModel.form.baseUrlPlaceholder')"
              class="w-full px-3.5 py-2 text-sm font-mono rounded-xl border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <!-- API Key -->
          <div>
            <label class="block text-xs font-semibold text-gray-700 dark:text-gray-300 mb-1">
              {{ t('admin.aiModel.form.apiKey') }}
              <span v-if="!isEditing" class="text-red-500">*</span>
              <span v-else class="text-xs text-gray-400 font-normal"> (留空表示沿用原有密钥)</span>
            </label>
            <div class="relative">
              <input
                v-model="form.apiKey"
                :type="showApiKey ? 'text' : 'password'"
                :required="!isEditing"
                :placeholder="isEditing ? '••••••••••••••••••••••••' : t('admin.aiModel.form.apiKeyPlaceholder')"
                class="w-full pl-3.5 pr-10 py-2 text-sm font-mono rounded-xl border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              <button
                type="button"
                @click="showApiKey = !showApiKey"
                class="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-200"
              >
                <svg v-if="showApiKey" xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M9.88 9.88a3 3 0 1 0 4.24 4.24"/><path d="M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68"/><path d="M6.61 6.61A13.526 13.526 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61"/><line x1="2" x2="22" y1="2" y2="22"/>
                </svg>
                <svg v-else xmlns="http://www.w3.org/2000/svg" class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z"/><circle cx="12" cy="12" r="3"/>
                </svg>
              </button>
            </div>
          </div>

          <!-- 模型标识与温度 -->
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label class="block text-xs font-semibold text-gray-700 dark:text-gray-300 mb-1">
                {{ t('admin.aiModel.form.modelName') }} <span class="text-red-500">*</span>
              </label>
              <input
                v-model="form.modelName"
                type="text"
                required
                :placeholder="t('admin.aiModel.form.modelNamePlaceholder')"
                class="w-full px-3.5 py-2 text-sm font-mono rounded-xl border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div>
              <div class="flex items-center justify-between mb-1">
                <label class="text-xs font-semibold text-gray-700 dark:text-gray-300">
                  {{ t('admin.aiModel.form.temperature') }}
                </label>
                <span class="text-xs font-mono font-medium text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-900/30 px-2 py-0.5 rounded">
                  {{ form.temperature }}
                </span>
              </div>
              <input
                v-model.number="form.temperature"
                type="range"
                min="0.0"
                max="2.0"
                step="0.05"
                class="w-full h-2 bg-gray-200 dark:bg-gray-700 rounded-lg appearance-none cursor-pointer accent-blue-600"
              />
              <span class="text-[11px] text-gray-400">
                {{ t('admin.aiModel.form.temperatureDesc') }}
              </span>
            </div>
          </div>

          <!-- Agent 提示词设定 -->
          <div>
            <div class="flex items-center justify-between mb-1">
              <label class="text-xs font-semibold text-gray-700 dark:text-gray-300">
                {{ t('admin.aiModel.form.agentPrompt') }}
              </label>
              <button
                type="button"
                @click="form.agentPrompt = ''"
                class="text-[11px] text-blue-600 dark:text-blue-400 hover:underline"
              >
                重置为系统默认
              </button>
            </div>
            <textarea
              v-model="form.agentPrompt"
              rows="3"
              :placeholder="t('admin.aiModel.form.agentPromptPlaceholder')"
              class="w-full px-3.5 py-2 text-xs rounded-xl border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
            ></textarea>
          </div>

          <!-- 开关组合 -->
          <div class="p-3.5 bg-gray-50 dark:bg-gray-800/60 rounded-xl border border-gray-100 dark:border-gray-700 space-y-3 text-xs">
            <label class="flex items-center justify-between cursor-pointer">
              <div>
                <span class="font-medium text-gray-800 dark:text-gray-200">{{ t('admin.aiModel.form.isDefault') }}</span>
                <p class="text-[11px] text-gray-400">{{ t('admin.aiModel.form.isDefaultDesc') }}</p>
              </div>
              <input v-model="form.isDefault" type="checkbox" class="w-4 h-4 text-blue-600 rounded border-gray-300 focus:ring-blue-500" />
            </label>

            <label class="flex items-center justify-between cursor-pointer border-t border-gray-100 dark:border-gray-700 pt-2.5">
              <div>
                <span class="font-medium text-gray-800 dark:text-gray-200">{{ t('admin.aiModel.form.isEnabled') }}</span>
                <p class="text-[11px] text-gray-400">{{ t('admin.aiModel.form.isEnabledDesc') }}</p>
              </div>
              <input v-model="form.isEnabled" type="checkbox" class="w-4 h-4 text-blue-600 rounded border-gray-300 focus:ring-blue-500" />
            </label>

            <label class="flex items-center justify-between cursor-pointer border-t border-gray-100 dark:border-gray-700 pt-2.5">
              <div>
                <span class="font-medium text-gray-800 dark:text-gray-200">{{ t('admin.aiModel.form.disableThinking') }}</span>
                <p class="text-[11px] text-gray-400">{{ t('admin.aiModel.form.disableThinkingDesc') }}</p>
              </div>
              <input v-model="form.disableThinking" type="checkbox" class="w-4 h-4 text-blue-600 rounded border-gray-300 focus:ring-blue-500" />
            </label>
          </div>

          <!-- 测试反馈信息 -->
          <div v-if="testResult" class="p-3 rounded-xl text-xs flex items-start gap-2" :class="testResult.success ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800' : 'bg-red-50 text-red-700 dark:bg-red-950/40 dark:text-red-300 border border-red-200 dark:border-red-800'">
            <svg v-if="testResult.success" xmlns="http://www.w3.org/2000/svg" class="w-4 h-4 flex-shrink-0 mt-0.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 6 9 17l-5-5"/></svg>
            <svg v-else xmlns="http://www.w3.org/2000/svg" class="w-4 h-4 flex-shrink-0 mt-0.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" x2="12" y1="8" y2="12"/><line x1="12" x2="12.01" y1="16" y2="16"/></svg>
            <div class="flex-1 min-w-0">
              <span class="font-semibold">{{ testResult.success ? '测试通过' : '测试失败' }}:</span>
              <span class="ml-1 break-all">{{ testResult.message }}</span>
            </div>
          </div>

          <!-- 弹窗底部操作按钮 -->
          <div class="flex items-center justify-between pt-4 border-t border-gray-100 dark:border-gray-700">
            <!-- 测试连接按钮 -->
            <button
              type="button"
              @click="handleTestConnection"
              :disabled="testing"
              class="inline-flex items-center px-3.5 py-2 text-xs font-medium text-gray-700 dark:text-gray-300 bg-gray-100 dark:bg-gray-700 hover:bg-gray-200 dark:hover:bg-gray-600 rounded-xl transition-colors disabled:opacity-50"
            >
              <svg v-if="testing" class="animate-spin -ml-1 mr-2 h-3.5 w-3.5 text-gray-500" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
              <svg v-else xmlns="http://www.w3.org/2000/svg" class="w-3.5 h-3.5 mr-1.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M22 12h-4l-3 9L9 3l-3 9H2"/>
              </svg>
              {{ testing ? t('admin.aiModel.testing') : t('admin.aiModel.testConnection') }}
            </button>

            <!-- 取消与提交 -->
            <div class="flex items-center gap-2">
              <button
                type="button"
                @click="showModal = false"
                class="px-4 py-2 text-xs font-medium text-gray-700 dark:text-gray-300 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 hover:bg-gray-50 dark:hover:bg-gray-700 rounded-xl transition-colors"
              >
                取消
              </button>
              <button
                type="submit"
                :disabled="submitting"
                class="px-5 py-2 text-xs font-medium text-white bg-blue-600 hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-600 rounded-xl shadow-sm transition-all disabled:opacity-50"
              >
                {{ submitting ? t('admin.submitting') : '保存配置' }}
              </button>
            </div>
          </div>
        </form>
      </div>
    </BaseDialog>

    <!-- 删除确认模态框 -->
    <BaseDialog
      :show="showDeleteConfirm"
      @close="showDeleteConfirm = false"
      max-width-class="max-w-[420px]"
    >
      <div class="p-6 text-center">
        <div class="w-12 h-12 rounded-full bg-red-100 dark:bg-red-900/30 text-red-600 dark:text-red-400 mx-auto flex items-center justify-center mb-4">
          <svg xmlns="http://www.w3.org/2000/svg" class="w-6 h-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 6h18"/><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/>
          </svg>
        </div>
        <h3 class="text-base font-bold text-gray-900 dark:text-white mb-2">删除确认</h3>
        <p class="text-xs text-gray-500 dark:text-gray-400 mb-6">
          {{ t('admin.aiModel.confirmDelete', { name: deletingModel?.name || '' }) }}
        </p>
        <div class="flex justify-center gap-3">
          <button
            type="button"
            @click="showDeleteConfirm = false"
            class="px-4 py-2 text-xs font-medium text-gray-700 dark:text-gray-300 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 hover:bg-gray-50 rounded-xl"
          >
            取消
          </button>
          <button
            type="button"
            @click="handleDelete"
            class="px-4 py-2 text-xs font-medium text-white bg-red-600 hover:bg-red-700 rounded-xl"
          >
            确定删除
          </button>
        </div>
      </div>
    </BaseDialog>
  </div>
</template>
