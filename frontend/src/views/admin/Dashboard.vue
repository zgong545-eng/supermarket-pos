<template>
  <div>
    <div class="kpi-grid">
      <div class="kpi-card" v-for="k in cards" :key="k.label">
        <div class="label">{{ k.label }}</div>
        <div class="value" :class="k.money ? 'money' : ''">{{ k.value }}</div>
        <div class="hint">{{ k.hint }}</div>
      </div>
    </div>

    <div class="row-2">
      <div class="page-card">
        <div class="toolbar">
          <h3 class="page-title">销售趋势</h3>
          <div class="spacer" />
          <el-radio-group v-model="days" size="small" @change="loadSeries">
            <el-radio-button :value="7">近 7 天</el-radio-button>
            <el-radio-button :value="14">近 14 天</el-radio-button>
            <el-radio-button :value="30">近 30 天</el-radio-button>
          </el-radio-group>
        </div>
        <div ref="lineRef" class="chart"></div>
      </div>

      <div class="page-card">
        <h3 class="page-title">支付方式</h3>
        <div ref="pieRef" class="chart"></div>
      </div>
    </div>

    <div class="row-2">
      <div class="page-card">
        <h3 class="page-title">今日分时客流</h3>
        <div ref="hourRef" class="chart-sm"></div>
      </div>

      <div class="page-card">
        <div class="toolbar">
          <h3 class="page-title">热销商品</h3>
          <div class="spacer" />
          <el-button size="small" @click="loadTop">刷新</el-button>
        </div>
        <el-table :data="top" size="small" height="260">
          <el-table-column type="index" width="50" label="#" />
          <el-table-column prop="skuName" label="商品" />
          <el-table-column prop="qty" label="数量" width="90" />
          <el-table-column label="金额" width="110">
            <template #default="{ row }">{{ yuan(row.amount) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <div class="page-card demo-card">
      <div>
        <h3 class="page-title">演示数据</h3>
        <p class="muted">
          课堂演示前点一下，60 个商品、3 个会员、2 个活动、30 天历史订单就都有了。
          接口只在 <code>pos.demo.enabled=true</code> 时可用，生产要关掉。
        </p>
      </div>
      <div class="demo-actions">
        <el-button @click="seedOrders" :loading="busy">生成 30 天历史订单</el-button>
        <el-button type="danger" plain @click="resetDemo" :loading="busy">重置全部演示数据</el-button>
        <el-button @click="rebuild" :loading="busy">刷新报表预聚合</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, computed } from 'vue'
import * as echarts from 'echarts'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../api'
import { yuan } from '../../session'

const lineRef = ref(null)
const pieRef = ref(null)
const hourRef = ref(null)
const days = ref(7)
const top = ref([])
const quick = ref({})
const kpi = ref({ today: {}, yesterday: {}, range: {} })
const busy = ref(false)

let lineChart = null
let pieChart = null
let hourChart = null

const cards = computed(() => [
  {
    label: '今日销售额',
    value: yuan(kpi.value.today?.netAmount || 0),
    hint: `昨日 ${yuan(kpi.value.yesterday?.netAmount || 0)}`
  },
  {
    label: '今日订单',
    value: `${kpi.value.today?.orderCount || 0} 单`,
    hint: `客单价 ${yuan(kpi.value.today?.avgTicket || 0)}`
  },
  {
    label: '近 ' + days.value + ' 天销售额',
    value: yuan(kpi.value.range?.netAmount || 0),
    hint: `毛利 ${yuan(kpi.value.range?.grossProfit || 0)}`
  },
  {
    label: '会员占比',
    value: pct(),
    hint: `会员单 ${kpi.value.range?.memberOrders || 0} / ${kpi.value.range?.orderCount || 0}`
  },
  {
    label: '库存预警',
    value: `${quick.value.warningCount ?? '-'} 个`,
    hint: '低于安全库存'
  },
  {
    label: '在售商品',
    value: `${quick.value.skuCount ?? '-'} 个`,
    hint: `会员 ${quick.value.memberCount ?? '-'} 人`
  }
])

function pct() {
  const total = kpi.value.range?.orderCount || 0
  const mem = kpi.value.range?.memberOrders || 0
  if (!total) return '0%'
  return ((mem * 100) / total).toFixed(1) + '%'
}

function resize() {
  lineChart?.resize()
  pieChart?.resize()
  hourChart?.resize()
}

async function loadSeries() {
  const o = await api.reportOverview(days.value)
  kpi.value = o.kpi
  top.value = o.topSku || []
  renderLine(o.series || [])
  renderPie(o.channels || [])
  renderHour(o.hourly || [])
}

