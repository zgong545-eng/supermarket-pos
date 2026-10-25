<template>
  <div class="home">
    <!-- 会员卡 -->
    <div class="member-card" @click="$router.push('/h5/mine')">
      <div class="row1">
        <span class="hello">Hi，{{ member?.name || '会员' }}</span>
        <span class="level">{{ member?.levelName || '' }}</span>
      </div>
      <div class="row2">
        <div class="stat">
          <div class="num">{{ member?.points ?? 0 }}</div>
          <div class="lab">积分</div>
        </div>
        <div class="stat">
          <div class="num">{{ yuan(member?.balance ?? 0) }}</div>
          <div class="lab">储值余额</div>
        </div>
        <div class="stat">
          <div class="num">{{ couponCount }}</div>
          <div class="lab">可用券</div>
        </div>
      </div>
    </div>

    <!-- 活动 -->
    <div v-if="activities.length" class="acts">
      <div v-for="a in activities" :key="a.activityNo" class="act">
        <van-icon name="gift-o" />
        <span>{{ a.name }}</span>
      </div>
    </div>

    <!-- 分类 -->
    <van-tabs v-model:active="activeCat" sticky swipeable @change="load">
      <van-tab title="全部" :name="0" />
      <van-tab v-for="c in categories" :key="c.id" :title="c.name" :name="c.id" />
    </van-tabs>

    <!-- 商品 -->
    <div class="goods-list">
      <div v-for="g in goods" :key="g.skuId" class="goods" @click="openDetail(g)">
        <div class="thumb">{{ (g.name || '').slice(0, 2) }}</div>
        <div class="info">
          <div class="name">{{ g.name }}</div>
          <div class="spec">{{ g.spec || '' }} {{ g.unit }}</div>
          <div class="price-row">
            <span class="price">¥{{ yuan(g.memberPrice || g.standardPrice) }}</span>
            <span v-if="g.memberPrice" class="origin">¥{{ yuan(g.standardPrice) }}</span>
            <span class="avail">余 {{ g.available }}</span>
          </div>
        </div>
        <van-button type="danger" size="small" round icon="plus" @click.stop="quickAdd(g)" />
      </div>
      <van-empty v-if="!goods.length" description="这个分类暂时没有商品" />
    </div>

    <!-- 详情弹层 -->
    <van-popup v-model:show="detailVisible" round position="bottom">
      <div v-if="detail" class="detail">
        <div class="d-name">{{ detail.name }}</div>
        <div class="d-spec">{{ detail.spec }} {{ detail.unit }}</div>
        <div class="d-price">
          ¥{{ yuan(detail.memberPrice || detail.standardPrice) }}
          <span v-if="detail.memberPrice" class="origin">¥{{ yuan(detail.standardPrice) }}</span>
        </div>
        <van-cell-group inset style="margin-top: 12px">
          <van-cell title="库存" :value="'可售 ' + detail.available" />
          <van-cell title="计价方式" :value="detail.isWeighed ? '称重' : '计件'" />
          <van-cell v-if="detail.description" title="说明" :label="detail.description" />
        </van-cell-group>
        <div class="d-btn">
          <van-button type="danger" block round @click="addFromDetail">加入购物车</van-button>
        </div>
      </div>
    </van-popup>

    <!-- 购物车悬浮条 -->
    <van-submit-bar v-if="count" :price="cartAmount" button-text="去结算" @submit="toCart"
                    button-color="#c8102e" label="预估" style="bottom: 50px">
      <template #tip>
        实际金额以下单页为准（会员价与优惠由服务端计算）
      </template>
    </van-submit-bar>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showLoadingToast } from 'vant'
import { api } from '../../api'
import { yuan } from '../../session'
import { cart, addItem } from './cartStore'

const router = useRouter()
const member = ref(null)
const categories = ref([])
const activities = ref([])
const couponCount = ref(0)
const goods = ref([])
const activeCat = ref(0)
const detailVisible = ref(false)
const detail = ref(null)

const count = computed(() => cart.items.reduce((s, i) => s + i.qty, 0))
const cartAmount = computed(() => cart.items.reduce((s, i) => s + i.price * i.qty, 0))

