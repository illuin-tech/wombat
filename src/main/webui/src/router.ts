import {createRouter, createWebHistory, type RouteRecordRaw} from 'vue-router'
import type {Permission} from '@/services/auth/permissions'
import {permissionGuard} from '@/services/auth/guard'
import {loadAppInfo} from '@/services/auth/session'
import HomeRedirectView from '@/pages/impact/HomeRedirectPage.vue'
import ImpactView from '@/pages/impact/ImpactPage.vue'
import ApiKeysView from '@/pages/api-keys/ApiKeysPage.vue'
import SignedOutView from '@/pages/system/SignedOutPage.vue'
import ForbiddenView from '@/pages/system/ForbiddenPage.vue'
import UsersView from '@/pages/users/UsersPage.vue'

declare module 'vue-router' {
    interface RouteMeta {
        permission?: Permission
        public?: boolean
    }
}

export const routes: RouteRecordRaw[] = [
    {
        path: '/',
        name: 'home',
        component: HomeRedirectView,
        meta: {permission: 'ui.dashboard.read'},
    },
    {
        path: '/impact/:environment',
        name: 'impact',
        component: ImpactView,
        meta: {permission: 'ui.dashboard.read'},
    },
    {
        path: '/users',
        name: 'users',
        component: UsersView,
        meta: {permission: 'ui.users.read'},
    },
    {
        path: '/api-keys',
        name: 'api-keys',
        component: ApiKeysView,
        meta: {permission: 'ui.apikeys.manage'},
    },
    {
        path: '/signed-out',
        name: 'signed-out',
        component: SignedOutView,
        meta: {public: true},
    },
    {
        path: '/forbidden',
        name: 'forbidden',
        component: ForbiddenView,
        meta: {public: true},
    },
    {
        path: '/:pathMatch(.*)*',
        redirect: to => ({name: 'home', query: to.query}),
    },
]

export const router = createRouter({
    history: createWebHistory(),
    routes,
})

router.beforeEach(permissionGuard(async () => (await loadAppInfo()).user))
