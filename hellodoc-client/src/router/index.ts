import { createRouter, createWebHistory } from 'vue-router'
import mobileRoutes from '../mobile/routes'

const DocumentEditor = () => import('../components/DocumentEditor.vue')
const KnowledgeBase = () => import('../components/KnowledgeBase.vue')
const Login = () => import('../components/Login.vue')
const Register = () => import('../components/Register.vue')
const KnowledgeBaseView = () => import('../components/KnowledgeBaseView.vue')
const AdminManagement = () => import('../components/AdminManagement.vue')

const routes = [
    {
        path: '/login',
        name: 'Login',
        component: Login,
        meta: { public: true }
    },
    {
        path: '/register',
        name: 'Register',
        component: Register,
        meta: { public: true }
    },
    {
        path: '/view/:kbId/:docId?',
        name: 'PublicView',
        component: KnowledgeBaseView,
        meta: { public: true },
        props: (route: any) => ({
            kbId: Number(route.params.kbId),
            docId: route.params.docId ? Number(route.params.docId) : undefined
        })
    },
    {
        path: '/',
        name: 'Home',
        component: KnowledgeBase
    },
    {
        path: '/shared',
        name: 'HomeShared',
        component: KnowledgeBase
    },
    {
        path: '/favorites',
        name: 'HomeFavorites',
        component: KnowledgeBase
    },
    {
        path: '/recent',
        name: 'HomeRecent',
        component: KnowledgeBase
    },
    {
        path: '/admin',
        name: 'Admin',
        component: AdminManagement,
        meta: { requiresAdmin: true }
    },
    {
        path: '/kb/:kbId/:docId?',
        name: 'Editor',
        component: DocumentEditor,
        props: (route: any) => ({
            kbId: Number(route.params.kbId),
            docId: route.params.docId ? Number(route.params.docId) : undefined
        })
    },

    // 📱 挂载移动端 H5 专区子路由 (/m)
    ...mobileRoutes
]

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes
})

router.beforeEach((to, _from, next) => {
    const token = localStorage.getItem('accessToken') || localStorage.getItem('token')
    const userRole = localStorage.getItem('userRole')
    const isPublic = to.meta?.public
    const isAuthPage = ['Login', 'Register', 'MobileLogin'].includes(to.name as string)

    // 若用户主动进入移动端页面，重置“电脑版”偏好标记
    if (to.path.startsWith('/m')) {
        sessionStorage.removeItem('preferDesktop')
    }

    const preferDesktop = sessionStorage.getItem('preferDesktop') === 'true'

    // 移动端 User-Agent 智能识别（若用户未开启“电脑版”模式，移动设备访问 / 自动重定向至 /m）
    const isMobileDevice = /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent)
    if (isMobileDevice && !preferDesktop && to.path === '/') {
        return next('/m')
    }

    if (!isPublic && !token) {
        // 未登录重定向
        if (to.path.startsWith('/m')) {
            next('/m/login')
        } else {
            next({ name: 'Login' })
        }
    } else if (isAuthPage && token) {
        // 已登录访问登录页重定向
        if (to.path.startsWith('/m')) {
            next('/m')
        } else {
            next({ name: 'Home' })
        }
    } else if (to.meta?.requiresAdmin && userRole !== 'admin') {
        next({ name: 'Home' })
    } else {
        next()
    }
})

export default router
