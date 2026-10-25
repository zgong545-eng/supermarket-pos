<template>
  <div class="page">
    <div class="page-card shift-card">
      <!-- 未开班：录备用金 -->
      <template v-if="!current">
        <h3 class="page-title">开班</h3>
        <p class="muted">
          开班要录备用金——这是抽屉里原有的钱，用于给顾客找零。
          交班时系统会用「备用金 + 本班现金收入」跟你数的钱对比，差多少就记多少。
        </p>
        <el-form label-width="110px" style="max-width: 460px; margin-top: 16px">
          <el-form-item label="备用金（元）">
            <el-input-number v-model="openingYuan" :min="0" :step="50" :precision="2" style="width: 200px" />
          </el-form-item>
          <el-form-item label="收银机编号">
            <el-input v-model="deviceCode" placeholder="如 POS-01" style="width: 200px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="doOpen" :loading="busy">确认开班</el-button>
          </el-form-item>
        </el-form>
      </template>

      <!-- 当班中 -->
      <template v-else>
        <div class="toolbar">
          <h3 class="page-title">当班中 · {{ current.shiftNo }}</h3>
          <el-tag type="success" effect="dark">OPEN</el-tag>
          <div class="spacer" />
          <el-button @click="load">刷新</el-button>
        </div>

        <div class="kpi-row">
          <div class="kpi">
            <div class="kpi-label">本班订单</div>
            <div class="kpi-value">{{ current.orderCount }} 单</div>
          </div>
          <div class="kpi">
            <div class="kpi-label">本班销售额</div>
            <div class="kpi-value money">{{ yuan(current.totalAmount) }}</div>
          </div>
          <div class="kpi">
            <div class="kpi-label">现金收入（净）</div>
            <div class="kpi-value money">{{ yuan(current.cashAmount) }}</div>
          </div>
          <div class="kpi">
            <div class="kpi-label">系统应收现金</div>
            <div class="kpi-value money">{{ yuan(current.systemCash) }}</div>
            <div class="muted">含备用金 {{ yuan(current.openingCash) }}</div>
          </div>
        </div>

        <h4>本班支付方式</h4>
        <el-table :data="current.channels || []" size="small" style="max-width: 520px">
          <el-table-column prop="channel" label="渠道" width="120" />
          <el-table-column prop="cnt" label="笔数" width="90" />
          <el-table-column label="金额">
            <template #default="{ row }">{{ yuan(row.amount) }}</template>
          </el-table-column>
        </el-table>

        <h4>交班</h4>
        <el-form label-width="130px" style="max-width: 520px">
          <el-form-item label="实点现金（元）">
            <el-input-number v-model="closingYuan" :min="0" :step="50" :precision="2" style="width: 200px" />
            <div class="muted" style="margin-left: 10px">
              预计 {{ yuan(expectedCash) }} 元，差额
              <b :class="diffClass"> {{ yuan(closingYuan * 100 - expectedCash) }} </b>
            </div>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="remark" placeholder="如：交班给晚班" style="width: 260px" />
          </el-form-item>
          <el-form-item>
            <el-button type="danger" @click="doClose" :loading="busy">确认交班</el-button>
          </el-form-item>
        </el-form>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../api'
import { yuan } from '../../session'

const current = ref(null)
const openingYuan = ref(200)
const closingYuan = ref(0)
const deviceCode = ref('POS-01')
const remark = ref('')
const busy = ref(false)

const expectedCash = computed(() => current.value?.systemCash || 0)
const diff = computed(() => Math.round(closingYuan.value * 100) - expectedCash.value)
const diffClass = computed(() => (diff.value === 0 ? 'ok' : diff.value > 0 ? 'long' : 'short'))

async function load() {
  try {
    current.value = await api.shiftCurrent()
    if (current.value) {
      closingYuan.value = Number((current.value.systemCash / 100).toFixed(2))
    }
  } catch (e) {
    current.value = null
  }
}

async function doOpen() {
  busy.value = true
  try {
    await api.shiftOpen(Math.round(openingYuan.value * 100), deviceCode.value)
    ElMessage.success('开班成功')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    busy.value = false
  }
}

async function doClose() {
  const diffYuan = (diff.value / 100).toFixed(2)
  try {
    await ElMessageBox.confirm(
      `实点 ${closingYuan.value.toFixed(2)} 元，系统应收 ${(expectedCash.value / 100).toFixed(2)} 元，` +
        `差额 ${diffYuan} 元。${diff.value === 0 ? '账实相符。' : '差额会记入长短款，账目不会被冲销。'}`,
      '交班确认',
      { type: 'warning' }
    )
    await api.shiftClose(Math.round(closingYuan.value * 100), remark.value)
    ElMessage.success('交班完成')
    remark.value = ''
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

.shift-card {
  max-width: 900px;
}

.kpi-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 10px;
  margin: 12px 0 18px;
}

.kpi {
  border: 1px solid var(--pos-border);
  border-radius: 8px;
  padding: 12px;
}

.kpi-label {
  font-size: 12px;
  color: var(--pos-muted);
}

.kpi-value {
  font-size: 20px;
  font-weight: 700;
  margin-top: 4px;
}

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
}
</style>
