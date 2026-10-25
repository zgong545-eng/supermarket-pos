<template>
  <div class="cart-page">
    <van-nav-bar title="购物车" />

    <div v-if="!cart.items.length" class="empty">
      <van-empty description="购物车还是空的">
        <van-button type="danger" round @click="$router.push('/h5/home')">去逛逛</van-button>
      </van-empty>
    </div>

    <template v-else>
      <div class="list">
        <van-swipe-cell v-for="(it, idx) in cart.items" :key="idx">
          <van-cell :title="it.name" :label="`${it.spec || ''} ${it.unit} · ¥${yuan(it.price)}`">
            <template #value>
              <van-stepper :model-value="it.qty" :min="1" integer @change="(v) => setQty(idx, v)" />
            </template>
          </van-cell>
          <template #right>
            <van-button square type="danger" text="删除" class="del" @click="removeItem(idx)" />
          </template>
        </van-swipe-cell>
      </div>

      <!-- 优惠 -->
      <van-cell-group inset class="opts">
        <van-cell title="优惠券" is-link :value="couponText" @click="showCoupons = true" />
        <van-cell title="使用积分" :label="`可用 ${member?.points || 0} 积分，100 积分抵 1 元`">
          <template #value>
            <van-stepper v-model="usePoints" :min="0" :max="member?.points || 0" :step="100" integer />
          </template>
        </van-cell>
      </van-cell-group>

      <!-- 金额 -->
      <van-cell-group inset class="sums">
        <van-cell title="商品原价" :value="'¥' + yuan(calc.totalAmount)" />
        <van-cell title="优惠合计" :value="'-¥' + yuan(calc.totalDiscount)" value-class="minus" />
        <van-cell title="应收" :value="'¥' + yuan(calc.payableAmount)" value-class="payable" />
      </van-cell-group>

      <div class="submit">
        <van-submit-bar :price="calc.payableAmount" button-text="提交订单" button-color="#c8102e"
                        @submit="createOrder" label="应收" />
      </div>
    </template>

    <!-- 选券 -->
    <van-popup v-model:show="showCoupons" round position="bottom" style="max-height: 70%">
      <div class="coupon-box">
        <div class="cb-title">选择优惠券</div>
        <van-checkbox-group v-model="couponIds">
          <van-cell v-for="c in coupons" :key="c.id" clickable @click="toggleCoupon(c.id)">
            <template #title>
              <div class="cp-name">{{ c.name }}</div>
              <div class="cp-cond">
                {{ c.type === 'CASH' ? '减 ' + yuan(c.faceValue) + ' 元' : (c.faceValue / 10) + ' 折' }}
                · {{ c.minAmount ? '满 ' + yuan(c.minAmount) : '无门槛' }}
                · 到期 {{ String(c.expireAt).slice(0, 10) }}
              </div>
            </template>
            <template #right-icon>
              <van-checkbox :name="c.id" @click.stop />
            </template>
          </van-cell>
        </van-checkbox-group>
        <van-empty v-if="!coupons.length" description="没有可用的券" image-size="60" />
        <div class="cb-foot">
          <van-button block round type="danger" @click="showCoupons = false">确定</van-button>
        </div>
      </div>
    </van-popup>

    <!-- 支付 -->
    <van-popup v-model:show="showPay" round position="bottom">
      <div class="pay-box">
        <div class="amount">¥{{ yuan(calc.payableAmount) }}</div>
        <van-radio-group v-model="channel">
          <van-cell-group>
            <van-cell title="微信支付（演示）" clickable @click="channel = 'WECHAT'">
              <template #right-icon><van-radio name="WECHAT" /></template>
            </van-cell>
            <van-cell title="支付宝（演示）" clickable @click="channel = 'ALIPAY'">
              <template #right-icon><van-radio name="ALIPAY" /></template>
            </van-cell>
            <van-cell :title="`储值卡（余额 ¥${yuan(member?.balance)}）`" clickable @click="channel = 'WALLET'">
              <template #right-icon><van-radio name="WALLET" /></template>
            </van-cell>
          </van-cell-group>
        </van-radio-group>
        <div class="pay-foot">
          <van-button block round type="danger" :loading="paying" @click="doPay">确认支付</van-button>
        </div>
        <p class="tip">演示环境由接口直接触发支付成功回调，走的仍是真实的幂等逻辑。</p>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { api } from '../../api'
