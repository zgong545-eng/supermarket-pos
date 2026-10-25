<template>
  <div class="cashier">
    <!-- ============ 左：找货 ============ -->
    <section class="left">
      <div class="scan-bar">
        <el-input v-model="barcode" size="large" placeholder="扫条码 / 输入条码后回车"
                  :prefix-icon="Aim" clearable @keyup.enter="scan" />
        <el-input v-model="keyword" size="large" placeholder="商品名搜索" :prefix-icon="Search"
                  clearable @keyup.enter="search" style="margin-left: 8px" />
        <el-button size="large" @click="search">查询</el-button>
      </div>

      <div class="grid">
        <div v-for="g in goodsList" :key="g.id" class="goods-card" @click="addGoods(g)">
          <div class="name">{{ g.name }}</div>
          <div class="spec muted">{{ g.spec || '' }} {{ g.unit }}</div>
          <div class="row">
            <span class="money">{{ yuan(priceOf(g)) }}</span>
            <span class="stock" :class="{ low: g.stock < 10 }">余 {{ g.stock }}</span>
          </div>
        </div>
        <el-empty v-if="!goodsList.length" description="没有匹配商品，试试别的关键词" />
      </div>
    </section>

    <!-- ============ 右：购物车 ============ -->
    <section class="right">
      <div class="cart-head">
        <div class="member-line">
          <el-input v-model="memberKey" size="default" placeholder="会员手机号/卡号后四位"
                    :prefix-icon="UserFilled" clearable @keyup.enter="lookupMember">
            <template #append><el-button @click="lookupMember">识别</el-button></template>
          </el-input>
          <div v-if="member" class="member-tag">
            <el-tag type="success" effect="dark">{{ member.levelName }}</el-tag>
            <span>{{ member.name }}</span>
            <span class="muted">积分 {{ member.points }} · 储值 {{ yuan(member.balance) }}</span>
            <el-button link type="danger" size="small" @click="clearMember">取消</el-button>
          </div>
        </div>
      </div>

      <div class="cart-body">
        <table class="cart-table">
          <thead>
            <tr>
              <th style="width: 38%">商品</th>
              <th>单价</th>
              <th style="width: 120px">数量</th>
              <th>小计</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(line, idx) in cart" :key="line.key">
              <td>
                <div class="ci-name">{{ line.name }}</div>
                <div class="muted" v-if="line.isWeighed">{{ line.weight }} kg</div>
              </td>
              <td class="money">{{ yuan(line.price) }}</td>
              <td>
                <div class="qty">
                  <el-button size="small" circle @click="changeQty(idx, -1)">-</el-button>
                  <span class="num">{{ line.qty }}</span>
                  <el-button size="small" circle @click="changeQty(idx, 1)">+</el-button>
                </div>
              </td>
              <td class="money">{{ yuan(line.price * line.qty) }}</td>
              <td><el-button link type="danger" @click="removeLine(idx)">删</el-button></td>
            </tr>
            <tr v-if="!cart.length">
              <td colspan="5" class="empty">购物车是空的，扫码或点左边商品加进来</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="cart-foot">
        <div class="discount-box" v-if="calc.discounts?.length">
          <div v-for="d in calc.discounts" :key="d.sourceNo + d.name" class="d-line">
            <span>{{ d.name }}</span>
            <span class="money neg">-{{ yuan(d.amount) }}</span>
          </div>
        </div>

        <div class="opt-row">
          <el-select v-model="couponIds" multiple collapse-tags placeholder="选优惠券" size="small"
                     style="flex: 1" :disabled="!member">
            <el-option v-for="c in usableCoupons" :key="c.id"
                       :label="`${c.name}（-${yuan(c.amount)}）`" :value="c.id" />
          </el-select>
          <el-input-number v-model="usePoints" :min="0" :max="member?.points || 0" :step="100"
                           size="small" controls-position="right" style="width: 130px"
                           :disabled="!member" />
          <span class="muted">积分</span>
        </div>

        <div class="sum-row">
          <div class="sum-left">
            <div class="muted">原价 {{ yuan(calc.totalAmount) }} ／ 优惠 {{ yuan(calc.totalDiscount) }}</div>
            <div class="qty-note">{{ cart.length }} 种商品，共 {{ totalQty }} 件</div>
          </div>
          <div class="sum-right">
            <div class="muted">应收</div>
            <div class="money-lg">{{ yuan(calc.payableAmount) }}</div>
          </div>
        </div>

        <div class="btn-row">
          <el-button size="large" @click="doHold" :disabled="!cart.length">挂单</el-button>
          <el-button size="large" @click="openHoldList">
            取单<el-badge v-if="holdCount" :value="holdCount" class="badge" />
          </el-button>
          <el-button size="large" @click="clearCart" :disabled="!cart.length">清空</el-button>
          <el-button type="primary" size="large" class="pay-btn" :disabled="!cart.length"
                     :loading="creating" @click="checkout">
            结 算
          </el-button>
        </div>
      </div>
    </section>

    <!-- ============ 支付弹窗 ============ -->
    <el-dialog v-model="payVisible" title="收款" width="560px" :close-on-click-modal="false">
      <div class="pay-head">
        <span class="muted">本单应收</span>
        <span class="money-lg">{{ yuan(payAmount) }}</span>
      </div>
      <div class="pay-amount-hint" v-if="currentOrder">
        订单号 <b>{{ currentOrder.orderNo }}</b>
      </div>

      <el-radio-group v-model="channel" class="pay-methods">
        <el-radio-button value="CASH">现金</el-radio-button>
        <el-radio-button value="WECHAT">微信</el-radio-button>
        <el-radio-button value="ALIPAY">支付宝</el-radio-button>
        <el-radio-button value="WALLET" :disabled="!member">储值卡</el-radio-button>
      </el-radio-group>

      <!-- 现金 -->
      <div v-if="channel === 'CASH'" class="cash-box">
        <div class="quick-cash">
          <el-button v-for="q in quickCash" :key="q" size="small" @click="tendered = q">
            {{ yuan(q) }}
          </el-button>
        </div>
        <el-input-number v-model="tenderedYuan" :min="0" :step="1" :precision="2"
                         size="large" style="width: 100%" />
        <div class="change">
          找零 <span class="money-lg">{{ yuan(Math.max(tendered - payAmount, 0)) }}</span>
        </div>
      </div>

      <!-- 扫码支付：演示面板 -->
      <div v-else-if="channel !== 'WALLET'" class="mock-box">
        <el-alert type="info" :closable="false" show-icon
                  title="演示环境没有真实支付通道"
                  description="点下面的按钮会走真实的回调逻辑（含幂等）。连续点两次可以演示“重复回调被忽略”。" />
        <div class="mock-actions">
          <el-button type="primary" @click="payMock(true, 0)">支付成功</el-button>
          <el-button @click="payMock(false, 0)">支付失败</el-button>
          <el-button @click="payMock(true, 2000)">模拟超时 2 秒</el-button>
          <el-button type="warning" plain @click="payTwice">连点两次（验幂等）</el-button>
        </div>
        <div v-if="payLog.length" class="pay-log">
          <div v-for="(l, i) in payLog" :key="i" class="log-line" :class="l.type">{{ l.text }}</div>
        </div>
      </div>

      <!-- 储值卡 -->
      <div v-else class="cash-box">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="会员">{{ member?.name }}</el-descriptions-item>
          <el-descriptions-item label="储值余额">{{ yuan(member?.balance) }}</el-descriptions-item>
          <el-descriptions-item label="本单扣款">{{ yuan(payAmount) }}</el-descriptions-item>
        </el-descriptions>
        <div class="muted" style="margin-top: 8px">
          余额不足会整单回滚，支付单保持待支付，可以换方式重收。
        </div>
      </div>

      <template #footer>
        <el-button @click="payVisible = false">取消</el-button>
        <el-button v-if="channel === 'CASH'" type="primary" :loading="paying" @click="payCash">
          收款并完成
        </el-button>
        <el-button v-else-if="channel === 'WALLET'" type="primary" :loading="paying" @click="payWallet">
          确认扣款
        </el-button>
      </template>
    </el-dialog>

    <!-- ============ 挂单列表 ============ -->
    <el-dialog v-model="holdVisible" title="挂起的单子" width="520px">
      <el-table :data="holdList" size="small">
        <el-table-column prop="holdNo" label="挂单号" width="170" />
        <el-table-column label="挂起时长">
          <template #default="{ row }">{{ row.minutes }} 分钟</template>
        </el-table-column>
        <el-table-column label="金额">
          <template #default="{ row }">{{ yuan(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button link type="primary" @click="resumeHold(row)">取出</el-button>
            <el-button link type="danger" @click="cancelHold(row)">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!holdList.length" description="没有挂起的单子" />
    </el-dialog>

    <!-- ============ 小票 ============ -->
    <el-dialog v-model="receiptVisible" title="收款成功" width="420px">
      <div class="receipt-print" id="receipt">
        <div class="r-title">会员仓储超市</div>
        <div class="r-sub">收银小票</div>
        <div class="r-line">单号：{{ receipt.orderNo }}</div>
        <div class="r-line">时间：{{ nowText }}</div>
        <div class="r-line">收银员：{{ session.user?.realName }}</div>
        <div class="r-hr"></div>
        <div v-for="it in receipt.items" :key="it.skuName" class="r-item">
          <span>{{ it.skuName }} × {{ it.qty }}</span>
          <span>{{ yuan(it.netAmount) }}</span>
        </div>
        <div class="r-hr"></div>
        <div class="r-line"><span>合计</span><span>{{ yuan(receipt.totalAmount) }}</span></div>
        <div class="r-line"><span>优惠</span><span>-{{ yuan(receipt.discountAmount) }}</span></div>
        <div class="r-line r-strong"><span>实收</span><span>{{ yuan(receipt.paidAmount) }}</span></div>
        <div class="r-line" v-if="receipt.changeAmount > 0">
          <span>找零</span><span>{{ yuan(receipt.changeAmount) }}</span>
        </div>
        <div class="r-line" v-if="member"><span>会员</span><span>{{ member.name }}</span></div>
        <div class="r-hr"></div>
        <div class="r-sub">谢谢光临，欢迎再来</div>
      </div>
      <template #footer>
        <el-button @click="printReceipt">打印</el-button>
        <el-button type="primary" @click="newOrder">下一单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Aim, Search, UserFilled } from '@element-plus/icons-vue'
