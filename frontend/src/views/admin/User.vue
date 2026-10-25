<template>
  <div class="page-card">
    <el-tabs v-model="tab">
      <!-- ============ 员工 ============ -->
      <el-tab-pane label="员工" name="user">
        <div class="toolbar">
          <el-input v-model="keyword" placeholder="姓名 / 登录名 / 手机" clearable style="width: 240px"
                    @keyup.enter="loadUsers" />
          <el-button type="primary" @click="loadUsers">查询</el-button>
          <div class="spacer" />
          <el-button type="primary" v-if="canEdit" @click="openCreate">新增员工</el-button>
        </div>

        <el-table :data="users" size="small" border v-loading="loading">
          <el-table-column prop="username" label="登录名" width="130" />
          <el-table-column prop="realName" label="姓名" width="110" />
          <el-table-column prop="phone" label="手机" width="130" />
          <el-table-column prop="roleNames" label="角色" min-width="150" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="最后登录" width="160">
            <template #default="{ row }">{{ fmt(row.lastLoginAt) || '从未登录' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" v-if="canEdit" @click="openEdit(row)">编辑</el-button>
              <el-button link type="warning" v-if="canEdit" @click="openRoles(row)">角色</el-button>
              <el-button link v-if="canEdit" @click="resetPwd(row)">重置密码</el-button>
              <el-button link :type="row.status === 1 ? 'danger' : 'success'" v-if="canEdit"
                         @click="toggle(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ============ 角色 ============ -->
      <el-tab-pane label="角色与权限" name="role">
        <p class="muted">
          角色决定能看到哪些菜单、能调哪些接口。权限码会写进 JWT，所以改完权限需要重新登录才生效。
          内置 ADMIN 角色不允许改——改了可能把所有人关在门外。
        </p>
        <el-table :data="roles" size="small" border v-loading="loading">
          <el-table-column prop="code" label="角色码" width="130" />
          <el-table-column prop="name" label="名称" width="140" />
          <el-table-column prop="remark" label="说明" min-width="200" />
          <el-table-column prop="userCount" label="人数" width="80" />
          <el-table-column prop="permCount" label="权限数" width="90" />
          <el-table-column label="内置" width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="row.isSystem === 1 ? 'warning' : 'info'">
                {{ row.isSystem === 1 ? '是' : '否' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :disabled="row.code === 'ADMIN'"
                         @click="openPerms(row)">分配权限</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 新增 / 编辑员工 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑员工' : '新增员工'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="登录名">
          <el-input v-model="form.username" :disabled="!!form.id" placeholder="3-20 位字母数字下划线" />
        </el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.realName" /></el-form-item>
        <el-form-item label="密码" v-if="!form.id">
          <el-input v-model="form.password" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="手机"><el-input v-model="form.phone" maxlength="11" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色 -->
    <el-dialog v-model="roleVisible" title="分配角色" width="420px">
      <p class="muted">{{ current?.realName }}（{{ current?.username }}）</p>
      <el-checkbox-group v-model="pickedRoles">
        <el-checkbox v-for="r in roles" :key="r.id" :value="r.id" :label="r.id" style="display: block">
          {{ r.name }} · {{ r.remark }}
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRoles">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配权限 -->
    <el-drawer v-model="permVisible" :title="`分配权限 · ${currentRole?.name || ''}`" size="480px">
      <el-tree ref="treeRef" :data="permTree" node-key="id" show-checkbox
               :props="{ label: 'name', children: 'children' }" default-expand-all />
      <template #footer>
        <el-button @click="permVisible = false">取消</el-button>
        <el-button type="primary" @click="savePerms">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../../api'
import { hasPerm } from '../../session'

const tab = ref('user')
const users = ref([])
const roles = ref([])
const permTree = ref([])
const keyword = ref('')
const loading = ref(false)
const formVisible = ref(false)
const roleVisible = ref(false)
const permVisible = ref(false)
const current = ref(null)
const currentRole = ref(null)
const pickedRoles = ref([])
const treeRef = ref(null)

const canEdit = computed(() => hasPerm('sys:user:edit'))

const form = reactive({
  id: null, username: '', realName: '', password: '', phone: '', roleIds: []
})

function fmt(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : ''
}

async function loadUsers() {
  loading.value = true
  try {
    users.value = await api.userPage(keyword.value || undefined)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  try {
    roles.value = await api.roleList()
  } catch (e) {
    roles.value = []
  }
}

function openCreate() {
  Object.assign(form, { id: null, username: '', realName: '', password: '', phone: '', roleIds: [] })
  formVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    username: row.username,
    realName: row.realName,
    password: '',
    phone: row.phone,
    roleIds: (row.roleCodes || [])
      .map((code) => roles.value.find((r) => r.code === code)?.id)
      .filter(Boolean)
  })
  formVisible.value = true
}

async function save() {
  try {
    if (form.id) {
      await api.userUpdate(form.id, {
        realName: form.realName,
        phone: form.phone,
        storeId: 1
      })
      await api.userAssignRoles(form.id, form.roleIds)
      ElMessage.success('已保存')
    } else {
      await api.userCreate({
        username: form.username,
        password: form.password,
        realName: form.realName,
        phone: form.phone,
        storeId: 1,
        roleIds: form.roleIds
      })
      ElMessage.success('员工已创建，可直接用该账号登录')
    }
    formVisible.value = false
    loadUsers()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openRoles(row) {
  current.value = row
  pickedRoles.value = (row.roleCodes || [])
    .map((code) => roles.value.find((r) => r.code === code)?.id)
    .filter(Boolean)
  roleVisible.value = true
}

async function saveRoles() {
  try {
    await api.userAssignRoles(current.value.id, pickedRoles.value)
    ElMessage.success('角色已更新（该用户需重新登录生效）')
    roleVisible.value = false
    loadUsers()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function resetPwd(row) {
  try {
    const { value } = await ElMessageBox.prompt(`给 ${row.realName} 设置新密码`, '重置密码', {
      inputPlaceholder: '至少 6 位',
      inputValidator: (v) => (v && v.length >= 6 ? true : '至少 6 位')
    })
    await api.userResetPwd(row.id, value)
    ElMessage.success('密码已重置')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '已取消')
  }
}

async function toggle(row) {
  try {
    await api.userStatus(row.id, row.status === 1 ? 0 : 1)
    ElMessage.success('已更新')
    loadUsers()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function openPerms(row) {
  currentRole.value = row
  try {
    if (!permTree.value.length) permTree.value = await api.permTree()
    const owned = await api.rolePerms(row.id)
    permVisible.value = true
    await nextTick()
    treeRef.value?.setCheckedKeys(owned || [])
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function savePerms() {
  try {
    // 半选的父节点也要提交，否则父级菜单会丢
    const checked = treeRef.value.getCheckedKeys()
    const half = treeRef.value.getHalfCheckedKeys()
    await api.roleAssignPerms(currentRole.value.id, [...checked, ...half])
    ElMessage.success('权限已更新')
    permVisible.value = false
    loadRoles()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(() => {
  loadUsers()
  loadRoles()
})
</script>