import { yuan } from '../../session'
import { cart, setQty, removeItem, clearCart, toRequestItems } from './cartStore'

const router = useRouter()
const member = ref(null)
const coupons = ref([])
const couponIds = ref([])
const usePoints = ref(0)
const showCoupons = ref(false)
const showPay = ref(false)
const channel = ref('WECHAT')
const paying = ref(false)
const orderNo = ref('')
const calc = ref({ totalAmount: 0, totalDiscount: 0, payableAmount: 0 })

const couponText = computed(() =>
  couponIds.value.length ? `已选 ${couponIds.value.length} 张` : '未使用'
)

function toggleCoupon(id) {
  const i = couponIds.value.indexOf(id)
  if (i >= 0) couponIds.value.splice(i, 1)
  else couponIds.value.push(id)
}

async function recalc() {
  if (!cart.items.length) {
    calc.value = { totalAmount: 0, totalDiscount: 0, payableAmount: 0 }
    return
  }
  try {
    calc.value = await api.h5Calculate({
      items: toRequestItems(),
      memberCouponIds: couponIds.value,
      usePoints: usePoints.value
    })
  } catch (e) {
    showToast(e.message)
  }
}

watch([couponIds, usePoints], recalc, { deep: true })

async function load() {
  try {
    member.value = await api.h5Me()
    coupons.value = await api.h5Coupons(true)
  } catch (e) {
    /* 未登录由 401 拦截 */
  }
  recalc()
}

async function createOrder() {
  if (!cart.items.length) return
  try {
    const r = await api.h5CreateOrder({
      items: toRequestItems(),
      memberCouponIds: couponIds.value,
      usePoints: usePoints.value
    })
    orderNo.value = r.orderNo
    showPay.value = true
  } catch (e) {
    showToast(e.message)
  }
}

async function doPay() {
  paying.value = true
  try {
    await api.h5Pay(orderNo.value, channel.value)
    showPay.value = false
    clearCart()
    couponIds.value = []
    usePoints.value = 0
    await showConfirmDialog({
      title: '支付成功',
      message: '订单已完成，可在"订单"里查看。感谢光临！',
      confirmButtonText: '看订单'
    }).catch(() => {})
    router.replace('/h5/orders')
  } catch (e) {
    showToast(e.message)
  } finally {
    paying.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.cart-page {
  padding-bottom: 80px;
  min-height: 100vh;
  background: #f7f8fa;
}

.list {
  margin: 10px 0;
}

.opts,
.sums {
  margin: 10px 0;
}

.del {
  height: 100%;
}

.minus {
  color: #c8102e !important;
}

.payable {
  color: #c8102e !important;
  font-weight: 700;
  font-size: 16px;
}

.submit :deep(.van-submit-bar) {
  bottom: 50px;
}

.coupon-box {
  padding: 16px 12px 20px;
}

.cb-title {
  font-weight: 600;
  margin-bottom: 10px;
}

.cp-name {
  font-size: 14px;
  font-weight: 600;
}

.cp-cond {
  font-size: 12px;
  color: #969799;
  margin-top: 2px;
}

.cb-foot {
  margin-top: 16px;
}

.pay-box {
  padding: 22px 16px 18px;
}

.amount {
  text-align: center;
  font-size: 30px;
  font-weight: 700;
  color: #c8102e;
  margin-bottom: 16px;
  font-variant-numeric: tabular-nums;
}

.pay-foot {
  margin-top: 18px;
}

.tip {
  font-size: 12px;
  color: #969799;
  margin-top: 12px;
  line-height: 1.6;
}

.empty {
  padding-top: 60px;
}
</style>
