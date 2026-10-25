<template>
  <div class="login-page">
    <div class="brand-side">
      <h1>会员仓储超市</h1>
      <p class="sub">收银管理系统 · 课程设计演示版</p>
      <ul class="points">
        <li><b>收银端</b>扫码开单、会员挂单、多种支付、交班对账</li>
        <li><b>管理端</b>商品库存、会员营销、销售报表、员工权限</li>
        <li><b>会员端</b>手机下单、积分储值、优惠券</li>
      </ul>
      <div class="tip">
        这是一套能真跑的系统：库存会预占、支付回调做了幂等、优惠按商品分摊。
      </div>
    </div>

    <div class="form-side">
      <div class="card">
        <h2>员工登录</h2>
        <p class="muted">用店内的账号登录。会员请走手机端入口。</p>

        <el-form :model="form" @submit.prevent="submit">
          <el-form-item>
            <el-input v-model="form.username" size="large" placeholder="登录名" :prefix-icon="User" />
          </el-form-item>
          <el-form-item>
            <el-input v-model="form.password" type="password" size="large" placeholder="密码"
                      show-password :prefix-icon="Lock" @keyup.enter="submit" />
          </el-form-item>
          <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="submit">
            登录
          </el-button>
        </el-form>

        <div class="quick">
          <div class="muted">演示账号（点一下自动填）</div>
          <div class="quick-row">
            <el-button size="small" @click="fill('admin', 'admin123')">管理员 admin</el-button>
            <el-button size="small" @click="fill('manager', 'manager123')">经理 manager</el-button>
            <el-button size="small" @click="fill('cashier', 'cashier123')">收银员 cashier</el-button>
          </div>
        </div>

        <div class="foot">
          <router-link to="/h5/login">我是会员，走手机端 →</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { doLogin, landingPath } from '../session'

const router = useRouter()
const loading = ref(false)
const form = reactive({ username: 'admin', password: 'admin123' })

function fill(u, p) {
  form.username = u
  form.password = p
}

async function submit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入登录名和密码')
    return
  }
  loading.value = true
  try {
    await doLogin(form.username, form.password)
    ElMessage.success('登录成功')
    router.replace(landingPath())
  } catch (e) {
    ElMessage.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
}

.brand-side {
  flex: 1.1;
  background: linear-gradient(145deg, #1f2733 0%, #2b3a4d 60%, #3a4d63 100%);
  color: #fff;
  padding: 64px 56px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.brand-side h1 {
  font-size: 34px;
  margin: 0 0 8px;
  letter-spacing: 1px;
}

.sub {
  color: #9fb0c4;
  margin: 0 0 32px;
}

.points {
  list-style: none;
  padding: 0;
  margin: 0 0 32px;
  line-height: 2;
  color: #d6e0ec;
  font-size: 14px;
}

.points b {
  color: #ff7b8c;
  margin-right: 6px;
}

.tip {
  border-left: 3px solid var(--pos-primary);
  padding-left: 12px;
  color: #9fb0c4;
  font-size: 13px;
  max-width: 460px;
  line-height: 1.7;
}

.form-side {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
}

.card {
  width: 340px;
}

.card h2 {
  margin: 0 0 6px;
  font-size: 22px;
}

.card .muted {
  margin: 0 0 22px;
}

.quick {
  margin-top: 22px;
  padding-top: 16px;
  border-top: 1px dashed var(--pos-border);
}

.quick-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.foot {
  margin-top: 18px;
  font-size: 13px;
}

@media (max-width: 860px) {
  .brand-side {
    display: none;
  }
}
</style>
