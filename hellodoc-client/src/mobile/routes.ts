import type { RouteRecordRaw } from 'vue-router'

const mobileRoutes: RouteRecordRaw[] = [
  {
    path: '/m',
    name: 'MobileHome',
    component: () => import('./views/HomeView.vue')
  },
  {
    path: '/m/shared',
    name: 'MobileShared',
    component: () => import('./views/SharedView.vue')
  },
  {
    path: '/m/kb/:kbId',
    name: 'MobileKbDetail',
    component: () => import('./views/KbDetailView.vue')
  },
  {
    path: '/m/kb/:kbId/doc/:docId',
    name: 'MobileDocEdit',
    component: () => import('./views/DocEditView.vue')
  },
  {
    path: '/m/profile',
    name: 'MobileProfile',
    component: () => import('./views/ProfileView.vue')
  },
  {
    path: '/m/login',
    name: 'MobileLogin',
    component: () => import('./views/LoginView.vue'),
    meta: { public: true }
  }
]

export default mobileRoutes
