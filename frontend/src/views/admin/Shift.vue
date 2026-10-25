<template>
  <div class="page-card">
    <div class="toolbar">
      <h3 class="page-title">交班记录</h3>
      <span class="muted">长短款 = 实点现金 -（备用金 + 本班现金收入）。正数为长款，负数为短款。</span>
      <div class="spacer" />
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table :data="rows" size="small" border v-loading="loading">
      <el-table-column prop="shiftNo" label="班次号" width="170" />
      <el-table-column prop="cashierName" label="收银员" width="100" />
      <el-table-column prop="deviceCode" label="收银机" width="100" />
      <el-table-column label="开始" width="160">
        <template #default="{ row }">{{ fmt(row.startAt) }}</template>
      </el-table-column>
      <el-table-column label="结束" width="160">
        <template #default="{ row }">{{ fmt(row.endAt) || '—' }}</template>
      </el-table-column>
      <el-table-column label="单数" width="80" prop="orderCount" />
      <el-table-column label="销售额" width="110">
        <template #default="{ row }">{{ yuan(row.totalAmount) }}</template>
      </el-table-column>
      <el-table-column label="备用金" width="100">
        <template #default="{ row }">{{ yuan(row.openingCash) }}</template>
      </el-table-column>
      <el-table-column label="系统应收" width="110">
        <template #default="{ row }">{{ yuan(row.systemCash) }}</template>
      </el-table-column>
      <el-table-column label="实点" width="100">
        <template #default="{ row }">{{ yuan(row.closingCash) }}</template>
      </el-table-column>
      <el-table-column label="长短款" width="110">
        <template #default="{ row }">
          <span v-if="row.cashDiff === null || row.cashDiff === undefined" class="muted">—</span>
          <span v-else :class="diffClass(row.cashDiff)">{{ yuan(row.cashDiff) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 'OPEN' ? 'success' : 'info'">
            {{ row.status === 'OPEN' ? '当班中' : '已交班' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDetail(row)">明细</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="drawer" :title="`班次明细 · ${detail?.shiftNo || ''}`" size="560px">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="收银员">{{ detail.cashierName }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
          <el-descriptions-item label="备用金">{{ yuan(detail.openingCash) }}</el-descriptions-item>
          <el-descriptions-item label="现金收入（净）">{{ yuan(detail.cashAmount) }}</el-descriptions-item>
          <el-descriptions-item label="系统应收">{{ yuan(detail.systemCash) }}</el-descriptions-item>
          <el-descriptions-item label="实点现金">{{ yuan(detail.closingCash) }}</el-descriptions-item>
          <el-descriptions-item label="长短款">
            <span :class="diffClass(detail.cashDiff)">{{ yuan(detail.cashDiff) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="单数">{{ detail.orderCount }}</el-descriptions-item>
        </el-descriptions>

        <h4>支付方式</h4>
        <el-table :data="detail.channels || []" size="small" border>
          <el-table-column prop="channel" label="渠道" width="110" />
          <el-table-column prop="cnt" label="笔数" width="80" />
          <el-table-column label="金额">
            <template #default="{ row }">{{ yuan(row.amount) }}</template>
          </el-table-column>
        </el-table>

        <h4>本班订单</h4>
        <el-table :data="orders" size="small" border height="300">
          <el-table-column prop="orderNo" label="订单号" width="175" />
          <el-table-column label="金额" width="100">
            <template #default="{ row }">{{ yuan(row.payableAmount) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="105" />
          <el-table-column label="时间" width="150">
            <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api'
import { yuan } from '../../session'

const rows = ref([])
const loading = ref(false)
const drawer = ref(false)
const detail = ref(null)
const orders = ref([])

function fmt(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : ''
}

function diffClass(v) {
  if (v === null || v === undefined || v === 0) return 'ok'
  return v > 0 ? 'long' : 'short'
}

async function load() {
  loading.value = true
  try {
    rows.value = await api.shiftPage({ page: 1, size: 50 })
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function showDetail(row) {
  try {
    detail.value = await api.shiftDetail(row.id)
    orders.value = await api.shiftOrders(row.id)
    drawer.value = true
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(load)
</script>

<style scoped>
h4 {
  margin: 18px 0 8px;
  font-size: 14px;
}

.ok {
  color: #2f9e44;
}

.long {
  color: #e6a23c;
}

.short {
  color: #c8102e;
  font-weight: 600;
}
</style>
