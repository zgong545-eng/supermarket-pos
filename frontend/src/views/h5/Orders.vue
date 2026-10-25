<template>
  <div class="orders-page">
    <van-nav-bar title="我的订单" />

    <van-tabs v-model:active="status" @change="load">
      <van-tab title="全部" name="" />
      <van-tab title="待支付" name="PENDING_PAY" />
      <van-tab title="已完成" name="COMPLETED" />
      <van-tab title="已退货" name="REFUNDED" />
    </van-tabs>

    <div class="list">
      <div v-for="o in rows" :key="o.orderNo" class="order" @click="openDetail(o)">
        <div class="o-head">
          <span class="no">{{ o.orderNo }}</span>
          <span :class="['status', o.status]">{{ statusText(o.status) }}</span>
        </div>
        <div class="o-body">
          <span class="cnt">{{ o.itemCount }} 件商品</span>
          <span class="amt">¥{{ yuan(o.payableAmount) }}</span>
        </div>
        <div class="o-foot">
          <span class="time">{{ fmt(o.createdAt) }}</span>
          <div class="acts">
            <van-button v-if="o.status === 'PENDING_PAY'" size="mini" round type="danger"
                        @click.stop="pay(o)">去支付</van-button>
            <van-button v-if="o.status === 'PENDING_PAY'" size="mini" round plain
                        @click.stop="cancel(o)">取消</van-button>
          </div>
        </div>
      </div>
      <van-empty v-if="!rows.length" description="还没有订单" />
    </div>

    <van-popup v-model:show="detailVisible" round position="bottom" style="max-height: 80%">
      <div v-if="detail" class="detail">
        <div class="d-head">
          <span>订单详情</span>
          <span class="status">{{ statusText(detail.order.status) }}</span>
        </div>
        <div v-for="it in detail.items" :key="it.skuId" class="d-item">
          <span>{{ it.skuName }} × {{ it.qty }}</span>
          <span>¥{{ yuan(it.netAmount) }}</span>
        </div>
        <van-cell-group inset style="margin-top: 10px">
          <van-cell title="商品原价" :value="'¥' + yuan(detail.order.totalAmount)" />
          <van-cell title="优惠" :value="'-¥' + yuan(detail.order.discountAmount)" />
          <van-cell title="实付" :value="'¥' + yuan(detail.order.paidAmount)" />
        </van-cell-group>
        <div class="timeline">
          <div v-for="(l, i) in detail.statusLogs" :key="i" class="tl">
            {{ l.toStatus }} <span class="muted">{{ fmt(l.createdAt) }}</span>
          </div>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { api } from '../../api'
import { yuan } from '../../session'

const rows = ref([])
const status = ref('')
const detailVisible = ref(false)
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

function fmt(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : ''
}

async function load() {
  try {
    rows.value = await api.h5Orders(status.value || undefined)
  } catch (e) {
    showToast(e.message)
  }
}

async function openDetail(o) {
  try {
    detail.value = await api.h5OrderDetail(o.orderNo)
    detailVisible.value = true
  } catch (e) {
    showToast(e.message)
  }
}

async function pay(o) {
  try {
    await showConfirmDialog({ title: '确认支付', message: `应付 ¥${yuan(o.payableAmount)}` })
    await api.h5Pay(o.orderNo, 'WECHAT')
    showToast('支付成功')
    load()
  } catch (e) {
    if (e !== 'cancel') showToast(e.message || '已取消')
  }
}

async function cancel(o) {
  try {
    await showConfirmDialog({ title: '取消订单', message: '确定要取消这个订单吗？' })
    await api.h5Cancel(o.orderNo)
    showToast('已取消')
    load()
  } catch (e) {
    if (e !== 'cancel') showToast(e.message || '已取消')
  }
}

onMounted(load)
</script>

<style scoped>
.orders-page {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 60px;
}

.list {
  padding: 10px 12px;
}

.order {
  background: #fff;
  border-radius: 12px;
  padding: 12px;
  margin-bottom: 10px;
}

.o-head {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #969799;
}

.status {
  color: #c8102e;
  font-weight: 600;
}

.status.COMPLETED {
  color: #07c160;
}

.status.CANCELLED,
.status.REFUNDED {
  color: #969799;
}

.o-body {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin: 8px 0;
}

.cnt {
  font-size: 13px;
  color: #646566;
}

.amt {
  font-size: 17px;
  font-weight: 700;
  color: #323233;
}

.o-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.time {
  font-size: 12px;
  color: #969799;
}

.acts {
  display: flex;
  gap: 8px;
}

.detail {
  padding: 16px 12px 24px;
}

.d-head {
  display: flex;
  justify-content: space-between;
  font-weight: 600;
  margin-bottom: 12px;
}

.d-item {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  padding: 6px 0;
  border-bottom: 1px solid #f2f3f5;
}

.timeline {
  margin-top: 14px;
}

.tl {
  font-size: 12px;
  color: #646566;
  padding: 4px 0;
}
</style>
