<template>
  <div class="page-card">
    <el-tabs v-model="tab">
      <el-tab-pane label="促销活动" name="act">
        <div class="toolbar">
          <p class="muted" style="margin: 0">
            活动按 priority 从小到大依次计算；同一单可以叠加多个活动，优惠会按商品金额比例分摊到每一行。
          </p>
          <div class="spacer" />
          <el-button v-if="hasPerm('promo:edit')" type="primary" @click="openAct">新建活动</el-button>
        </div>

        <el-table :data="activities" size="small" border v-loading="loading">
          <el-table-column prop="activityNo" label="编号" width="170" />
          <el-table-column prop="name" label="名称" min-width="160" />
          <el-table-column label="类型" width="110">
            <template #default="{ row }">
              <el-tag size="small">{{ actType(row.type) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="规则" min-width="180">
            <template #default="{ row }">{{ ruleText(row) }}</template>
          </el-table-column>
          <el-table-column prop="priority" label="优先级" width="80" />
          <el-table-column label="有效期" width="330">
            <template #default="{ row }">{{ fmt(row.startAt) }} ~ {{ fmt(row.endAt) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-switch :model-value="row.status === 1" :disabled="!hasPerm('promo:edit')"
                         @change="(v) => toggleAct(row, v)" />
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="优惠券" name="coupon">
        <div class="toolbar">
          <p class="muted" style="margin: 0">券是"有主的"：先建模板，再发给会员。收银台选券后金额由后端重算。</p>
          <div class="spacer" />
          <el-button v-if="hasPerm('promo:coupon')" type="primary" @click="openCoupon">新建券模板</el-button>
        </div>

        <el-table :data="coupons" size="small" border v-loading="loading">
          <el-table-column prop="couponCode" label="编码" width="170" />
          <el-table-column prop="name" label="名称" min-width="150" />
          <el-table-column label="面额" width="120">
            <template #default="{ row }">
              {{ row.type === 'CASH' ? '减 ' + yuan(row.faceValue) + ' 元' : row.faceValue / 10 + ' 折' }}
            </template>
          </el-table-column>
          <el-table-column label="门槛" width="120">
            <template #default="{ row }">
              {{ row.minAmount ? '满 ' + yuan(row.minAmount) : '无门槛' }}
            </template>
          </el-table-column>
          <el-table-column label="发放/总量" width="120">
            <template #default="{ row }">{{ row.issuedQty }} / {{ row.totalQty || '不限' }}</template>
          </el-table-column>
          <el-table-column prop="perLimit" label="每人限领" width="100" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 新建活动 -->
    <el-dialog v-model="actVisible" title="新建促销活动" width="560px">
      <el-form label-width="110px">
        <el-form-item label="活动名称"><el-input v-model="actForm.name" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="actForm.type" style="width: 100%">
            <el-option label="满减" value="FULL_REDUCE" />
            <el-option label="折扣" value="DISCOUNT" />
          </el-select>
        </el-form-item>
        <template v-if="actForm.type === 'FULL_REDUCE'">
          <el-form-item label="门槛（元）">
            <el-input-number v-model="actForm.threshold" :min="0" :step="10" style="width: 180px" />
          </el-form-item>
          <el-form-item label="减免（元）">
            <el-input-number v-model="actForm.reduce" :min="0" :step="5" style="width: 180px" />
          </el-form-item>
          <el-form-item label="可重复">
            <el-switch v-model="actForm.repeat" />
            <span class="muted" style="margin-left: 8px">满 200 减 10 且可重复时，400 元会减 20</span>
          </el-form-item>
        </template>
        <el-form-item v-else label="折扣（0-100）">
          <el-input-number v-model="actForm.rate" :min="1" :max="99" style="width: 180px" />
          <span class="muted" style="margin-left: 8px">95 表示 9.5 折（实付 95%）</span>
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="actForm.priority" :min="1" :max="999" style="width: 180px" />
          <span class="muted" style="margin-left: 8px">数字小的先算</span>
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="actForm.startAt" type="datetime" style="width: 220px" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="actForm.endAt" type="datetime" style="width: 220px" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="actForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="actVisible = false">取消</el-button>
        <el-button type="primary" @click="saveAct">创建</el-button>
      </template>
    </el-dialog>

    <!-- 新建券 -->
    <el-dialog v-model="couponVisible" title="新建券模板" width="520px">
      <el-form label-width="110px">
        <el-form-item label="券名称"><el-input v-model="couponForm.name" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="couponForm.type" style="width: 100%">
            <el-option label="代金券（立减）" value="CASH" />
            <el-option label="折扣券" value="DISCOUNT" />
          </el-select>
        </el-form-item>
        <el-form-item :label="couponForm.type === 'CASH' ? '面额（元）' : '折扣（0-100）'">
          <el-input-number v-model="couponForm.face" :min="1" :max="couponForm.type === 'CASH' ? 9999 : 99"
                           style="width: 180px" />
        </el-form-item>
        <el-form-item label="使用门槛">
          <el-input-number v-model="couponForm.minYuan" :min="0" :step="10" style="width: 180px" />
          <span class="muted" style="margin-left: 8px">0 表示无门槛</span>
        </el-form-item>
        <el-form-item label="发放总量">
          <el-input-number v-model="couponForm.totalQty" :min="0" style="width: 180px" />
          <span class="muted" style="margin-left: 8px">0 表示不限</span>
        </el-form-item>
        <el-form-item label="每人限领">
          <el-input-number v-model="couponForm.perLimit" :min="1" style="width: 180px" />
        </el-form-item>
        <el-form-item label="有效天数">
          <el-input-number v-model="couponForm.validDays" :min="1" style="width: 180px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="couponVisible = false">取消</el-button>
        <el-button type="primary" @click="saveCoupon">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api'
import { hasPerm, yuan, toCents } from '../../session'

const tab = ref('act')
const activities = ref([])
const coupons = ref([])
const loading = ref(false)
const actVisible = ref(false)
const couponVisible = ref(false)

const actForm = reactive({
  name: '', type: 'FULL_REDUCE', threshold: 100, reduce: 10, rate: 95,
  repeat: false, priority: 100, startAt: new Date(), endAt: null, remark: ''
})
const couponForm = reactive({
  name: '', type: 'CASH', face: 10, minYuan: 0, totalQty: 100, perLimit: 1, validDays: 30
})

const ACT_TYPE = { FULL_REDUCE: '满减', DISCOUNT: '折扣', NTH_DISCOUNT: '第N件' }

function actType(t) {
  return ACT_TYPE[t] || t
}

function fmt(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : ''
}

/** 规则存的是 JSON 字符串，列表里翻译成人话 */
function ruleText(row) {
  try {
    const r = JSON.parse(row.ruleJson || '{}')
    if (row.type === 'FULL_REDUCE') {
      return `满 ${yuan(r.threshold)} 减 ${yuan(r.reduce)}${r.repeat ? '（可重复）' : ''}`
    }
    if (row.type === 'DISCOUNT') return `${r.rate}%（${(r.rate / 10).toFixed(1)} 折）`
    return row.ruleJson
  } catch (e) {
    return row.ruleJson
  }
}

async function load() {
  loading.value = true
  try {
    activities.value = await api.promotionActivities()
    coupons.value = await api.promotionCoupons()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function toggleAct(row, v) {
  try {
    await api.promotionToggleActivity(row.id, v ? 1 : 0)
    ElMessage.success(v ? '已启用' : '已停用')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openAct() {
  Object.assign(actForm, {
    name: '', type: 'FULL_REDUCE', threshold: 100, reduce: 10, rate: 95,
    repeat: false, priority: 100, startAt: new Date(), endAt: null, remark: ''
  })
  actVisible.value = true
}

async function saveAct() {
  if (!actForm.name) {
    ElMessage.warning('活动名称必填')
    return
  }
  if (!actForm.endAt) {
    ElMessage.warning('请选择结束时间')
    return
  }
  const rule =
    actForm.type === 'FULL_REDUCE'
      ? { threshold: toCents(actForm.threshold), reduce: toCents(actForm.reduce), repeat: actForm.repeat }
      : { rate: actForm.rate }
  try {
    await api.promotionCreateActivity({
      name: actForm.name,
      type: actForm.type,
      priority: actForm.priority,
      ruleJson: JSON.stringify(rule),
      startAt: iso(actForm.startAt),
      endAt: iso(actForm.endAt),
      remark: actForm.remark
    })
    ElMessage.success('活动已创建')
    actVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openCoupon() {
  Object.assign(couponForm, {
    name: '', type: 'CASH', face: 10, minYuan: 0, totalQty: 100, perLimit: 1, validDays: 30
  })
  couponVisible.value = true
}

async function saveCoupon() {
  if (!couponForm.name) {
    ElMessage.warning('券名称必填')
    return
  }
  try {
    await api.promotionCreateCoupon({
      name: couponForm.name,
      type: couponForm.type,
      faceValue: couponForm.type === 'CASH' ? toCents(couponForm.face) : couponForm.face,
      minAmount: toCents(couponForm.minYuan),
      totalQty: couponForm.totalQty,
      perLimit: couponForm.perLimit,
      validDays: couponForm.validDays
    })
    ElMessage.success('券模板已创建')
    couponVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

/** 转成后端能直接吃的时间字符串（LocalDateTime），不要带 Z 和毫秒 */
function iso(d) {
  if (!d) return null
  const t = new Date(d)
  const pad = (n) => String(n).padStart(2, '0')
  return `${t.getFullYear()}-${pad(t.getMonth() + 1)}-${pad(t.getDate())} ${pad(t.getHours())}:${pad(t.getMinutes())}:00`
}

onMounted(load)
</script>