function renderLine(rows) {
  if (!lineChart) lineChart = echarts.init(lineRef.value)
  lineChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 46, right: 18, top: 24, bottom: 30 },
    xAxis: { type: 'category', data: rows.map((r) => r.date), axisLine: { lineStyle: { color: '#dfe3e8' } } },
    yAxis: {
      type: 'value',
      axisLabel: { formatter: (v) => (v / 100).toFixed(0) },
      splitLine: { lineStyle: { color: '#f0f2f5' } }
    },
    series: [
      {
        name: '销售额（元）',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: rows.map((r) => r.amount),
        itemStyle: { color: '#c8102e' },
        areaStyle: { color: 'rgba(200,16,46,0.10)' }
      },
      {
        name: '订单数',
        type: 'bar',
        yAxisIndex: 0,
        barWidth: 10,
        data: rows.map((r) => r.orders * 100),
        itemStyle: { color: 'rgba(31,39,51,0.10)' },
        tooltip: { valueFormatter: () => '' }
      }
    ]
  })
}

function renderPie(rows) {
  if (!pieChart) pieChart = echarts.init(pieRef.value)
  const NAME = { CASH: '现金', WECHAT: '微信', ALIPAY: '支付宝', WALLET: '储值卡', CARD: '银行卡' }
  pieChart.setOption({
    tooltip: { trigger: 'item', valueFormatter: (v) => yuan(v) },
    legend: { bottom: 0, icon: 'circle' },
    series: [
      {
        type: 'pie',
        radius: ['42%', '66%'],
        center: ['50%', '44%'],
        data: rows.map((r) => ({ name: NAME[r.channel] || r.channel, value: r.amount })),
        label: { formatter: '{b}\n{d}%' }
      }
    ]
  })
}

function renderHour(rows) {
  if (!hourChart) hourChart = echarts.init(hourRef.value)
  hourChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 16, top: 20, bottom: 30 },
    xAxis: { type: 'category', data: rows.map((r) => r.hour) },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#f0f2f5' } } },
    series: [{ type: 'bar', data: rows.map((r) => r.orders), itemStyle: { color: '#3a4d63' }, barWidth: '55%' }]
  })
}

async function loadTop() {
  const o = await api.reportOverview(days.value)
  top.value = o.topSku || []
}

async function loadQuick() {
  try {
    quick.value = await api.dashboard()
  } catch (e) {
    quick.value = {}
  }
}

async function seedOrders() {
  busy.value = true
  try {
    const r = await api.demoSeedOrders(30, 300)
    ElMessage.success(`已生成 ${r.orders || r.count || '若干'} 笔历史订单`)
    await loadSeries()
    await loadQuick()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    busy.value = false
  }
}

async function resetDemo() {
  try {
    await ElMessageBox.confirm(
      '这会清空业务表（商品/会员/订单/库存）并重新灌演示数据，不可恢复。确认继续？',
      '重置演示数据',
      { type: 'warning' }
    )
    busy.value = true
    await api.demoReset()
    ElMessage.success('演示数据已重置')
    await loadSeries()
    await loadQuick()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message)
  } finally {
    busy.value = false
  }
}

async function rebuild() {
  busy.value = true
  try {
    await api.reportRebuild(30)
    ElMessage.success('预聚合表已刷新')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  await loadSeries()
  await loadQuick()
  window.addEventListener('resize', resize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  lineChart?.dispose()
  pieChart?.dispose()
  hourChart?.dispose()
})
</script>

<style scoped>
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.kpi-card {
  background: #fff;
  border-radius: var(--pos-radius);
  padding: 14px 16px;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04);
}

.kpi-card .label {
  font-size: 13px;
  color: var(--pos-muted);
}

.kpi-card .value {
  font-size: 24px;
  font-weight: 700;
  margin: 6px 0 2px;
  font-variant-numeric: tabular-nums;
}

.kpi-card .hint {
  font-size: 12px;
  color: var(--pos-muted);
}

.row-2 {
  display: grid;
  grid-template-columns: 1.55fr 1fr;
  gap: 12px;
  margin-bottom: 12px;
}

.chart {
  height: 300px;
}

.chart-sm {
  height: 210px;
}

.demo-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
}

.demo-card code {
  background: #f2f4f7;
  padding: 1px 4px;
  border-radius: 3px;
}

.demo-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

@media (max-width: 1100px) {
  .row-2 {
    grid-template-columns: 1fr;
  }
}
</style>