async function load() {
  try {
    const rows = await api.h5Goods({
      categoryId: activeCat.value || undefined,
      page: 1,
      size: 50
    })
    goods.value = rows || []
  } catch (e) {
    showToast(e.message)
  }
}

async function loadHome() {
  const toast = showLoadingToast({ message: '加载中', forbidClick: true })
  try {
    const h = await api.h5Home()
    member.value = h.member
    categories.value = h.categories || []
    activities.value = h.activities || []
    couponCount.value = h.couponCount || 0
  } catch (e) {
    showToast(e.message)
  } finally {
    toast.close()
  }
  load()
}

function quickAdd(g) {
  if (g.isWeighed) {
    openDetail(g)
    return
  }
  addItem({
    skuId: g.skuId,
    name: g.name,
    spec: g.spec,
    unit: g.unit,
    price: g.memberPrice || g.standardPrice
  })
  showToast('已加入购物车')
}

async function openDetail(g) {
  try {
    detail.value = await api.h5GoodsDetail(g.skuId)
    detailVisible.value = true
  } catch (e) {
    showToast(e.message)
  }
}

function addFromDetail() {
  const d = detail.value
  if (!d) return
  if (d.isWeighed) {
    // 手机端简化：称重商品默认 1kg，数量在购物车可改
    addItem(
      { skuId: d.skuId, name: d.name, spec: d.spec, unit: d.unit, price: d.memberPrice || d.standardPrice },
      1
    )
  } else {
    addItem({ skuId: d.skuId, name: d.name, spec: d.spec, unit: d.unit, price: d.memberPrice || d.standardPrice })
  }
  detailVisible.value = false
  showToast('已加入购物车')
}

function toCart() {
  router.push('/h5/cart')
}

onMounted(loadHome)
</script>

<style scoped>
.home {
  padding-bottom: 90px;
  background: #f7f8fa;
  min-height: 100vh;
}

.member-card {
  margin: 12px;
  border-radius: 14px;
  padding: 16px;
  background: linear-gradient(135deg, #c8102e 0%, #8f0a1f 100%);
  color: #fff;
}

.row1 {
  display: flex;
  align-items: center;
  gap: 8px;
}

.hello {
  font-size: 17px;
  font-weight: 600;
}

.level {
  font-size: 11px;
  background: rgba(255, 255, 255, 0.22);
  padding: 2px 8px;
  border-radius: 10px;
}

.row2 {
  display: flex;
  margin-top: 16px;
}

.stat {
  flex: 1;
  text-align: center;
}

.stat .num {
  font-size: 18px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.stat .lab {
  font-size: 11px;
  opacity: 0.8;
  margin-top: 2px;
}

.acts {
  display: flex;
  gap: 8px;
  padding: 0 12px 8px;
  overflow-x: auto;
}

.act {
  display: flex;
  align-items: center;
  gap: 4px;
  background: #fff3f4;
  color: #c8102e;
  font-size: 12px;
  padding: 6px 10px;
  border-radius: 14px;
  white-space: nowrap;
}

.goods-list {
  padding: 8px 12px;
}

.goods {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border-radius: 12px;
  padding: 10px;
  margin-bottom: 8px;
}

.thumb {
  width: 56px;
  height: 56px;
  border-radius: 10px;
  background: #f2f4f7;
  color: #98a2b3;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  flex-shrink: 0;
}

.info {
  flex: 1;
  min-width: 0;
}

.name {
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.spec {
  font-size: 12px;
  color: #969799;
  margin-top: 2px;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-top: 6px;
}

.price {
  color: #c8102e;
  font-weight: 700;
  font-size: 15px;
  font-variant-numeric: tabular-nums;
}

.origin {
  color: #c8c9cc;
  text-decoration: line-through;
  font-size: 12px;
}

.avail {
  font-size: 11px;
  color: #969799;
  margin-left: auto;
}

.detail {
  padding: 18px 16px 24px;
}

.d-name {
  font-size: 18px;
  font-weight: 700;
}

.d-spec {
  color: #969799;
  font-size: 13px;
  margin-top: 4px;
}

.d-price {
  color: #c8102e;
  font-size: 22px;
  font-weight: 700;
  margin-top: 10px;
}

.d-btn {
  margin-top: 18px;
}
</style>
