<template>
  <div class="page-card">
    <el-tabs v-model="tab">
      <!-- ============ 库存总览 ============ -->
      <el-tab-pane label="库存总览" name="list">
        <div class="toolbar">
          <el-input v-model="keyword" placeholder="商品名 / 编码 / 条码" clearable style="width: 240px"
                    @keyup.enter="loadStock" />
          <el-button type="primary" @click="loadStock">查询</el-button>
          <div class="spacer" />
          <el-button v-if="hasPerm('stock:replenish')" type="primary" @click="openBill('replenish')">
            批量补货
          </el-button>
          <el-button v-if="hasPerm('stock:loss')" @click="openBill('loss')">报损</el-button>
          <el-button v-if="hasPerm('stock:check')" @click="openBill('check')">盘点</el-button>
        </div>

        <el-table :data="stockRows" v-loading="loading" size="small" border height="calc(100vh - 260px)">
          <el-table-column prop="skuCode" label="编码" width="120" />
          <el-table-column prop="skuName" label="商品" min-width="180" />
          <el-table-column prop="unit" label="单位" width="70" />
          <el-table-column label="账面" width="90" prop="onHand" />
          <el-table-column label="可售" width="90">
            <template #default="{ row }">
              <span :class="{ warn: row.available <= row.safetyStock }">{{ row.available }}</span>
            </template>
          </el-table-column>
          <el-table-column label="预占" width="90">
            <template #default="{ row }">
              <el-tooltip content="已下单未付款锁定的数量，付款后转为出库，取消则释放" placement="top">
                <span>{{ row.locked }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="安全库存" width="100" prop="safetyStock" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.available <= row.safetyStock" type="warning" size="small">需补货</el-tag>
              <el-tag v-else type="success" size="small">充足</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" v-if="hasPerm('stock:replenish')" @click="quickReplenish(row)">
                补货
              </el-button>
              <el-button link @click="showLogs(row)">流水</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ============ 预警 ============ -->
      <el-tab-pane label="补货预警" name="warning">
        <p class="muted">按缺口排序：可售 - 安全库存，缺口越大越靠前。行内"补货"直接填数量入库。</p>
        <el-table :data="warningRows" size="small" border v-loading="loading">
          <el-table-column prop="skuCode" label="编码" width="120" />
          <el-table-column prop="skuName" label="商品" min-width="200" />
          <el-table-column label="可售" width="90" prop="available" />
          <el-table-column label="安全库存" width="100" prop="safetyStock" />
          <el-table-column label="缺口" width="90">
            <template #default="{ row }">
              <span class="warn">{{ row.shortage }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="quickReplenish(row)">快捷补货</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ============ 单据 ============ -->
      <el-tab-pane label="出入库单据" name="bills">
        <div class="toolbar">
          <el-select v-model="billType" placeholder="全部类型" clearable style="width: 160px" @change="loadBills">
            <el-option label="补货入库" value="INBOUND" />
            <el-option label="报损出库" value="LOSS" />
            <el-option label="盘点调整" value="CHECK" />
          </el-select>
          <el-button @click="loadBills">刷新</el-button>
        </div>
        <el-table :data="bills" size="small" border>
          <el-table-column prop="billNo" label="单号" width="180" />
          <el-table-column label="类型" width="110">
            <template #default="{ row }">{{ typeText(row.type) }}</template>
          </el-table-column>
          <el-table-column prop="itemCount" label="条目" width="80" />
          <el-table-column label="数量合计" width="100" prop="totalQty" />
          <el-table-column prop="remark" label="备注" min-width="180" />
          <el-table-column label="时间" width="170">
            <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ============ 一致性自检 ============ -->
      <el-tab-pane label="一致性自检" name="check">
        <p class="muted">
          核心不变式：<code>账面 = 可售 + 预占</code>。任何时候这条不成立，说明有人绕过了库存服务直接改库。
          这个按钮就是答辩现场用来证明"并发下账没算错"的。
        </p>
        <el-button type="primary" @click="checkInvariants" :loading="checking">运行自检</el-button>
        <div v-if="invariants" class="inv-result">
          <el-alert :type="invariants.ok ? 'success' : 'error'" :closable="false" show-icon
                    :title="invariants.ok ? '全部通过：账面 = 可售 + 预占' : '发现不一致，请检查'" />
          <el-descriptions :column="3" border size="small" style="margin-top: 12px">
            <el-descriptions-item label="库存行数">{{ invariants.rowCount }}</el-descriptions-item>
            <el-descriptions-item label="违反 账面=可售+预占">{{ invariants.brokenInvariant }}</el-descriptions-item>
            <el-descriptions-item label="出现负数的行">{{ invariants.negativeRow }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- ============ 补货 / 报损 / 盘点 弹窗 ============ -->
    <el-dialog v-model="billVisible" :title="billTitle" width="640px">
      <p class="muted">{{ billHint }}</p>
      <el-table :data="billItems" size="small" border>
        <el-table-column label="商品" min-width="200">
          <template #default="{ row }">
            <span v-if="row.skuName">{{ row.skuName }}</span>
            <el-select v-else v-model="row.skuId" filterable placeholder="选择商品" style="width: 100%"
                       @change="(v) => onPickSku(row, v)">
              <el-option v-for="s in allSkus" :key="s.skuId" :label="`${s.skuName}（余 ${s.available}）`"
                         :value="s.skuId" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="当前数量" width="100">
          <template #default="{ row }">{{ row.currentQty ?? '-' }}</template>
        </el-table-column>
        <el-table-column :label="billMode === 'check' ? '实盘数量' : '数量'" width="140">
          <template #default="{ row }">
            <el-input-number v-model="row.qty" :min="0" size="small" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="" width="60">
          <template #default="{ $index }">
            <el-button link type="danger" @click="billItems.splice($index, 1)">删</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button size="small" style="margin-top: 8px" @click="addBillRow">+ 添加一行</el-button>
      <el-input v-model="billRemark" placeholder="备注（可选）" style="margin-top: 10px" />
      <template #footer>
        <el-button @click="billVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitBill">提交（提交即生效）</el-button>
      </template>
    </el-dialog>

    <!-- ============ 流水 ============ -->
    <el-drawer v-model="logVisible" :title="`库存流水 · ${logSku?.skuName || ''}`" size="520px">
      <el-table :data="logs" size="small">
        <el-table-column prop="changeType" label="类型" width="110" />
        <el-table-column prop="qty" label="变动" width="70" />
        <el-table-column label="变动前 → 后" width="130">
          <template #default="{ row }">{{ row.beforeOnHand }} → {{ row.afterOnHand }}</template>
        </el-table-column>
        <el-table-column prop="bizNo" label="关联单号" min-width="150" />
        <el-table-column label="时间" width="150">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api'
import { hasPerm, yuan } from '../../session'

const tab = ref('list')
const keyword = ref('')
const loading = ref(false)
const saving = ref(false)
const checking = ref(false)

const stockRows = ref([])
const warningRows = ref([])
const bills = ref([])
const billType = ref(null)
const allSkus = ref([])

const billVisible = ref(false)
const billMode = ref('replenish')
const billItems = ref([])
const billRemark = ref('')

const logVisible = ref(false)
const logSku = ref(null)
const logs = ref([])
const invariants = ref(null)

const billTitle = ref('')
const billHint = ref('')

const TITLES = {
  replenish: ['补货入库', '提交即生效：写单据 + 加库存 + 留流水，三件事在一个事务里。没有审批流，因为演示版不接采购系统。'],
  loss: ['报损出库', '破损、过期走的流程。报损会直接减少账面库存，并留下"为什么少"的记录。'],
  check: ['库存盘点', '填实盘数量，系统算差异并调整。差异会写进流水，方便事后复盘。']
}

function fmt(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : ''
}

function typeText(t) {
  return { INBOUND: '补货入库', LOSS: '报损出库', CHECK: '盘点调整' }[t] || t
}

async function loadStock() {
  loading.value = true
  try {
    stockRows.value = await api.stockPage({ keyword: keyword.value || '', page: 1, size: 200 })
    allSkus.value = stockRows.value
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function loadWarning() {
  try {
    warningRows.value = await api.stockWarning(50)
  } catch (e) {
    warningRows.value = []
  }
}

async function loadBills() {
  try {
    bills.value = await api.stockBills({ type: billType.value || undefined, page: 1, size: 50 })
  } catch (e) {
    bills.value = []
  }
}

function openBill(mode) {
  billMode.value = mode
  billTitle.value = TITLES[mode][0]
  billHint.value = TITLES[mode][1]
  billItems.value = [{ skuId: null, skuName: '', qty: mode === 'check' ? 0 : 10, currentQty: null }]
  billRemark.value = ''
  billVisible.value = true
}

function quickReplenish(row) {
  billMode.value = 'replenish'
  billTitle.value = '快捷补货'
  billHint.value = `补货 ${row.skuName}：提交后库存立刻增加，并生成一条入库单据。`
  billItems.value = [{
    skuId: row.skuId,
    skuName: row.skuName,
    qty: Math.max(row.shortage || row.safetyStock - row.available || 10, 1),
    currentQty: row.available
  }]
  billRemark.value = '库存预警快捷补货'
  billVisible.value = true
}

function addBillRow() {
  billItems.value.push({ skuId: null, skuName: '', qty: 10, currentQty: null })
}

function onPickSku(row, skuId) {
  const s = allSkus.value.find((x) => x.skuId === skuId)
  if (s) {
    row.skuName = s.skuName
    row.currentQty = s.available
    if (billMode.value === 'check') row.qty = s.onHand
  }
}

async function submitBill() {
  const items = billItems.value
    .filter((r) => r.skuId && r.qty >= 0)
    .map((r) => ({ skuId: r.skuId, qty: r.qty }))
  if (!items.length) {
    ElMessage.warning('至少选一个商品')
    return
  }
  saving.value = true
  try {
    if (billMode.value === 'replenish') await api.stockReplenish(items, billRemark.value)
    else if (billMode.value === 'loss') await api.stockLoss(items, billRemark.value)
    else await api.stockCheck(items, billRemark.value)
    ElMessage.success('已生效')
    billVisible.value = false
    await loadStock()
    await loadWarning()
    await loadBills()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function showLogs(row) {
  logSku.value = row
  logVisible.value = true
  try {
    logs.value = await api.stockLogs(row.skuId)
  } catch (e) {
    logs.value = []
  }
}

async function checkInvariants() {
  checking.value = true
  try {
    invariants.value = await api.stockInvariants()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    checking.value = false
  }
}

watch(tab, (v) => {
  if (v === 'warning') loadWarning()
  if (v === 'bills') loadBills()
})

onMounted(() => {
  loadStock()
  loadWarning()
})
</script>

<style scoped>
.warn {
  color: #e6a23c;
  font-weight: 600;
}

.inv-result {
  margin-top: 14px;
}

code {
  background: #f2f4f7;
  padding: 1px 4px;
  border-radius: 3px;
}
</style>
