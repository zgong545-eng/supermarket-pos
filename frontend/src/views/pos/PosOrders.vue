<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <h3 class="page-title">本班订单</h3>
        <span class="muted" v-if="shift">
          班次 {{ shift.shiftNo }} · 共 {{ shift.orderCount }} 单 · 实收 {{ yuan(shift.totalAmount) }} 元
        </span>
        <div class="spacer" />
        <el-button @click="load" :loading="loading">刷新</el-button>
      </div>

      <el-table :data="rows" size="small" v-loading="loading" height="calc(100vh - 200px)">
        <el-table-column prop="orderNo" label="订单号" width="180" />
        <el-table-column label="金额" width="110">
          <template #default="{ row }">{{ yuan(row.payableAmount) }}</template>
        </el-table-column>
        <el-table-column label="件数" width="80" prop="itemCount" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="tagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="220">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row)">详情</el-button>
            <el-button link type="warning" v-if="['PAID', 'COMPLETED'].includes(row.status)"
                       @click="refund(row)">退货</el-button>
            <el-button link type="danger" v-if="['PENDING_PAY', 'CREATED', 'HOLDING'].includes(row.status)"
                       @click="cancel(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!rows.length && !loading" description="本班还没有订单" />
    </div>

    <el-drawer v-model="drawer" title="订单详情" size="480px">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="订单号" :span="2">{{ detail.order.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusText(detail.order.status) }}</el-descriptions-item>
          <el-descriptions-item label="渠道">{{ detail.order.channel }}</el-descriptions-item>
          <el-descriptions-item label="原价">{{ yuan(detail.order.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="优惠">{{ yuan(detail.order.discountAmount) }}</el-descriptions-item>
          <el-descriptions-item label="应收">{{ yuan(detail.order.payableAmount) }}</el-descriptions-item>
          <el-descriptions-item label="实收">{{ yuan(detail.order.paidAmount) }}</el-descriptions-item>
        </el-descriptions>

        <h4>商品</h4>
        <el-table :data="detail.items" size="small">
          <el-table-column prop="skuName" label="商品" />
          <el-table-column prop="qty" label="数量" width="70" />
          <el-table-column label="金额" width="90">
            <template #default="{ row }">{{ yuan(row.netAmount) }}</template>
          </el-table-column>
        </el-table>

        <h4>优惠明细</h4>
        <div v-if="detail.discounts.length">
          <div v-for="d in detail.discounts" :key="d.seq" class="d-row">
            <span>{{ d.name }}</span>
            <span class="money">-{{ yuan(d.amount) }}</span>
          </div>
        </div>
        <p v-else class="muted">没有优惠</p>

        <h4>支付记录</h4>
        <el-table :data="detail.payments" size="small">
          <el-table-column prop="channelCode" label="渠道" width="90" />
          <el-table-column prop="status" label="状态" width="90" />
          <el-table-column prop="callbackCount" label="回调次数" width="90" />
          <el-table-column label="金额">
            <template #default="{ row }">{{ yuan(row.amount) }}</template>
          </el-table-column>
        </el-table>

        <h4>状态流转</h4>
        <el-timeline>
          <el-timeline-item v-for="(l, i) in detail.statusLogs" :key="i" :timestamp="fmt(l.createdAt)">
            {{ l.fromStatus || '—' }} → {{ l.toStatus }} {{ l.remark ? '（' + l.remark + '）' : '' }}
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../api'
import { yuan } from '../../session'

const rows = ref([])
const shift = ref(null)
const loading = ref(false)
const drawer = ref(false)
const detail = ref(null)

const STATUS = {
  CREATED: '已创建',
  HOLDING: '挂起中',
  PENDING_PAY: '待支付',
  PAID: '已支付',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  REFUNDED: '已退货'
}

function statusText(s) {
  return STATUS[s] || s
}

function tagType(s) {
  if (s === 'COMPLETED' || s === 'PAID') return 'success'
  if (s === 'CANCELLED' || s === 'REFUNDED') return 'info'
  if (s === 'PENDING_PAY') return 'warning'
  return ''
}

function fmt(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : ''
}

async function load() {
  loading.value = true
  try {
    shift.value = await api.shiftCurrent()
    if (shift.value) {
      rows.value = await api.shiftOrders(shift.value.id)
    } else {
      // 没开班也能看今天的单，否则收银员会以为系统坏了
      const page = await api.orderPage({ page: 1, size: 50 })
      rows.value = page || []
    }
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function showDetail(row) {
  try {
    detail.value = await api.orderDetail(row.orderNo)
    drawer.value = true
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function refund(row) {
  try {
    await ElMessageBox.confirm(
      `确认整单退货？金额 ${yuan(row.payableAmount)} 元将原路退回，库存会加回去。`,
      '退货确认',
      { type: 'warning' }
    )
    await api.orderRefund(row.orderNo, '收银台退货')
    ElMessage.success('已退货')
    load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '操作取消')
  }
}

async function cancel(row) {
  try {
    await ElMessageBox.confirm('取消后预占的库存会释放，确认取消？', '提示', { type: 'warning' })
    await api.orderCancel(row.orderNo, '收银台取消')
    ElMessage.success('已取消')
    load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '操作取消')
  }
}

onMounted(load)
</script>

<style scoped>
.page {
  height: 100%;
}

h4 {
  margin: 18px 0 8px;
  font-size: 14px;
}

.d-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  line-height: 1.9;
}
</style>
