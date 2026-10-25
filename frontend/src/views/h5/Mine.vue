<template>
  <div class="mine-page">
    <div class="head">
      <div class="avatar">{{ (member?.name || '会').slice(0, 1) }}</div>
      <div class="who">
        <div class="name">{{ member?.name || '会员' }}</div>
        <div class="no">{{ member?.memberNo || '' }} · {{ member?.levelName || '' }}</div>
      </div>
    </div>

    <div class="stats">
      <div class="stat" @click="showPoints = true">
        <div class="num">{{ member?.points ?? 0 }}</div>
        <div class="lab">积分</div>
      </div>
      <div class="stat" @click="showWallet = true">
        <div class="num">{{ yuan(member?.balance ?? 0) }}</div>
        <div class="lab">储值余额</div>
      </div>
      <div class="stat" @click="showCoupons = true">
        <div class="num">{{ coupons.length }}</div>
        <div class="lab">可用券</div>
      </div>
    </div>

    <van-cell-group inset style="margin-top: 12px">
      <van-cell title="我的订单" is-link to="/h5/orders" icon="orders-o" />
      <van-cell title="我的优惠券" is-link icon="coupon-o" @click="showCoupons = true" />
      <van-cell title="积分明细" is-link icon="star-o" @click="showPoints = true" />
      <van-cell title="储值流水" is-link icon="gold-coin-o" @click="showWallet = true" />
    </van-cell-group>

    <van-cell-group inset style="margin-top: 12px">
      <van-cell title="会员折扣" :value="(member?.discountRate / 100).toFixed(2) + ' 折'" />
      <van-cell title="积分倍率" :value="(member?.pointRate / 100).toFixed(2) + ' 倍'" />
    </van-cell-group>

    <div class="logout">
      <van-button block round plain type="danger" @click="logout">退出登录</van-button>
    </div>

    <!-- 积分 -->
    <van-popup v-model:show="showPoints" round position="bottom" style="height: 60%">
      <div class="sheet">
        <div class="sheet-title">积分明细</div>
        <van-cell v-for="p in pointsLog" :key="p.id" :title="p.remark || p.changeType"
                  :label="fmt(p.createdAt)">
          <template #value>
            <span :class="p.points > 0 ? 'up' : 'down'">{{ p.points > 0 ? '+' : '' }}{{ p.points }}</span>
          </template>
        </van-cell>
        <van-empty v-if="!pointsLog.length" description="还没有积分记录" image-size="60" />
      </div>
    </van-popup>

    <!-- 储值 -->
    <van-popup v-model:show="showWallet" round position="bottom" style="height: 60%">
      <div class="sheet">
        <div class="sheet-title">储值流水</div>
        <van-cell v-for="w in walletLog" :key="w.id" :title="w.remark || w.txnType"
                  :label="fmt(w.createdAt)">
          <template #value>
            <span :class="w.amount > 0 ? 'up' : 'down'">{{ yuan(w.amount) }}</span>
          </template>
        </van-cell>
        <van-empty v-if="!walletLog.length" description="还没有储值记录" image-size="60" />
      </div>
    </van-popup>

    <!-- 券 -->
    <van-popup v-model:show="showCoupons" round position="bottom" style="height: 60%">
      <div class="sheet">
        <div class="sheet-title">我的优惠券</div>
        <div v-for="c in coupons" :key="c.id" class="coupon">
          <div class="c-left">
            <div class="c-val">
              {{ c.type === 'CASH' ? '¥' + yuan(c.faceValue) : (c.faceValue / 10) + '折' }}
            </div>
            <div class="c-cond">{{ c.minAmount ? '满 ' + yuan(c.minAmount) + ' 可用' : '无门槛' }}</div>
          </div>
          <div class="c-right">
            <div class="c-name">{{ c.name }}</div>
            <div class="c-exp">{{ String(c.expireAt).slice(0, 10) }} 到期</div>
          </div>
        </div>
        <van-empty v-if="!coupons.length" description="暂时没有可用券" image-size="60" />
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { api } from '../../api'
import { yuan, doLogout } from '../../session'
import { clearCart } from './cartStore'

const router = useRouter()
const member = ref(null)
const coupons = ref([])
const pointsLog = ref([])
const walletLog = ref([])
const showPoints = ref(false)
const showWallet = ref(false)
const showCoupons = ref(false)

function fmt(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : ''
}

async function load() {
  try {
    member.value = await api.h5Me()
    coupons.value = await api.h5Coupons(true)
    pointsLog.value = await api.h5Points()
    walletLog.value = await api.h5Wallet()
  } catch (e) {
    showToast(e.message)
  }
}

async function logout() {
  clearCart()
  await doLogout()
  router.replace('/h5/login')
}

onMounted(load)
</script>

<style scoped>
.mine-page {
  min-height: 100vh;
  background: #f7f8fa;
  padding-bottom: 40px;
}

.head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 26px 18px 22px;
  background: linear-gradient(135deg, #c8102e 0%, #8f0a1f 100%);
  color: #fff;
}

.avatar {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.22);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  font-weight: 700;
}

.name {
  font-size: 18px;
  font-weight: 600;
}

.no {
  font-size: 12px;
  opacity: 0.85;
  margin-top: 4px;
}

.stats {
  display: flex;
  background: #fff;
  margin: -12px 12px 0;
  border-radius: 12px;
  padding: 14px 0;
  box-shadow: 0 2px 10px rgba(16, 24, 40, 0.06);
}

.stat {
  flex: 1;
  text-align: center;
}

.stat .num {
  font-size: 17px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.stat .lab {
  font-size: 11px;
  color: #969799;
  margin-top: 2px;
}

.logout {
  padding: 26px 16px;
}

.sheet {
  padding: 16px 12px;
  height: 100%;
  overflow-y: auto;
}

.sheet-title {
  font-weight: 600;
  margin-bottom: 10px;
}

.up {
  color: #07c160;
}

.down {
  color: #c8102e;
}

.coupon {
  display: flex;
  background: #fff7f7;
  border-radius: 10px;
  margin-bottom: 10px;
  overflow: hidden;
}

.c-left {
  width: 96px;
  background: #c8102e;
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 12px 0;
}

.c-val {
  font-size: 18px;
  font-weight: 700;
}

.c-cond {
  font-size: 11px;
  opacity: 0.85;
  margin-top: 2px;
}

.c-right {
  flex: 1;
  padding: 12px;
}

.c-name {
  font-size: 14px;
  font-weight: 600;
}

.c-exp {
  font-size: 11px;
  color: #969799;
  margin-top: 4px;
}
</style>
