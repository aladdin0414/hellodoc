<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { message } from '../../utils/message'
import { listConfigs, updateConfig, createConfig, refreshConfigCache } from '../../api/config'
import zhCN from '../../i18n/locales/zh-CN/common'
import enUS from '../../i18n/locales/en-US/common'
import BaseDialog from '../shared/BaseDialog.vue'

const { t, locale } = useI18n()

// Config management state
const configs = ref<any[]>([])
const configLoading = ref(false)
const showConfigModal = ref(false)
const submitting = ref(false)

const configForm = reactive({
  configName: '',
  configKey: '',
  configValue: 'true',
  valueType: 'boolean',
  isFrontend: true,
  description: '',
  configNameI18n: {} as Record<string, string>,
  descriptionI18n: {} as Record<string, string>
})

const getI18nText = (config: any, field: 'configName' | 'description') => {
  const i18nField = field === 'configName' ? 'configNameI18n' : 'descriptionI18n'
  const i18nMap = config[i18nField]
  if (i18nMap && i18nMap[locale.value]) {
    return i18nMap[locale.value]
  }
  return config[field]
}

const isAiConfig = (config: any) => String(config?.configKey || '').startsWith('ai.openai.')
const isLongTextConfig = (config: any) => config?.configKey === 'ai.openai.agent'

const groupedConfigs = computed(() => {
  const aiConfigs = configs.value.filter((config) => isAiConfig(config))
  const otherConfigs = configs.value.filter((config) => !isAiConfig(config))
  const groups: Array<{ key: string; title: string; items: any[] }> = []

  if (aiConfigs.length > 0) {
    groups.push({ key: 'ai', title: t('admin.settings.groups.ai'), items: aiConfigs })
  }
  if (otherConfigs.length > 0) {
    groups.push({ key: 'other', title: t('admin.settings.groups.other'), items: otherConfigs })
  }

  return groups
})

const autoResizeTextarea = (event: Event) => {
  const textarea = event.target as HTMLTextAreaElement
  const lineHeight = parseFloat(window.getComputedStyle(textarea).lineHeight || '20')
  const maxHeight = lineHeight * 5
  textarea.style.height = 'auto'
  textarea.style.height = `${Math.min(textarea.scrollHeight, maxHeight)}px`
  textarea.style.overflowY = textarea.scrollHeight > maxHeight ? 'auto' : 'hidden'
}

const fetchConfigs = async () => {
  configLoading.value = true
  try {
    const res: any = await listConfigs()
    configs.value = res
  } catch (err) {
    console.error('Fetch configs failed:', err)
  } finally {
    configLoading.value = false
  }
}

