<template>
  <div class="page-card">
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="商品名 / 编码 / 条码" clearable style="width: 260px"
                @keyup.enter="load" />
      <el-select v-model="categoryId" placeholder="全部分类" clearable style="width: 170px" @change="load">
        <el-option v-for="c in flatCategories" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="status" placeholder="全部状态" clearable style="width: 130px" @change="load">
        <el-option label="上架" :value="1" />
        <el-option label="下架" :value="0" />
      </el-select>
      <el-button type="primary" @click="load">查询</el-button>
      <div class="spacer" />
      <el-button type="primary" v-if="canEdit" @click="openCreate">新增商品</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" size="small" border>
      <el-table-column prop="skuCode" label="编码" width="120" />
      <el-table-column prop="name" label="商品名称" min-width="180" />
      <el-table-column prop="spec" label="规格" width="90" />
      <el-table-column prop="unit" label="单位" width="70" />
      <el-table-column label="标准价" width="100">
        <template #default="{ row }">{{ yuan(row.standardPrice) }}</template>
      </el-table-column>
      <el-table-column label="会员价" width="100">
        <template #default="{ row }">{{ row.memberPrice ? yuan(row.memberPrice) : '按等级' }}</template>
      </el-table-column>
      <el-table-column label="成本" width="90">
        <template #default="{ row }">{{ yuan(row.costPrice) }}</template>
      </el-table-column>
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.weightType === 'WEIGHT' ? 'warning' : 'info'">
            {{ row.weightType === 'WEIGHT' ? '称重' : '计件' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? '上架' : '下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" v-if="canEdit" @click="openEdit(row)">编辑</el-button>
          <el-button link type="warning" v-if="canPrice" @click="openPrice(row)">改价</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination v-model:current-page="page" :page-size="size" :total="total"
                     layout="total, prev, pager, next" background @current-change="load" />
    </div>

    <!-- 新增 / 编辑 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑商品' : '新增商品'" width="620px">
      <el-form :model="form" label-width="96px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="商品编码">
              <el-input v-model="form.skuCode" placeholder="留空自动生成" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="主条码">
              <el-input v-model="form.barcode" placeholder="8-14 位数字" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="商品名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="分类">
              <el-select v-model="form.categoryId" style="width: 100%">
                <el-option v-for="c in flatCategories" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="规格">
              <el-input v-model="form.spec" placeholder="如 500ml" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="标准价">
              <el-input-number v-model="priceYuan.standard" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="会员价">
              <el-input-number v-model="priceYuan.member" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="成本价">
              <el-input-number v-model="priceYuan.cost" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="单位">
              <el-input v-model="form.unit" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="计价方式">
              <el-select v-model="form.weightType" style="width: 100%">
                <el-option label="计件" value="PIECE" />
                <el-option label="称重" value="WEIGHT" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态">
              <el-select v-model="form.status" style="width: 100%">
                <el-option label="上架" :value="1" />
                <el-option label="下架" :value="0" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 改价 -->
    <el-dialog v-model="priceVisible" title="调整售价" width="420px">
      <p class="muted">
        {{ priceTarget?.name }}：当前 {{ yuan(priceTarget?.standardPrice) }} 元。
        改价会留痕（谁、什么时候、为什么），不是直接 UPDATE。
      </p>
      <el-form label-width="90px">
        <el-form-item label="新价格">
          <el-input-number v-model="newPriceYuan" :min="0" :precision="2" style="width: 180px" />
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="priceReason" placeholder="如：供应商调价" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="priceVisible = false">取消</el-button>
        <el-button type="primary" @click="doChangePrice">确认改价</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api'
import { hasPerm, yuan, toCents } from '../../session'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const categoryId = ref(null)
const status = ref(null)
const loading = ref(false)
const saving = ref(false)

const categories = ref([])
const formVisible = ref(false)
const priceVisible = ref(false)
const priceTarget = ref(null)
const newPriceYuan = ref(0)
const priceReason = ref('')

const canEdit = computed(() => hasPerm('goods:edit'))
const canPrice = computed(() => hasPerm('goods:price'))

const form = reactive({
  id: null, skuCode: '', barcode: '', name: '', categoryId: null, spec: '',
  unit: '件', brand: '', weightType: 'PIECE', status: 1, remark: ''
})
const priceYuan = reactive({ standard: 0, member: 0, cost: 0 })

const flatCategories = computed(() => {
  const out = []
  const walk = (list, depth = 0) => {
    for (const c of list || []) {
      out.push({ id: c.id, name: (depth ? '　'.repeat(depth) : '') + c.name })
      if (c.children?.length) walk(c.children, depth + 1)
    }
  }
  walk(categories.value)
  return out
})

async function load() {
  loading.value = true
  try {
    const res = await api.goodsPage({
      keyword: keyword.value || undefined,
      categoryId: categoryId.value || undefined,
      status: status.value ?? undefined,
      page: page.value,
      size: size.value
    })
    rows.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  try {
    categories.value = await api.categories()
  } catch (e) {
    categories.value = []
  }
}

function openCreate() {
  Object.assign(form, {
    id: null, skuCode: '', barcode: '', name: '', categoryId: flatCategories.value[0]?.id || null,
    spec: '', unit: '件', brand: '', weightType: 'PIECE', status: 1, remark: ''
  })
  Object.assign(priceYuan, { standard: 0, member: 0, cost: 0 })
  formVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    skuCode: row.skuCode,
    barcode: row.barcode,
    name: row.name,
    categoryId: row.categoryId,
    spec: row.spec,
    unit: row.unit,
    brand: row.brand,
    weightType: row.weightType,
    status: row.status,
    remark: row.remark
  })
  Object.assign(priceYuan, {
    standard: Number((row.standardPrice / 100).toFixed(2)),
    member: row.memberPrice ? Number((row.memberPrice / 100).toFixed(2)) : 0,
    cost: Number((row.costPrice / 100).toFixed(2))
  })
  formVisible.value = true
}

async function save() {
  if (!form.name) {
    ElMessage.warning('商品名称必填')
    return
  }
  saving.value = true
  try {
    const payload = {
      ...form,
      standardPrice: toCents(priceYuan.standard),
      memberPrice: priceYuan.member ? toCents(priceYuan.member) : null,
      costPrice: toCents(priceYuan.cost)
    }
    if (form.id) {
      await api.goodsUpdate(form.id, payload)
      ElMessage.success('已保存')
    } else {
      await api.goodsCreate(payload)
      ElMessage.success('已新增')
    }
    formVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

function openPrice(row) {
  priceTarget.value = row
  newPriceYuan.value = Number((row.standardPrice / 100).toFixed(2))
  priceReason.value = ''
  priceVisible.value = true
}

async function doChangePrice() {
  try {
    await api.goodsChangePrice(priceTarget.value.id, toCents(newPriceYuan.value), priceReason.value)
    ElMessage.success('已改价并留痕')
    priceVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(() => {
  loadCategories()
  load()
})
</script>

<style scoped>
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
