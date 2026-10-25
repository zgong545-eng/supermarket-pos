<template>
  <div class="page-card">
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="订单号 / 手机号 / 会员名" clearable style="width: 240px"
                @keyup.enter="load" />
      <el-select v-model="status" placeholder="全部状态" clearable style="width: 140px" @change="load">
        <el-option v-for="(v, k) in STATUS" :key="k" :label="v" :value="k" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
      <div class="spacer" />
      <span class="muted">本月共 {{ total }} 笔</span>
    </div>

    <el-table :data="rows" v-loading="loading" size="small" border>
      <el-table-column prop="orderNo" label="订单号" width="180" />
      <el-table-column prop="memberName" label="会员" width="110">
        <template #default="{ row }">{{ row.memberName || '散客' }}</template>
      </el-table-column>
      <el-table-column label="渠道" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.channel === 'H5' ? 'warning' : 'info'">{{ row.channel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="原价" width="100">
        <template #default="{ row }">{{ yuan(row.totalAmount) }}</template>
      </el-table-column>
      <el-table-column label="优惠" width="100">
        <template #default="{ row }">-{{ yuan(row.discountAmount) }}</template>
      </el-table-column>
      <el-table-column label="应收" width="100">
        <template #default="{ row }">{{ yuan(row.payableAmount) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="tagType(row.status)">{{ STATUS[row.status] || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDetail(row)">详情</el-button>
          <el-button link type="danger" v-if="hasPerm('order:refund') && ['PAID','COMPLETED'].includes(row.status)"
                     @click="refund(row)">退货</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination v-model:current-page="page" :page-size="size" :total="total"
                     layout="total, prev, pager, next" background @current-change="load" />
    </div>

    <el-drawer v-model="drawer" title="订单详情" size="560px">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="订单号" :span="2">{{ detail.order.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ STATUS[detail.order.status] }}</el-descriptions-item>
          <el-descriptions-item label="渠道">{{ detail.order.channel }}</el-descriptions-item>
          <el-descriptions-item label="原价">{{ yuan(detail.order.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="优惠">{{ yuan(detail.order.discountAmount) }}</el-descriptions-item>
          <el-descriptions-item label="应收">{{ yuan(detail.order.payableAmount) }}</el-descriptions-item>
          <el-descriptions-item label="实收">{{ yuan(detail.order.paidAmount) }}</el-descriptions-item>
        </el-descriptions>

        <h4>商品明细</h4>
        <el-table :data="detail.items" size="small" border>
          <el-table-column prop="skuName" label="商品" min-width="160" />
          <el-table-column label="单价" width="90">
            <template #default="{ row }">{{ yuan(row.salePrice) }}</template>
          </el-table-column>
          <el-table-column prop="qty" label="数量" width="70" />
          <el-table-column label="优惠" width="90">
            <template #default="{ row }">{{ yuan(row.discountAmount) }}</template>
          </el-table-column>
          <el-table-column label="小计" width="90">
            <template #default="{ row }">{{ yuan(row.netAmount) }}</template>
          </el-table-column>
        </el-table>

        <h4>优惠来源与分摊</h4>
        <div v-for="d in detail.discounts" :key="d.seq" class="d-block">
          <div class="d-head">
            <span>{{ d.name }}（{{ d.sourceType }}）</span>
            <span class="money">-{{ yuan(d.amount) }}</span>
          </div>
          <div class="muted alloc">分摊到各行：{{ d.allocJson }}</div>
        </div>
        <p v-if="!detail.discounts.length" class="muted">本单没有优惠</p>

        <h4>支付与回调</h4>
        <el-table :data="detail.payments" size="small" border>
          <el-table-column prop="payNo" label="支付单号" width="170" />
          <el-table-column prop="channelCode" label="渠道" width="90" />
          <el-table-column prop="status" label="状态" width="90" />
          <el-table-column prop="callbackCount" label="回调次数" width="90" />
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
import { hasPerm, yuan } from '../../session'

const STATUS = {
  CREATED: '已创建',
  HOLDING: '挂起中',
  PENDING_PAY: '待支付',
  PAID: '已支付',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  REFUNDED: '已退货'
}

const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const status = ref(null)
const loading = ref(false)
const drawer = ref(false)
const detail = ref(null)

function tagType(s) {
  if (['PAID', 'COMPLETED'].includes(s)) return 'success'
  if (['CANCELLED', 'REFUNDED'].includes(s)) return 'info'
  if (s === 'PENDING_PAY') return 'warning'
  return ''
}

function fmt(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : ''
}

async function load() {
  loading.value = true
  try {
    rows.value = await api.orderPage({
      keyword: keyword.value || undefined,
      status: status.value || undefined,
      page: page.value,
      size: size.value
    })
    total.value = rows.value.length
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
    await ElMessageBox.confirm(`整单退货 ${yuan(row.payableAmount)} 元？库存会加回、积分会扣回、券会退回。`,
      '退货确认', { type: 'warning' })
    await api.orderRefund(row.orderNo, '后台退货')
    ElMessage.success('已退货')
    load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '已取消')
  }
}

onMounted(load)
</script>

<style scoped>
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

h4 {
  margin: 18px 0 8px;
  font-size: 14px;
}

.d-block {
  border-left: 3px solid var(--pos-primary);
  padding-left: 10px;
  margin-bottom: 8px;
}

.d-head {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
}

.alloc {
  font-family: monospace;
  font-size: 11px;
  word-break: break-all;
}
</style>