const initializeSystemConfigs = async () => {
  try {
    const res: any = await listConfigs()
    if (!res.some((c: any) => c.configKey === 'app.kb_search.enabled')) {
      await createConfig({
        configName: t('admin.defaultConfigs.kbSearch.name'),
        configKey: 'app.kb_search.enabled',
        configValue: 'true',
        valueType: 'boolean',
        isFrontend: true,
        description: t('admin.defaultConfigs.kbSearch.desc'),
        configNameI18n: { 
          'zh-CN': zhCN.admin.defaultConfigs.kbSearch.name, 
          'en-US': enUS.admin.defaultConfigs.kbSearch.name 
        },
        descriptionI18n: { 
          'zh-CN': zhCN.admin.defaultConfigs.kbSearch.desc, 
          'en-US': enUS.admin.defaultConfigs.kbSearch.desc 
        }
      })
    }
    if (!res.some((c: any) => c.configKey === 'app.collab.enabled')) {
      await createConfig({
        configName: t('admin.defaultConfigs.collab.name'),
        configKey: 'app.collab.enabled',
        configValue: 'false',
        valueType: 'boolean',
        isFrontend: true,
        description: t('admin.defaultConfigs.collab.desc'),
        configNameI18n: { 
          'zh-CN': zhCN.admin.defaultConfigs.collab.name, 
          'en-US': enUS.admin.defaultConfigs.collab.name 
        },
        descriptionI18n: { 
          'zh-CN': zhCN.admin.defaultConfigs.collab.desc, 
          'en-US': enUS.admin.defaultConfigs.collab.desc 
        }
      })
    }
    if (!res.some((c: any) => c.configKey === 'app.kb.nav_style')) {
      await createConfig({
        configName: t('admin.defaultConfigs.navStyle.name'),
        configKey: 'app.kb.nav_style',
        configValue: 'top',
        valueType: 'string',
        isFrontend: true,
        description: t('admin.defaultConfigs.navStyle.desc'),
        configNameI18n: { 
          'zh-CN': zhCN.admin.defaultConfigs.navStyle.name, 
          'en-US': enUS.admin.defaultConfigs.navStyle.name 
        },
        descriptionI18n: { 
          'zh-CN': zhCN.admin.defaultConfigs.navStyle.desc, 
          'en-US': enUS.admin.defaultConfigs.navStyle.desc 
        }
      })
    }
    if (!res.some((c: any) => c.configKey === 'ai.openai.base-url')) {
      await createConfig({
        configName: t('admin.defaultConfigs.aiUrl.name'),
        configKey: 'ai.openai.base-url',
        configValue: '',
        valueType: 'string',
        isFrontend: false,
        description: t('admin.defaultConfigs.aiUrl.desc'),
        configNameI18n: { 
          'zh-CN': zhCN.admin.defaultConfigs.aiUrl.name, 
          'en-US': enUS.admin.defaultConfigs.aiUrl.name 
        },
        descriptionI18n: { 
          'zh-CN': zhCN.admin.defaultConfigs.aiUrl.desc, 
          'en-US': enUS.admin.defaultConfigs.aiUrl.desc 
        }
      })
    }
    if (!res.some((c: any) => c.configKey === 'ai.openai.api-key')) {
      await createConfig({
        configName: t('admin.defaultConfigs.aiApiKey.name'),
        configKey: 'ai.openai.api-key',
        configValue: '',
        valueType: 'string',
        isFrontend: false,
        description: t('admin.defaultConfigs.aiApiKey.desc'),
        configNameI18n: { 
          'zh-CN': zhCN.admin.defaultConfigs.aiApiKey.name, 
          'en-US': enUS.admin.defaultConfigs.aiApiKey.name 
        },
        descriptionI18n: { 
          'zh-CN': zhCN.admin.defaultConfigs.aiApiKey.desc, 
          'en-US': enUS.admin.defaultConfigs.aiApiKey.desc 
        }
      })
    }
    await fetchConfigs()
  } catch (err) {
    console.error('Initialize system configs failed:', err)
  }
}

const handleUpdateConfig = async (config: any) => {
  try {
    await updateConfig(config)
    message.success(t('admin.msg.configSaved'))
  } catch (err) {
    console.error('Update config failed:', err)
    message.error(t('admin.msg.configSaveFailed'))
  }
}

const openAddConfigModal = () => {
  Object.assign(configForm, {
    configName: '',
    configKey: '',
    configValue: 'true',
    valueType: 'boolean',
    isFrontend: true,
    description: '',
    configNameI18n: {},
    descriptionI18n: {}
  })
  showConfigModal.value = true
}

const handleAddConfig = async () => {
  submitting.value = true
  try {
    await createConfig(configForm)
    message.success(t('admin.msg.configAdded'))
    showConfigModal.value = false
    fetchConfigs()
  } catch (err: any) {
    console.error('Add config failed:', err)
    message.error(err.message || t('admin.msg.configAddFailed'))
  } finally {
    submitting.value = false
  }
}

const handleRefreshCache = async () => {
  try {
    await refreshConfigCache()
    message.success(t('admin.msg.cacheRefreshed'))
  } catch (err) {
    console.error('Refresh cache failed:', err)
  }
}

onMounted(() => {
  initializeSystemConfigs()
})
</script>

