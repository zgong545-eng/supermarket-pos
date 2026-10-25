<template>
  <div class="page-card">
    <el-tabs v-model="tab">
      <!-- 字典 -->
      <el-tab-pane label="数据字典" name="dict">
        <div class="split">
          <div class="side">
            <div class="side-title">字典类型</div>
            <div v-for="t in dictTypes" :key="t.dictType" class="type-item"
                 :class="{ on: t.dictType === activeType }" @click="pickType(t.dictType)">
              <span>{{ t.dictName }}</span>
              <span class="muted">{{ t.itemCount }}</span>
            </div>
          </div>
          <div class="main">
            <div class="toolbar">
              <span class="muted">类型：{{ activeType || '请选择' }}</span>
              <div class="spacer" />
              <el-button size="small" type="primary" :disabled="!activeType" @click="openDictItem">
                新增字典项
              </el-button>
            </div>
            <el-table :data="dictItems" size="small" border>
              <el-table-column prop="itemValue" label="值" width="160" />
              <el-table-column prop="itemLabel" label="显示名" />
              <el-table-column prop="sort" label="排序" width="80" />
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-switch :model-value="row.status === 1" @change="(v) => toggleItem(row, v)" />
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </el-tab-pane>

      <!-- 系统参数 -->
      <el-tab-pane label="系统参数" name="config">
        <p class="muted">这些值参与业务计算（积分抵扣上限、挂单超时等），改错了会影响算钱，改完建议跑一次收银流程验证。</p>
        <el-table :data="configs" size="small" border>
          <el-table-column prop="configKey" label="参数名" width="280" />
          <el-table-column label="值" width="220">
            <template #default="{ row }">
              <el-input v-model="row.configValue" size="small" />
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="说明" min-width="220" />
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button link type="primary" @click="saveConfig(row)">保存</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 门店 -->
      <el-tab-pane label="门店" name="store">
        <el-table :data="stores" size="small" border>
          <el-table-column prop="code" label="编码" width="120" />
          <el-table-column prop="name" label="名称" min-width="160" />
          <el-table-column prop="address" label="地址" min-width="200" />
          <el-table-column prop="phone" label="电话" width="140" />
          <el-table-column prop="businessHours" label="营业时间" width="140" />
        </el-table>
        <p class="muted" style="margin-top: 10px">
          演示版只有一家店。库存、订单、班次都带 store_id，多店只需加数据，代码不用改。
        </p>
      </el-tab-pane>

      <!-- 外设 -->
      <el-tab-pane label="收银外设" name="device">
        <el-table :data="devices" size="small" border>
          <el-table-column prop="code" label="编号" width="130" />
          <el-table-column prop="name" label="设备名" min-width="160" />
          <el-table-column label="类型" width="120">
            <template #default="{ row }">{{ devType(row.type) }}</template>
          </el-table-column>
          <el-table-column prop="storeName" label="门店" width="150" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-switch :model-value="row.status === 1" @change="(v) => toggleDevice(row, v)" />
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="160" />
        </el-table>
        <p class="muted" style="margin-top: 10px">
          课堂演示不接真设备，这里只做登记。答辩如果被问"扫码枪怎么接"，回答：浏览器端扫码枪等同键盘输入，
          回车触发查询，所以不需要驱动。
        </p>
      </el-tab-pane>

      <!-- 日志 -->
      <el-tab-pane label="日志" name="log">
        <div class="toolbar">
          <el-input v-model="logKeyword" placeholder="用户名 / 动作 / 接口" clearable style="width: 240px" />
          <el-button @click="loadOperLogs">查询操作日志</el-button>
          <el-button @click="loadLoginLogs">查看登录日志</el-button>
        </div>
        <el-table v-if="logMode === 'oper'" :data="operLogs" size="small" border height="440">
          <el-table-column prop="username" label="用户" width="110" />
          <el-table-column prop="module" label="模块" width="110" />
          <el-table-column prop="action" label="动作" min-width="180" />
          <el-table-column prop="uri" label="接口" min-width="200" />
          <el-table-column prop="costMs" label="耗时(ms)" width="100" />
          <el-table-column label="结果" width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="row.success === 1 ? 'success' : 'danger'">
                {{ row.success === 1 ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="160">
            <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
        <el-table v-else :data="loginLogs" size="small" border height="440">
          <el-table-column prop="username" label="用户" width="130" />
          <el-table-column prop="ip" label="IP" width="140" />
          <el-table-column label="结果" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.success === 1 ? 'success' : 'danger'">
                {{ row.success === 1 ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="msg" label="说明" min-width="180" />
          <el-table-column label="时间" width="160">
            <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="dictVisible" title="新增字典项" width="420px">
      <el-form label-width="90px">
        <el-form-item label="类型"><el-input v-model="dictForm.dictType" disabled /></el-form-item>
        <el-form-item label="值"><el-input v-model="dictForm.itemValue" /></el-form-item>
        <el-form-item label="显示名"><el-input v-model="dictForm.itemLabel" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="dictForm.sort" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dictVisible = false">取消</el-button>
        <el-button type="primary" @click="saveDictItem">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api'

const tab = ref('dict')
const dictTypes = ref([])
const dictItems = ref([])
const activeType = ref('')
const configs = ref([])
const stores = ref([])
const devices = ref([])
const operLogs = ref([])
const loginLogs = ref([])
const logKeyword = ref('')
const logMode = ref('oper')
const dictVisible = ref(false)

const dictForm = reactive({ dictType: '', itemValue: '', itemLabel: '', sort: 0 })

const DEV = { SCANNER: '扫码枪', PRINTER: '小票机', SCALE: '电子秤', DRAWER: '钱箱' }

function devType(t) {
  return DEV[t] || t
}

function fmt(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : ''
}

async function loadDictTypes() {
  try {
    dictTypes.value = await api.dictTypes()
    if (!activeType.value && dictTypes.value.length) pickType(dictTypes.value[0].dictType)
  } catch (e) {
    dictTypes.value = []
  }
}

async function pickType(type) {
  activeType.value = type
  try {
    dictItems.value = await api.dictItems(type)
  } catch (e) {
    dictItems.value = []
  }
}

function openDictItem() {
  Object.assign(dictForm, { dictType: activeType.value, itemValue: '', itemLabel: '', sort: 0 })
  dictVisible.value = true
}

async function saveDictItem() {
  try {
    await api.dictSaveItem({ ...dictForm })
    ElMessage.success('已保存')
    dictVisible.value = false
    pickType(activeType.value)
    loadDictTypes()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function toggleItem(row, v) {
  try {
    await api.dictItemStatus(row.id, v ? 1 : 0)
    pickType(activeType.value)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function loadConfigs() {
  try {
    configs.value = await api.configList()
  } catch (e) {
    configs.value = []
  }
}

async function saveConfig(row) {
  try {
    await api.configSave(row.configKey, row.configValue)
    ElMessage.success('已保存（下一次算钱生效）')
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function loadStores() {
  try {
    stores.value = await api.storeList()
  } catch (e) {
    stores.value = []
  }
}

async function loadDevices() {
  try {
    devices.value = await api.deviceList()
  } catch (e) {
    devices.value = []
  }
}

async function toggleDevice(row, v) {
  try {
    await api.deviceStatus(row.id, v ? 1 : 0)
    loadDevices()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function loadOperLogs() {
  logMode.value = 'oper'
  try {
    operLogs.value = await api.operLogs({ keyword: logKeyword.value || undefined, page: 1, size: 100 })
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function loadLoginLogs() {
  logMode.value = 'login'
  try {
    loginLogs.value = await api.loginLogs({ page: 1, size: 100 })
  } catch (e) {
    ElMessage.error(e.message)
  }
}

watch(tab, (v) => {
  if (v === 'config') loadConfigs()
  if (v === 'store') loadStores()
  if (v === 'device') loadDevices()
  if (v === 'log') loadOperLogs()
})

onMounted(loadDictTypes)
</script>

<style scoped>
.split {
  display: flex;
  gap: 14px;
}

.side {
  width: 200px;
  border-right: 1px solid var(--pos-border);
  padding-right: 10px;
}

.side-title {
  font-size: 13px;
  color: var(--pos-muted);
  margin-bottom: 8px;
}

.type-item {
  display: flex;
  justify-content: space-between;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
}

.type-item:hover {
  background: #f5f6f8;
}

.type-item.on {
  background: rgba(200, 16, 46, 0.08);
  color: var(--pos-primary);
  font-weight: 600;
}

.main {
  flex: 1;
  min-width: 0;
}
</style>
