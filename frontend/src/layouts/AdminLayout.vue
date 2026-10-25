<template>
  <div class="admin-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-name">会员仓储超市</div>
        <div class="brand-sub">收银管理系统 · 管理端</div>
      </div>

      <el-menu :default-active="active" router class="menu" background-color="#1f2733"
               text-color="#c3ccda" active-text-color="#ffffff">
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.title }}</span>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-foot muted">v1.0 · 课程设计演示版</div>
    </aside>

    <div class="main">
      <header class="topbar">
        <div class="crumb">
          <span class="crumb-root">管理端</span>
          <el-icon><ArrowRight /></el-icon>
          <span>{{ currentTitle }}</span>
        </div>
        <div class="spacer" />
        <el-tag v-if="!session.token" type="danger">未登录</el-tag>
        <span class="who">{{ session.user?.realName || session.user?.username }}</span>
        <el-dropdown @command="onCommand">
          <el-button text>
            <el-icon><User /></el-icon>
            <el-icon><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="pos">切换到收银台</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>

      <main class="content">
        <router-view v-slot="{ Component }">
          <component :is="Component" />
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { session, hasAnyPerm, doLogout } from '../session'

const route = useRoute()
const router = useRouter()

const ALL = [
  { path: '/admin/dashboard', title: '经营看板', icon: 'DataLine' },
  { path: '/admin/goods', title: '商品管理', icon: 'Goods', perms: ['goods:view'] },
  { path: '/admin/stock', title: '库存与补货', icon: 'Box', perms: ['stock:view'] },
  { path: '/admin/member', title: '会员管理', icon: 'UserFilled', perms: ['member:view'] },
  { path: '/admin/promotion', title: '促销与券', icon: 'Discount', perms: ['promo:view'] },
  { path: '/admin/order', title: '订单查询', icon: 'Tickets', perms: ['order:view'] },
  { path: '/admin/shift', title: '交班记录', icon: 'Clock', perms: ['pos:shift'] },
  { path: '/admin/report', title: '销售报表', icon: 'TrendCharts', perms: ['report:view'] },
  { path: '/admin/user', title: '员工与角色', icon: 'Avatar', perms: ['sys:user'] },
  {
    path: '/admin/system',
    title: '系统设置',
    icon: 'Setting',
    perms: ['sys:config', 'sys:dict', 'sys:store', 'sys:device', 'sys:log']
  }
]

// 菜单按权限过滤：没权限的菜单不显示，避免点进去 403。
// 看板不设权限——只要能进管理端就该看到经营数据。
const menus = computed(() => ALL.filter((m) => !m.perms || hasAnyPerm(m.perms)))

const active = computed(() => route.path)
const currentTitle = computed(() => route.meta.title || '')

async function onCommand(cmd) {
  if (cmd === 'logout') {
    await ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' })
    await doLogout()
    router.replace('/login')
  } else if (cmd === 'pos') {
    router.push('/pos/cashier')
  }
}
</script>

<style scoped>
.admin-shell {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.sidebar {
  width: 216px;
  background: #1f2733;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.brand {
  padding: 18px 18px 14px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.brand-name {
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.brand-sub {
  color: #7d8899;
  font-size: 12px;
  margin-top: 4px;
}

.menu {
  border-right: none;
  flex: 1;
  overflow-y: auto;
  padding-top: 8px;
}

.menu :deep(.el-menu-item.is-active) {
  background: #c8102e !important;
  border-radius: 6px;
  margin: 0 8px;
}

.menu :deep(.el-menu-item) {
  margin: 2px 8px;
  border-radius: 6px;
  height: 42px;
}

.sidebar-foot {
  padding: 12px 18px;
  color: #5d6878;
  font-size: 12px;
}

.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.topbar {
  height: 56px;
  background: #fff;
  border-bottom: 1px solid var(--pos-border);
  display: flex;
  align-items: center;
  padding: 0 16px;
  gap: 8px;
  flex-shrink: 0;
}

.crumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
}

.crumb-root {
  color: var(--pos-muted);
}

.spacer {
  flex: 1;
}

.who {
  font-size: 14px;
  margin-right: 4px;
}

.content {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}
</style>
