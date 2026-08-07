<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, type RouteLocationNormalizedLoaded } from 'vue-router'
import Message from './components/Message.vue'

const router = useRouter()
const baseKeepAliveComponents = ['DocumentEditor', 'KbDetailView']
const keepAliveComponents = ref([...baseKeepAliveComponents])

const shouldKeepAliveRoute = (targetRoute: RouteLocationNormalizedLoaded) => {
  return targetRoute.name === 'Editor' || targetRoute.meta?.keepAlive === true
}

const getRouteCacheKey = (targetRoute: RouteLocationNormalizedLoaded) => {
  if (targetRoute.name === 'MobileKbDetail') {
    return `MobileKbDetail:${String(targetRoute.params.kbId ?? '')}`
  }
  return String(targetRoute.name || targetRoute.path)
}

router.beforeEach((to, from, next) => {
  // 如果进入了编辑页，并且是从非预览页（如首页）进入的，则清空缓存，以便重置状态
  if (to.name === 'Editor' && from.name !== 'PublicView') {
    keepAliveComponents.value = []
  }
  next()
})

router.afterEach((to) => {
  if (to.name === 'Editor') {
    // 恢复对 DocumentEditor 的缓存，让之后如果在编辑页内点击预览后再返回，由于已经处于白名单中则可以保留其状态
    setTimeout(() => {
        keepAliveComponents.value = [...baseKeepAliveComponents]
    }, 0)
  }
})
</script>

<template>
  <router-view v-slot="{ Component, route: currentRoute }">
    <keep-alive :include="keepAliveComponents" :max="4">
      <component
        v-if="shouldKeepAliveRoute(currentRoute)"
        :is="Component"
        :key="getRouteCacheKey(currentRoute)"
      />
    </keep-alive>
    <component
      v-if="!shouldKeepAliveRoute(currentRoute)"
      :is="Component"
      :key="currentRoute.fullPath"
    />
  </router-view>
  <Message />
</template>

<style>
/* 全局样式已在 style.css 中处理 */
</style>
