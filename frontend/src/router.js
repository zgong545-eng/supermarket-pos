import { createRouter, createWebHashHistory } from 'vue-router'
import { session, isMember, landingPath } from './session'

/**
 * 路由表按端分三组，各自套一层布局：
 *   /admin/**  左侧菜单 + 顶部面包屑
 *   /pos/**    全屏收银台，无侧边栏（收银员盯屏幕，别给他点别的东西）
 *   /h5/**     手机端，底部 tabbar
 *
 * 用 hash 模式：nginx 不用配 try_files 也能直接刷新，
 * 答辩换机器部署时少一个坑。
 */
const routes = [
  { path: '/', redirect: () => landingPath() },
  { path: '/login', component: () => import('./views/Login.vue'), meta: { public: true } },

  // ---------------- 管理端 ----------------
  {
    path: '/admin',
    component: () => import('./layouts/AdminLayout.vue'),
    redirect: '/admin/dashboard',
    children: [
      { path: 'dashboard', component: () => import('./views/admin/Dashboard.vue'), meta: { title: '经营看板' } },
      { path: 'goods', component: () => import('./views/admin/Goods.vue'), meta: { title: '商品管理', perm: 'goods:view' } },
      { path: 'stock', component: () => import('./views/admin/Stock.vue'), meta: { title: '库存与补货', perm: 'stock:view' } },
      { path: 'member', component: () => import('./views/admin/Member.vue'), meta: { title: '会员管理', perm: 'member:view' } },
      { path: 'promotion', component: () => import('./views/admin/Promotion.vue'), meta: { title: '促销与优惠券', perm: 'promo:view' } },
      { path: 'order', component: () => import('./views/admin/Order.vue'), meta: { title: '订单查询', perm: 'order:view' } },
      { path: 'shift', component: () => import('./views/admin/Shift.vue'), meta: { title: '交班记录', perm: 'pos:shift' } },
      { path: 'report', component: () => import('./views/admin/Report.vue'), meta: { title: '销售报表', perm: 'report:view' } },
      { path: 'user', component: () => import('./views/admin/User.vue'), meta: { title: '员工与角色', perm: 'sys:user' } },
      { path: 'system', component: () => import('./views/admin/System.vue'), meta: { title: '系统设置', perm: 'sys:config', fallbackPerms: ['sys:dict', 'sys:store', 'sys:device', 'sys:log'] } }
    ]
  },

  // ---------------- 收银端 ----------------
  {
    path: '/pos',
    component: () => import('./layouts/PosLayout.vue'),
    redirect: '/pos/cashier',
    children: [
      { path: 'cashier', component: () => import('./views/pos/Cashier.vue'), meta: { title: '收银台', perm: 'pos:checkout' } },
      { path: 'orders', component: () => import('./views/pos/PosOrders.vue'), meta: { title: '本班订单', perm: 'pos:checkout' } },
      { path: 'shift', component: () => import('./views/pos/Shift.vue'), meta: { title: '交班', perm: 'pos:shift' } }
    ]
  },

  // ---------------- 会员 H5 ----------------
  { path: '/h5/login', component: () => import('./views/h5/H5Login.vue'), meta: { public: true } },
  {
    path: '/h5',
    component: () => import('./layouts/H5Layout.vue'),
    redirect: '/h5/home',
    children: [
      { path: 'home', component: () => import('./views/h5/Home.vue'), meta: { title: '首页' } },
      { path: 'cart', component: () => import('./views/h5/Cart.vue'), meta: { title: '购物车' } },
      { path: 'orders', component: () => import('./views/h5/Orders.vue'), meta: { title: '我的订单' } },
      { path: 'mine', component: () => import('./views/h5/Mine.vue'), meta: { title: '我的' } }
    ]
  },

  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to) => {
  // 公开页面直接放行；但已登录还去登录页要送回各自的首页
  if (to.meta.public) {
    if (session.token && session.user) return landingPath()
    return true
  }

  if (!session.token) {
    return to.path.startsWith('/h5') ? '/h5/login' : '/login'
  }

  // 会员只能待在 H5 区，员工不能进 H5 区
  const member = isMember()
  if (member && !to.path.startsWith('/h5')) return '/h5/home'
  if (!member && to.path.startsWith('/h5')) return landingPath()

  return true
})

export default router
