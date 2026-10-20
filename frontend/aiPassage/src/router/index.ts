import {createRouter, createWebHistory} from "vue-router";
import UserLoginPage from "@/pages/user/UserLoginPage.vue";
import HomePage from "@/pages/HomePage.vue";
import UserRegisterPage from "@/pages/user/UserRegisterPage.vue";

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
        {
            path: '/',
            name: '主页',
            component: HomePage,
        },
        {
            path: '/user/login',
            name: '用户登录',
            component: UserLoginPage,
        },
        {
            path: '/user/register',
            name: '用户注册',
            component: UserRegisterPage,
        },
        {
            path: '/admin/userManage',
            name: '用户管理',
            component: () => import('@/pages/admin/UserManagePage.vue'),
        },
        {
            path: '/create',
            name: '创作文章',
            component: () => import('@/pages/article/ArticleCreatePage.vue'),
        },
        {
            path: '/article/list',
            name: '文章列表',
            component: () => import('@/pages/article/ArticleListPage.vue'),
        },
        {
            path: '/article/:taskId',
            name: '文章详情',
            component: () => import('@/pages/article/ArticleDetailPage.vue'),
        },
        {
            path: '/admin/statistics',
            name: '数据分析',
            component: () => import('@/pages/admin/StatisticsPage.vue'),
        },
        {
            path: '/vip',
            name: '会员中心',
            component: () => import('@/pages/vip/VipPage.vue'),
        },

    ],
})

export default router
