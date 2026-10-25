<template>
  <div class="pos-shell">
    <header class="pos-head">
      <div class="logo">仓储超市 <span>POS</span></div>
      <nav class="tabs">
        <router-link to="/pos/cashier" class="tab" active-class="on">收银台</router-link>
        <router-link to="/pos/orders" class="tab" active-class="on">本班订单</router-link>
        <router-link to="/pos/shift" class="tab" active-class="on">交班</router-link>
      </nav>
      <div class="spacer" />
      <div class="shift-info" v-if="shift">
        <el-tag type="success" effect="dark" size="small">当班中</el-tag>
        <span class="mono">{{ shift.shiftNo }}</span>
        <span class="muted">已收 {{ yuan(shift.totalAmount) }} 元 / {{ shift.orderCount }} 单</span>
      </div>
      <div class="shift-info" v-else>
        <el-tag type="warning" size="small">未开班</el-tag>
      </div>
      <el-button size="small" text @click="toAdmin" v-if="canAdmin">管理端</el-button>
      <el-button size="small" text @click="logout">退出</el-button>
    </header>

    <main class="pos-body">
      <router-view v-slot="{ Component }">
        <component :is="Component" />
      </router-view>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { session, isManager, doLogout, yuan } from '../session'

const router = useRouter()
const shift = ref(null)
const canAdmin = computed(() => isManager())
let timer = null

async function loadShift() {
  try {
    shift.value = await api.shiftCurrent()
  } catch (e) {
    shift.value = null
  }
}

function toAdmin() {
  router.push('/admin/dashboard')
}

async function logout() {
  await doLogout()
  router.replace('/login')
}

onMounted(() => {
  loadShift()
  // 30 秒刷一次班次汇总：收银员不用手动刷新就能看到当前流水
  timer = setInterval(loadShift, 30000)
})

onUnmounted(() => timer && clearInterval(timer))

defineExpose({ reloadShift: loadShift })
</script>

<style scoped>
.pos-shell {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--pos-bg);
}

.pos-head {
  height: 52px;
  background: #1f2733;
  color: #fff;
  display: flex;
  align-items: center;
  padding: 0 14px;
  gap: 14px;
  flex-shrink: 0;
}

.logo {
  font-weight: 700;
  letter-spacing: 0.5px;
}

.logo span {
  color: #ff5a6e;
  margin-left: 2px;
}

.tabs {
  display: flex;
  gap: 4px;
}

.tab {
  color: #aab4c2;
  text-decoration: none;
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 14px;
}

.tab:hover {
  background: rgba(255, 255, 255, 0.06);
}

.tab.on {
  background: var(--pos-primary);
  color: #fff;
}

.spacer {
  flex: 1;
}

.shift-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.shift-info .muted {
  color: #9aa5b4;
}

.mono {
  font-variant-numeric: tabular-nums;
}

.pos-body {
  flex: 1;
  min-height: 0;
  padding: 10px;
}
</style>
