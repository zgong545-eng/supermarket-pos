<template>
  <div class="h5-scope login">
    <div class="top">
      <div class="logo">会员仓储超市</div>
      <div class="slogan">会员价 · 积分 · 优惠券，一个手机全搞定</div>
    </div>

    <div class="card">
      <van-cell-group inset>
        <van-field v-model="phone" type="tel" label="手机号" placeholder="请输入手机号" maxlength="11" />
        <van-field v-model="code" type="digit" label="验证码" placeholder="演示环境固定 1234" maxlength="6">
          <template #button>
            <van-button size="small" type="primary" plain @click="fillCode">填入 1234</van-button>
          </template>
        </van-field>
      </van-cell-group>

      <div class="btn">
        <van-button type="primary" block round :loading="loading" @click="submit">
          登录 / 自动开通会员
        </van-button>
      </div>

      <p class="tip">
        没注册过的手机号会自动开通普通会员，方便课堂上直接体验。
        真实环境这里要接短信网关，演示版不发短信。
      </p>

      <div class="staff">
        <router-link to="/login">我是员工，走后台入口 →</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { doH5Login } from '../../session'

const router = useRouter()
const phone = ref('13800138000')
const code = ref('1234')
const loading = ref(false)

function fillCode() {
  code.value = '1234'
}

async function submit() {
  if (!/^1[3-9]\d{9}$/.test(phone.value)) {
    showToast('手机号格式不对')
    return
  }
  loading.value = true
  try {
    const r = await doH5Login(phone.value, code.value)
    showToast(r.isNew ? '已为你开通会员' : '欢迎回来')
    router.replace('/h5/home')
  } catch (e) {
    showToast(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login {
  background: linear-gradient(180deg, #c8102e 0%, #a10c24 40%, #fff 40%, #fff 100%);
  padding-top: 40px;
}

.top {
  padding: 0 24px 26px;
  color: #fff;
}

.logo {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 1px;
}

.slogan {
  font-size: 13px;
  opacity: 0.85;
  margin-top: 8px;
}

.card {
  padding: 0 12px;
}

.btn {
  margin: 18px 16px;
}

.tip {
  color: #969799;
  font-size: 12px;
  line-height: 1.7;
  padding: 0 16px;
}

.staff {
  text-align: center;
  margin-top: 30px;
  font-size: 13px;
}
</style>
