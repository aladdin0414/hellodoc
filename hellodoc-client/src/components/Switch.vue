<script setup lang="ts">
const props = defineProps<{
    modelValue: boolean
    label?: string
    disabled?: boolean
}>()

const emit = defineEmits(['update:modelValue', 'change'])

const toggle = () => {
    if (props.disabled) return
    emit('update:modelValue', !props.modelValue)
    emit('change', !props.modelValue)
}
</script>

<template>
    <div class="flex items-center space-x-2">
        <span v-if="label" @click="toggle" :class="[
            'text-sm font-bold cursor-pointer select-none whitespace-nowrap',
            disabled ? 'text-slate-300' : 'text-slate-600'
        ]">
            {{ label }}
        </span>
        <button @click="toggle" :disabled="disabled" :disabled-attr="disabled" type="button"
            class="relative inline-flex h-5 w-9 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-indigo-600 focus:ring-offset-1 disabled:opacity-50 disabled:cursor-not-allowed"
            :class="[modelValue ? 'bg-indigo-600' : 'bg-slate-200']">
            <span class="sr-only">Toggle</span>
            <span aria-hidden="true"
                class="pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out"
                :class="[modelValue ? 'translate-x-4' : 'translate-x-0']" />
        </button>
    </div>
</template>
