<template>
  <div>
    <div class="page-card head-card">
      <div class="toolbar" style="margin: 0">
        <h3 class="page-title">销售报表</h3>
        <span class="muted">口径：销售额 = 应收金额（优惠后、退货前），只统计已支付/已完成订单。</span>
        <div class="spacer" />
        <el-radio-group v-model="days" size="small" @change="load">
          <el-radio-button :value="7">近 7 天</el-radio-button>
          <el-radio-button :value="14">近 14 天</el-radio-button>
          <el-radio-button :value="30">近 30 天</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <div class="kpi-grid">
      <div class="kpi-card">
        <div class="label">区间销售额</div>
        <div class="value">{{ yuan(range.netAmount) }}</div>
        <div class="hint">订单 {{ range.orderCount || 0 }} 笔</div>
      </div>
      <div class="kpi-card">
        <div class="label">客单价</div>
        <div class="value">{{ yuan(range.avgTicket) }}</div>
        <div class="hint">销售额 ÷ 订单数</div>
      </div>
      <div class="kpi-card">
        <div class="label">优惠合计</div>
        <div class="value">{{ yuan(range.discountAmount) }}</div>
        <div class="hint">活动 + 券 + 会员价 + 积分</div>
      </div>
      <div class="kpi-card">
        <div class="label">毛利</div>
        <div class="value">{{ yuan(range.grossProfit) }}</div>
        <div class="hint">成本 {{ yuan(range.costAmount) }}</div>
      </div>
      <div class="kpi-card">
        <div class="label">会员单占比</div>
        <div class="value">{{ memberPct }}</div>
        <div class="hint">{{ range.memberOrders || 0 }} / {{ range.orderCount || 0 }} 单</div>
      </div>
    </div>

    <div class="page-card">
      <h3 class="page-title">每日销售额与订单数</h3>
      <div ref="lineRef" class="chart"></div>
    </div>

    <div class="row-2">
      <div class="page-card">
        <h3 class="page-title">支付方式分布</h3>
        <div ref="pieRef" class="chart-sm"></div>
      </div>
      <div class="page-card">
        <h3 class="page-title">商品销售排行</h3>
        <el-table :data="topSku" size="small" height="260">
          <el-table-column type="index" label="#" width="46" />
          <el-table-column prop="skuName" label="商品" min-width="150" />
          <el-table-column prop="qty" label="数量" width="80" />
          <el-table-column label="金额" width="100">
            <template #default="{ row }">{{ yuan(row.amount) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <div class="page-card">
      <div class="toolbar">
        <h3 class="page-title">会员贡献对比</h3>
        <div class="spacer" />
        <el-button size="small" @click="rebuild" :loading="busy">刷新预聚合表</el-button>
      </div>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="会员订单">
          {{ member.member?.orders || 0 }} 单 / {{ yuan(member.member?.amount) }}
        </el-descriptions-item>
        <el-descriptions-item label="散客订单">
          {{ member.nonMember?.orders || 0 }} 单 / {{ yuan(member.nonMember?.amount) }}
        </el-descriptions-item>
      </el-descriptions>
      <p class="muted" style="margin-top: 10px">
        "会员单占比"高说明会员体系有效。这个数字不参与任何结算，只是给经理看的经营指标。
      </p>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { api } from '../../api'
import { yuan } from '../../session'

const days = ref(7)
const lineRef = ref(null)
const pieRef = ref(null)
const range = ref({})
const topSku = ref([])
const member = ref({})
const busy = ref(false)

let lineChart = null
let pieChart = null

const memberPct = computed(() => {
  const total = range.value.orderCount || 0
  const m = range.value.memberOrders || 0
  if (!total) return '0%'
  return ((m * 100) / total).toFixed(1) + '%'
})

const CHANNEL = { CASH: '现金', WECHAT: '微信', ALIPAY: '支付宝', WALLET: '储值卡', CARD: '银行卡' }

async function load() {
  try {
    const o = await api.reportOverview(days.value)
    range.value = o.kpi?.range || {}
    topSku.value = o.topSku || []
    renderLine(o.series || [])
    renderPie(o.channels || [])
    member.value = await api.reportMember(days.value)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function renderLine(rows) {
  if (!lineChart) lineChart = echarts.init(lineRef.value)
  lineChart.setOption({
    tooltip: {
      trigger: 'axis',
      valueFormatter: (v) => v
    },
    legend: { top: 0, icon: 'circle' },
    grid: { left: 60, right: 50, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: rows.map((r) => r.date) },
    yAxis: [
      { type: 'value', name: '金额(元)', axisLabel: { formatter: (v) => (v / 100).toFixed(0) } },
      { type: 'value', name: '订单数' }
    ],
    series: [
      {
        name: '销售额',
        type: 'line',
        smooth: true,
        data: rows.map((r) => r.amount),
        itemStyle: { color: '#c8102e' },
        areaStyle: { color: 'rgba(200,16,46,0.10)' }
      },
      {
        name: '订单数',
        type: 'bar',
        yAxisIndex: 1,
        barWidth: 12,
        data: rows.map((r) => r.orders),
        itemStyle: { color: 'rgba(31,39,51,0.22)' }
      }
    ]
  })
}

function renderPie(rows) {
  if (!pieChart) pieChart = echarts.init(pieRef.value)
  pieChart.setOption({
    tooltip: { trigger: 'item', valueFormatter: (v) => yuan(v) },
    legend: { bottom: 0, icon: 'circle' },
    series: [
      {
        type: 'pie',
        radius: ['45%', '68%'],
        center: ['50%', '44%'],
        data: rows.map((r) => ({ name: CHANNEL[r.channel] || r.channel, value: r.amount })),
        label: { formatter: '{b} {d}%' }
      }
    ]
  })
}

async function rebuild() {
  busy.value = true
  try {
    await api.reportRebuild(days.value)
    ElMessage.success('预聚合表已刷新')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    busy.value = false
  }
}

function resize() {
  lineChart?.resize()
  pieChart?.resize()
}

onMounted(() => {
  load()
  window.addEventListener('resize', resize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  lineChart?.dispose()
  pieChart?.dispose()
})
</script>

<style scoped>
.head-card {
  margin-bottom: 12px;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.kpi-card {
  background: #fff;
  border-radius: var(--pos-radius);
  padding: 14px 16px;
}

.kpi-card .label {
  font-size: 13px;
  color: var(--pos-muted);
}

.kpi-card .value {
  font-size: 22px;
  font-weight: 700;
  margin: 6px 0 2px;
  font-variant-numeric: tabular-nums;
}

.kpi-card .hint {
  font-size: 12px;
  color: var(--pos-muted);
}

.chart {
  height: 320px;
}

.chart-sm {
  height: 260px;
}

.row-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin: 12px 0;
}

@media (max-width: 1100px) {
  .row-2 {
    grid-template-columns: 1fr;
  }
}
</style>
