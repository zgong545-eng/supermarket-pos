<template>
  <div class="page-card">
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="姓名 / 手机号 / 卡号" clearable style="width: 240px"
                @keyup.enter="load" />
      <el-button type="primary" @click="load">查询</el-button>
      <div class="spacer" />
      <el-button type="primary" v-if="hasPerm('member:edit')" @click="openRegister">开卡</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" size="small" border>
      <el-table-column prop="memberNo" label="会员号" width="140" />
      <el-table-column prop="name" label="姓名" width="110" />
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column prop="levelName" label="等级" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="levelType(row.levelName)">{{ row.levelName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="points" label="积分" width="90" />
      <el-table-column label="储值余额" width="110">
        <template #default="{ row }">{{ yuan(row.balance) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? '正常' : '冻结' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button link type="success" v-if="hasPerm('member:recharge')"
                     @click="openRecharge(row)">充值</el-button>
          <el-button link type="warning" v-if="hasPerm('member:edit')"
                     @click="openIssue(row)">发券</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination v-model:current-page="page" :page-size="size" :total="total"
                     layout="total, prev, pager, next" background @current-change="load" />
    </div>

    <!-- 开卡 -->
    <el-dialog v-model="registerVisible" title="开卡（新建会员）" width="420px">
      <el-form label-width="80px">
        <el-form-item label="姓名"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" maxlength="11" /></el-form-item>
        <el-form-item label="等级">
          <el-select v-model="form.levelId" style="width: 100%">
            <el-option label="普通会员" :value="1" />
            <el-option label="银卡" :value="2" />
            <el-option label="金卡" :value="3" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="registerVisible = false">取消</el-button>
        <el-button type="primary" @click="doRegister">确认开卡</el-button>
      </template>
    </el-dialog>

    <!-- 充值 -->
    <el-dialog v-model="rechargeVisible" title="储值充值" width="400px">
      <p class="muted">{{ current?.name }} · 当前余额 {{ yuan(current?.balance) }} 元</p>
      <el-input-number v-model="rechargeYuan" :min="1" :step="100" :precision="2" style="width: 100%" />
      <div class="quick">
        <el-button v-for="v in [100, 200, 500, 1000]" :key="v" size="small" @click="rechargeYuan = v">
          {{ v }}
        </el-button>
      </div>
      <template #footer>
        <el-button @click="rechargeVisible = false">取消</el-button>
        <el-button type="primary" @click="doRecharge">确认充值</el-button>
      </template>
    </el-dialog>

    <!-- 发券 -->
    <el-dialog v-model="issueVisible" title="给会员发券" width="420px">
      <p class="muted">{{ current?.name }}</p>
      <el-select v-model="couponId" placeholder="选择券模板" style="width: 100%">
        <el-option v-for="c in templates" :key="c.id"
                   :label="`${c.name}（${c.type === 'CASH' ? '减 ' + yuan(c.faceValue) + ' 元' : (c.faceValue / 10) + ' 折'}）`"
                   :value="c.id" />
      </el-select>
      <template #footer>
        <el-button @click="issueVisible = false">取消</el-button>
        <el-button type="primary" @click="doIssue">发放</el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-drawer v-model="detailVisible" :title="`会员详情 · ${current?.name || ''}`" size="520px">
      <el-descriptions :column="2" border size="small" v-if="detail">
        <el-descriptions-item label="会员号">{{ detail.memberNo }}</el-descriptions-item>
        <el-descriptions-item label="等级">{{ detail.levelName }}</el-descriptions-item>
        <el-descriptions-item label="积分">{{ detail.points }}</el-descriptions-item>
        <el-descriptions-item label="储值余额">{{ yuan(detail.balance) }}</el-descriptions-item>
        <el-descriptions-item label="折扣率">{{ (detail.discountRate / 100).toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="积分倍率">{{ (detail.pointRate / 100).toFixed(2) }}</el-descriptions-item>
      </el-descriptions>

      <el-tabs v-model="detailTab" style="margin-top: 14px">
        <el-tab-pane label="积分记录" name="points">
          <el-table :data="pointsLog" size="small">
            <el-table-column prop="changeType" label="类型" width="100" />
            <el-table-column prop="points" label="变动" width="80" />
            <el-table-column label="余额" width="80">
              <template #default="{ row }">{{ row.afterPoints }}</template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="储值流水" name="wallet">
          <el-table :data="walletLog" size="small">
            <el-table-column prop="txnType" label="类型" width="100" />
            <el-table-column label="金额" width="100">
              <template #default="{ row }">{{ yuan(row.amount) }}</template>
            </el-table-column>
            <el-table-column label="余额" width="100">
              <template #default="{ row }">{{ yuan(row.afterBalance) }}</template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="优惠券" name="coupons">
          <el-table :data="coupons" size="small">
            <el-table-column prop="name" label="券" />
            <el-table-column label="面额" width="110">
              <template #default="{ row }">
                {{ row.type === 'CASH' ? yuan(row.faceValue) + ' 元' : (row.faceValue / 10) + ' 折' }}
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="90" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api'
import { hasPerm, yuan, toCents } from '../../session'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const loading = ref(false)

const registerVisible = ref(false)
const rechargeVisible = ref(false)
const issueVisible = ref(false)
const detailVisible = ref(false)
const detailTab = ref('points')

const current = ref(null)
const detail = ref(null)
const pointsLog = ref([])
const walletLog = ref([])
const coupons = ref([])
const templates = ref([])
const couponId = ref(null)
const rechargeYuan = ref(200)
const form = reactive({ name: '', phone: '', levelId: 1 })

function levelType(name) {
  if (name?.includes('金')) return 'warning'
  if (name?.includes('银')) return ''
  return 'info'
}

async function load() {
  loading.value = true
  try {
    const res = await api.memberPage({ keyword: keyword.value || undefined, page: page.value, size: size.value })
    rows.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function openRegister() {
  Object.assign(form, { name: '', phone: '', levelId: 1 })
  registerVisible.value = true
}

async function doRegister() {
  if (!/^1[3-9]\d{9}$/.test(form.phone)) {
    ElMessage.warning('手机号格式不对')
    return
  }
  try {
    await api.memberRegister({ name: form.name, phone: form.phone, levelId: form.levelId })
    ElMessage.success('开卡成功')
    registerVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openRecharge(row) {
  current.value = row
  rechargeYuan.value = 200
  rechargeVisible.value = true
}

async function doRecharge() {
  try {
    await api.memberRecharge(current.value.id, toCents(rechargeYuan.value))
    ElMessage.success('充值成功')
    rechargeVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function openIssue(row) {
  current.value = row
  couponId.value = null
  try {
    templates.value = await api.couponTemplates()
  } catch (e) {
    templates.value = []
  }
  issueVisible.value = true
}

async function doIssue() {
  if (!couponId.value) {
    ElMessage.warning('请选择券')
    return
  }
  try {
    await api.issueCoupon(current.value.id, couponId.value)
    ElMessage.success('已发放')
    issueVisible.value = false
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function openDetail(row) {
  current.value = row
  detailVisible.value = true
  detailTab.value = 'points'
  try {
    detail.value = await api.memberDetail(row.id)
    pointsLog.value = await api.memberPoints(row.id)
    walletLog.value = await api.memberWallet(row.id)
    coupons.value = await api.memberCoupons(row.id, false)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(load)
</script>

<style scoped>
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.quick {
  display: flex;
  gap: 6px;
  margin-top: 10px;
}
</style>