<template>
  <div class="space-y-6">
    <div class="flex justify-between items-center">
      <div>
        <h2 class="text-2xl font-bold text-gray-900 dark:text-white">{{ t('admin.settings.title') }}</h2>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">{{ t('admin.settings.subtitle') }}</p>
      </div>
      <div class="flex items-center space-x-2">
        <button @click="openAddConfigModal"
          class="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg text-sm font-semibold transition-colors shadow-sm flex items-center space-x-2">
          <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
          </svg>
          <span>{{ t('admin.settings.addConfig') }}</span>
        </button>
        <button @click="handleRefreshCache"
          class="px-4 py-2 bg-gray-100 dark:bg-gray-700 hover:bg-gray-200 dark:hover:bg-gray-600 text-gray-700 dark:text-gray-200 rounded-lg text-sm font-semibold transition-colors flex items-center space-x-2">
          <svg class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
          <span>{{ t('admin.settings.refreshCache') }}</span>
        </button>
      </div>
    </div>

    <div v-if="configLoading" class="py-12 text-center text-gray-500">{{ t('common.loading') }}</div>
    <div v-else class="space-y-6">
      <div v-for="group in groupedConfigs" :key="group.key" class="space-y-4">
        <div class="flex items-center justify-between">
          <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-300">{{ group.title }}</h3>
          <span class="text-xs text-gray-400 dark:text-gray-500">{{ group.items.length }}</span>
        </div>

        <div v-for="config in group.items" :key="config.id"
          class="p-6 bg-gray-50 dark:bg-gray-700/30 rounded-2xl border border-gray-100 dark:border-gray-700 flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div class="flex-1">
            <div class="flex items-center gap-2 mb-1">
              <h4 class="text-sm font-bold text-gray-900 dark:text-white">{{ getI18nText(config, 'configName') }}</h4>
            </div>
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ getI18nText(config, 'description') || t('admin.settings.noDesc') }}</p>
          </div>

          <div class="w-full md:w-auto md:min-w-[600px] flex flex-col items-stretch gap-2">
            <span class="self-start px-1.5 py-0.5 bg-gray-200 dark:bg-gray-600 text-[10px] text-gray-500 dark:text-gray-400 rounded font-mono">{{ config.configKey }}</span>
            <template v-if="config.valueType === 'boolean'">
              <label class="relative inline-flex items-center cursor-pointer">
                <input type="checkbox" :checked="config.configValue === 'true'"
                  @change="(e: any) => { const checked = e.target.checked; config.configValue = checked ? 'true' : 'false'; handleUpdateConfig(config) }"
                  class="sr-only peer">
                <div class="w-11 h-6 bg-gray-200 peer-focus:outline-none rounded-full peer dark:bg-gray-600 peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all dark:border-gray-600 peer-checked:bg-blue-600"></div>
              </label>
            </template>
            <template v-else-if="config.configKey === 'app.kb.nav_style'">
              <select v-model="config.configValue"
                @change="handleUpdateConfig(config)"
                class="w-full md:w-[600px] px-3 py-1.5 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-600 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 outline-none transition-all">
                <option value="top">{{ t('admin.settings.form.navStyleTop') }}</option>
                <option value="left">{{ t('admin.settings.form.navStyleLeft') }}</option>
              </select>
            </template>
            <template v-else-if="isLongTextConfig(config)">
              <textarea v-model="config.configValue"
                @focus="autoResizeTextarea"
                @input="autoResizeTextarea"
                @blur="handleUpdateConfig(config)"
                rows="4"
                class="w-full md:w-[600px] px-3 py-2 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-600 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 outline-none transition-all resize-none overflow-y-hidden"></textarea>
            </template>
            <template v-else>
              <input v-model="config.configValue"
                @blur="handleUpdateConfig(config)"
                type="text"
                class="w-full md:w-[600px] px-3 py-1.5 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-600 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 outline-none transition-all" />
            </template>
          </div>
        </div>
      </div>

      <div v-if="groupedConfigs.length === 0" class="py-12 text-center text-gray-500">
        {{ t('admin.settings.noConfigs') }}
      </div>
    </div>

    <!-- Config Modal Overlay -->
    <BaseDialog :show="showConfigModal" max-width-class="max-w-md" @close="showConfigModal = false">
      <div
        class="px-6 py-4 border-b border-gray-100 dark:border-gray-700 flex justify-between items-center bg-gray-50 dark:bg-gray-800/50">
        <h3 class="text-lg font-bold text-gray-900 dark:text-white">{{ t('admin.settings.addConfig') }}</h3>
        <button @click="showConfigModal = false" class="text-gray-400 hover:text-gray-600 transition-colors">
          <svg class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>
      <form @submit.prevent="handleAddConfig" class="p-6 space-y-4">
        <div class="space-y-4">
          <div>
            <label class="block text-xs font-semibold text-gray-500 uppercase mb-1">{{ t('admin.settings.form.configName') }}</label>
            <input v-model="configForm.configName" @input="configForm.configNameI18n['zh-CN'] = configForm.configName" type="text" required :placeholder="t('admin.settings.form.configNamePlaceholder')"
              class="w-full px-4 py-2 bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 outline-none transition-all" />
          </div>
          <div>
            <label class="block text-xs font-semibold text-gray-500 uppercase mb-1">{{ t('admin.settings.form.configName') }} (English)</label>
            <input v-model="configForm.configNameI18n['en-US']" type="text" placeholder="English configuration name"
              class="w-full px-4 py-2 bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 outline-none transition-all" />
          </div>
          <div>
            <label class="block text-xs font-semibold text-gray-500 uppercase mb-1">{{ t('admin.settings.form.configKey') }}</label>
            <input v-model="configForm.configKey" type="text" required :placeholder="t('admin.settings.form.configKeyPlaceholder')"
              class="w-full px-4 py-2 bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 outline-none transition-all" />
          </div>
          <div>
            <label class="block text-xs font-semibold text-gray-500 uppercase mb-1">{{ t('admin.settings.form.desc') }}</label>
            <textarea v-model="configForm.description" @input="configForm.descriptionI18n['zh-CN'] = configForm.description" rows="2"
              class="w-full px-4 py-2 bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 outline-none transition-all" />
          </div>
          <div>
            <label class="block text-xs font-semibold text-gray-500 uppercase mb-1">{{ t('admin.settings.form.desc') }} (English)</label>
            <textarea v-model="configForm.descriptionI18n['en-US']" rows="2" placeholder="English description"
              class="w-full px-4 py-2 bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 outline-none transition-all" />
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-xs font-semibold text-gray-500 uppercase mb-1">{{ t('admin.settings.form.valueType') }}</label>
              <select v-model="configForm.valueType"
                class="w-full px-4 py-2 bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 outline-none transition-all">
                <option value="string">{{ t('admin.settings.form.valueTypeString') }}</option>
                <option value="boolean">{{ t('admin.settings.form.valueTypeBoolean') }}</option>
              </select>
            </div>
            <div class="flex items-end pb-2">
              <label class="flex items-center cursor-pointer">
                <input type="checkbox" v-model="configForm.isFrontend" class="sr-only peer">
                <div class="w-9 h-5 bg-gray-200 peer-focus:outline-none rounded-full peer dark:bg-gray-600 peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all dark:border-gray-600 peer-checked:bg-blue-600"></div>
                <span class="ml-2 text-xs font-semibold text-gray-500 uppercase">{{ t('admin.settings.form.frontend') }}</span>
              </label>
            </div>
          </div>
        </div>
        <div class="pt-4 flex space-x-3">
          <button type="button" @click="showConfigModal = false"
            class="flex-1 px-5 py-2 border border-gray-200 dark:border-gray-700 rounded-xl text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition-all">{{ t('nav.cancel') }}</button>
          <button type="submit" :disabled="submitting"
            class="flex-1 px-5 py-2 bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed text-white rounded-xl text-sm font-semibold transition-all active:scale-95">
            {{ t('admin.settings.submitConfig') }}
          </button>
        </div>
      </form>
    </BaseDialog>
  </div>
</template>