import { api } from '../../api'
import { session, yuan, toCents } from '../../session'

const barcode = ref('')
const keyword = ref('')
const goodsList = ref([])
const memberKey = ref('')
const member = ref(null)
const usableCoupons = ref([])
const couponIds = ref([])
const usePoints = ref(0)
const cart = ref([])
const holdList = ref([])
const holdCount = ref(0)
const calc = ref({ totalAmount: 0, totalDiscount: 0, payableAmount: 0, discounts: [], lines: [] })

const creating = ref(false)
const payVisible = ref(false)
const paying = ref(false)
const channel = ref('CASH')
const payAmount = ref(0)
const currentOrder = ref(null)
const tenderedYuan = ref(0)
const payLog = ref([])
const holdVisible = ref(false)
const receiptVisible = ref(false)
const receipt = ref({ items: [] })
const nowText = ref('')
let lineSeq = 0

const quickCash = [500, 1000, 2000, 5000, 10000, 20000]
const tendered = computed(() => toCents(tenderedYuan.value))
const totalQty = computed(() => cart.value.reduce((s, l) => s + l.qty, 0))

function priceOf(g) {
  // 会员有会员价就显示会员价，否则标准价
  return member.value && g.memberPrice ? g.memberPrice : g.standardPrice
}

// ---------------------------------------------------------------
// 检索
// ---------------------------------------------------------------
async function search() {
  try {
    // 商品基础信息 + 可售库存分两次取，再按 skuId 合并：
    // 商品服务不返回库存（那属于库存模块），收银台要显示"余几件"必须自己拼。
    const [res, stockRows] = await Promise.all([
      api.goodsPage({ keyword: keyword.value || undefined, page: 1, size: 60, status: 1 }),
      api.stockPage({ keyword: keyword.value || '', page: 1, size: 500 }).catch(() => [])
    ])
    const stockMap = new Map((stockRows || []).map((r) => [r.skuId, r.available]))
    goodsList.value = (res.records || []).map((g) => ({
      id: g.id,
      name: g.name,
      spec: g.spec,
      unit: g.unit,
      standardPrice: g.standardPrice,
      memberPrice: g.memberPrice,
      barcode: g.barcode,
      isWeighed: g.weightType === 'WEIGHT',
      stock: stockMap.has(g.id) ? stockMap.get(g.id) : 0
    }))
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function scan() {
  const code = barcode.value.trim()
  if (!code) return
  try {
    const g = await api.goodsByBarcode(code)
    addGoods({
      id: g.id,
      name: g.name,
      spec: g.spec,
      unit: g.unit,
      standardPrice: g.standardPrice,
      memberPrice: g.memberPrice,
      isWeighed: g.weightType === 'WEIGHT',
      stock: g.availableStock ?? 999
    })
    barcode.value = ''
  } catch (e) {
    ElMessage.error('没找到这个条码：' + code)
  }
}

async function addGoods(g) {
  if (g.isWeighed) {
    try {
      const { value } = await ElMessageBox.prompt('输入重量（kg）', g.name, {
        inputValue: '1.0',
        inputPattern: /^\d+(\.\d{1,3})?$/,
        inputErrorMessage: '请输入数字重量'
      })
      const weight = Number(value)
      cart.value.push({
        key: ++lineSeq, skuId: g.id, name: g.name, unit: g.unit, price: priceOf(g),
        qty: Math.max(1, Math.round(weight * 1000)), weight, isWeighed: true
      })
    } catch (e) {
      /* 取消 */
    }
    recalc()
    return
  }

  const exist = cart.value.find((l) => l.skuId === g.id && !l.isWeighed)
  if (exist) {
    exist.qty += 1
  } else {
    cart.value.push({
      key: ++lineSeq, skuId: g.id, name: g.name, unit: g.unit, price: priceOf(g),
      qty: 1, weight: null, isWeighed: false
    })
  }
  recalc()
}

function changeQty(idx, delta) {
  const line = cart.value[idx]
  line.qty += delta
  if (line.qty <= 0) cart.value.splice(idx, 1)
  recalc()
}

function removeLine(idx) {
  cart.value.splice(idx, 1)
  recalc()
}

function clearCart() {
  cart.value = []
  couponIds.value = []
  usePoints.value = 0
  calc.value = { totalAmount: 0, totalDiscount: 0, payableAmount: 0, discounts: [], lines: [] }
}

// ---------------------------------------------------------------
// 算钱：每次都问后端，前端不自己算优惠
// ---------------------------------------------------------------
let calcTimer = null
function recalc() {
  clearTimeout(calcTimer)
  calcTimer = setTimeout(doCalc, 120)
}

async function doCalc() {
  if (!cart.value.length) {
    calc.value = { totalAmount: 0, totalDiscount: 0, payableAmount: 0, discounts: [], lines: [] }
    return
  }
  try {
    const res = await api.promotionCalculate(buildCart())
    calc.value = res
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function buildCart() {
  return {
    items: cart.value.map((l) => ({
      skuId: l.skuId,
      qty: l.isWeighed ? Math.round(l.weight * 1000) : l.qty,
      weight: l.isWeighed ? l.weight : null
    })),
    memberId: member.value?.id || null,
    memberCouponIds: couponIds.value,
    usePoints: usePoints.value
  }
}

watch([couponIds, usePoints], () => recalc(), { deep: true })

// ---------------------------------------------------------------
// 会员
// ---------------------------------------------------------------
async function lookupMember() {
  const key = memberKey.value.trim()
  if (!key) return
  try {
    const m = await api.memberLookup(key)
    member.value = m
    usableCoupons.value = await api.memberCoupons(m.id, true)
    ElMessage.success(`已识别会员：${m.name}（${m.levelName}）`)
    recalc()
  } catch (e) {
    member.value = null
    ElMessage.error(e.message || '没有这个会员')
  }
}

function clearMember() {
  member.value = null
  usableCoupons.value = []
  couponIds.value = []
  usePoints.value = 0
  memberKey.value = ''
  recalc()
}

// ---------------------------------------------------------------
// 挂单
// ---------------------------------------------------------------
async function doHold() {
  try {
    const r = await api.holdCreate(buildCart())
    ElMessage.success(`已挂单 ${r.holdNo}`)
    clearCart()
    loadHolds()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function openHoldList() {
  await loadHolds()
  holdVisible.value = true
}

async function loadHolds() {
  try {
    holdList.value = await api.holdList()
    holdCount.value = holdList.value.length
  } catch (e) {
    holdList.value = []
  }
}

async function resumeHold(row) {
  try {
    const r = await api.holdResume(row.holdNo)
    const snap = r.snapshot || {}
    cart.value = []
    lineSeq = 0
    for (const it of snap.items || []) {
      cart.value.push({
        key: ++lineSeq,
        skuId: it.skuId,
        name: it.skuName,
        unit: it.unit,
        price: it.salePrice,
        qty: it.qty,
        weight: it.weight,
        isWeighed: it.weight !== null && it.weight !== undefined
      })
    }
    if (snap.memberId) {
      try {
        member.value = await api.memberLookup(String(snap.memberId))
        usableCoupons.value = await api.memberCoupons(snap.memberId, true)
      } catch (e) {
        member.value = null
      }
    }
    holdVisible.value = false
    recalc()
    ElMessage.success('已取回购物车（金额会重新计算）')
    loadHolds()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function cancelHold(row) {
  try {
    await api.holdCancel(row.holdNo)
    ElMessage.success('已作废')
    loadHolds()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

// ---------------------------------------------------------------
// 结算
// ---------------------------------------------------------------
async function checkout() {
  creating.value = true
  try {
    const order = await api.orderCreate(buildCart())
    currentOrder.value = order
    payAmount.value = order.payableAmount
    tenderedYuan.value = Number((order.payableAmount / 100).toFixed(2))
    payLog.value = []
    channel.value = 'CASH'
    payVisible.value = true
    loadHolds()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    creating.value = false
  }
}

async function payCash() {
  paying.value = true
  try {
    const r = await api.payCash(currentOrder.value.orderNo, tendered.value)
    await afterPaid(currentOrder.value.orderNo, r.change)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    paying.value = false
  }
}

async function payWallet() {
  if (!member.value) {
    ElMessage.warning('储值支付需要先识别会员')
    return
  }
  paying.value = true
  try {
    const pay = await api.payCreate(currentOrder.value.orderNo, 'WALLET')
    await api.payMockCallback(pay.payNo, true, null, 0)
    await afterPaid(currentOrder.value.orderNo, 0)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    paying.value = false
  }
}

/** 扫码支付演示：成功/失败/延迟/重复回调 都走同一个真实回调接口 */
async function payMock(success, delayMs) {
  try {
    const pay = await api.payCreate(currentOrder.value.orderNo, channel.value)
    const r = await api.payMockCallback(pay.payNo, success, `MOCK${Date.now()}`, delayMs)
    payLog.value.push({
      type: success && r.accepted ? 'ok' : 'warn',
      text: `${new Date().toLocaleTimeString()} 回调结果：accepted=${r.accepted}，status=${r.status}，回调次数=${r.callbackCount}`
    })
    if (success && r.accepted) {
      await afterPaid(currentOrder.value.orderNo, 0)
    } else if (!success) {
      ElMessage.warning('支付失败，可重新发起')
    }
  } catch (e) {
    ElMessage.error(e.message)
  }
}

/** 同一个支付单连点两次，第二次会被幂等挡掉 */
async function payTwice() {
  try {
    const pay = await api.payCreate(currentOrder.value.orderNo, channel.value)
    const tradeNo = `MOCK-DUP-${Date.now()}`
    const first = await api.payMockCallback(pay.payNo, true, tradeNo, 0)
    payLog.value.push({ type: 'ok', text: `第 1 次：accepted=${first.accepted}，回调次数=${first.callbackCount}` })
    const second = await api.payMockCallback(pay.payNo, true, tradeNo, 0)
    payLog.value.push({
      type: 'warn',
      text: `第 2 次：accepted=${second.accepted}，回调次数=${second.callbackCount}（${second.reason || ''}）`
    })
    await afterPaid(currentOrder.value.orderNo, 0)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function afterPaid(orderNo, change) {
  const detail = await api.orderDetail(orderNo)
  // 订单详情是 { order, items, discounts, payments, statusLogs } 五段结构
  receipt.value = {
    orderNo,
    items: detail.items || [],
    totalAmount: detail.order?.totalAmount || 0,
    discountAmount: detail.order?.discountAmount || 0,
    paidAmount: detail.order?.paidAmount || 0,
    changeAmount: change ?? detail.order?.changeAmount ?? 0
  }
  nowText.value = new Date().toLocaleString()
  payVisible.value = false
  receiptVisible.value = true
  ElMessage.success('收款完成')
}

function printReceipt() {
  window.print()
}

function newOrder() {
  receiptVisible.value = false
  clearCart()
  clearMember()
  keyword.value = ''
  search()
  loadHolds()
}

onMounted(() => {
  search()
  loadHolds()
})
</script>

<style scoped>
.cashier {
  display: flex;
  gap: 10px;
  height: 100%;
}

/* ---------- 左 ---------- */
.left {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: #fff;
  border-radius: var(--pos-radius);
  padding: 12px;
}

.scan-bar {
  display: flex;
  margin-bottom: 10px;
}

.grid {
  flex: 1;
  overflow-y: auto;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(168px, 1fr));
  gap: 8px;
  align-content: start;
}

.goods-card {
  border: 1px solid var(--pos-border);
  border-radius: 8px;
  padding: 10px;
  cursor: pointer;
  transition: 0.15s;
  background: #fff;
}

.goods-card:hover {
  border-color: var(--pos-primary);
  box-shadow: 0 2px 10px rgba(200, 16, 46, 0.12);
}

.goods-card .name {
  font-size: 14px;
  font-weight: 600;
  height: 40px;
  overflow: hidden;
  line-height: 1.4;
}

.goods-card .row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-top: 6px;
}

.goods-card .stock {
  font-size: 12px;
  color: var(--pos-muted);
}

.goods-card .stock.low {
  color: #e6a23c;
}

/* ---------- 右 ---------- */
.right {
  width: 480px;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: var(--pos-radius);
  flex-shrink: 0;
  overflow: hidden;
}

.cart-head {
  padding: 12px;
  border-bottom: 1px solid var(--pos-border);
}

.member-tag {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  font-size: 13px;
  flex-wrap: wrap;
}

.cart-body {
  flex: 1;
  overflow-y: auto;
}

.cart-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.cart-table th {
  text-align: left;
  padding: 8px 6px;
  background: #fafbfc;
  color: var(--pos-muted);
  font-weight: 500;
  position: sticky;
  top: 0;
}

.cart-table td {
  padding: 8px 6px;
  border-bottom: 1px solid #f2f4f7;
  vertical-align: middle;
}

.ci-name {
  font-weight: 500;
}

.qty {
  display: flex;
  align-items: center;
  gap: 4px;
}

.qty .num {
  min-width: 28px;
  text-align: center;
}

.empty {
  text-align: center;
  color: var(--pos-muted);
  padding: 40px 0 !important;
}

.cart-foot {
  border-top: 1px solid var(--pos-border);
  padding: 10px 12px 12px;
}

.discount-box {
  background: #fff8f8;
  border-radius: 6px;
  padding: 6px 8px;
  margin-bottom: 8px;
}

.d-line {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #a02534;
  line-height: 1.8;
}

.neg {
  color: #c8102e;
}

.opt-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}

.sum-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  padding: 6px 0;
}

.sum-right {
  text-align: right;
}

.qty-note {
  font-size: 12px;
  color: var(--pos-muted);
  margin-top: 2px;
}

.btn-row {
  display: flex;
  gap: 8px;
  margin-top: 6px;
}

.pay-btn {
  flex: 1;
  height: 52px;
  font-size: 18px;
  letter-spacing: 4px;
  background: var(--pos-primary);
  border-color: var(--pos-primary);
}

.badge {
  margin-left: 4px;
}

/* ---------- 支付弹窗 ---------- */
.pay-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  border-bottom: 1px dashed var(--pos-border);
  padding-bottom: 10px;
}

.pay-amount-hint {
  font-size: 12px;
  color: var(--pos-muted);
  margin: 6px 0 10px;
}

.pay-methods {
  margin: 6px 0 14px;
}

.cash-box .quick-cash {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.change {
  margin-top: 12px;
  font-size: 13px;
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}

.mock-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.pay-log {
  margin-top: 12px;
  background: #f7f8fa;
  border-radius: 6px;
  padding: 8px;
  font-size: 12px;
  max-height: 130px;
  overflow-y: auto;
}

.log-line.ok {
  color: #2f9e44;
}

.log-line.warn {
  color: #e6a23c;
}

/* ---------- 小票 ---------- */
.receipt-print {
  font-family: 'Courier New', monospace;
  font-size: 13px;
  color: #000;
  padding: 8px;
}

.r-title {
  text-align: center;
  font-size: 16px;
  font-weight: 700;
}

.r-sub {
  text-align: center;
  color: #666;
  margin: 4px 0;
}

.r-line {
  display: flex;
  justify-content: space-between;
  line-height: 1.9;
}

.r-strong {
  font-weight: 700;
  font-size: 15px;
}

.r-item {
  display: flex;
  justify-content: space-between;
  line-height: 1.8;
}

.r-hr {
  border-top: 1px dashed #999;
  margin: 6px 0;
}
</style>
