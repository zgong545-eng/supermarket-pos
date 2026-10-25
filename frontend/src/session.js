import { reactive } from 'vue'
import { api } from './api'

/**
 * 登录状态。
 *
 * 没有引入 Pinia：这个项目要共享的状态只有"当前用户"和"token"，
 * 一个 reactive 对象就够了。多装一个库，组员就多学一个概念。
 *
 * token 存 localStorage：三端共用同一浏览器时互不干扰，
 * 但注意收银端和会员端是不同账号体系，切换端要清一下（logout 会做）。
 */
const TOKEN_KEY = 'pos_token'
const USER_KEY = 'pos_user'

function readUser() {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? JSON.parse(raw) : null
  } catch (e) {
    return null
  }
}

export const session = reactive({
  token: localStorage.getItem(TOKEN_KEY) || '',
  user: readUser()
})

export function setSession(data) {
  session.token = data.token || ''
  session.user = {
    userId: data.userId,
    username: data.username,
    realName: data.realName,
    storeId: data.storeId,
    roles: data.roles || [],
    permissions: data.permissions || [],
    memberId: data.memberId,
    memberName: data.name,
    terminal: data.terminal
  }
  localStorage.setItem(TOKEN_KEY, session.token)
  localStorage.setItem(USER_KEY, JSON.stringify(session.user))
}

export function clearSession() {
  session.token = ''
  session.user = null
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

/** 有没有某个权限码。ADMIN 全放行（后端也是这么判的） */
export function hasPerm(code) {
  const u = session.user
  if (!u) return false
  if ((u.roles || []).includes('ADMIN')) return true
  return (u.permissions || []).includes(code)
}

export function hasAnyPerm(codes) {
  return codes.some((c) => hasPerm(c))
}

export function isMember() {
  return (session.user?.roles || []).includes('MEMBER')
}

export function isManager() {
  const roles = session.user?.roles || []
  return roles.includes('ADMIN') || roles.includes('MANAGER')
}

/** 登录后该去哪个端 */
export function landingPath() {
  if (!session.user) return '/login'
  if (isMember()) return '/h5/home'
  const roles = session.user.roles || []
  if (roles.includes('CASHIER') && !roles.includes('ADMIN') && !roles.includes('MANAGER')) {
    return '/pos/cashier'
  }
  return '/admin/dashboard'
}

export async function doLogin(username, password, terminal = 'ADMIN') {
  const data = await api.login({ username, password })
  setSession({ ...data, terminal })
  // 拿一次完整权限（登录接口已经返回，这一步是兜底：老 token 换新权限）
  return data
}

export async function doLogout() {
  try {
    await api.logout()
  } catch (e) {
    // 后端无状态，登出失败也要清本地
  }
  clearSession()
}

export async function doH5Login(phone, code) {
  const data = await api.h5Login(phone, code)
  setSession({ ...data, realName: data.name, terminal: 'H5' })
  return data
}

// ============================================================
//  金额 / 数量格式化（分 → 元）
// ============================================================

export function yuan(cents) {
  if (cents === null || cents === undefined || cents === '') return '0.00'
  return (Number(cents) / 100).toFixed(2)
}

/** 输入框里的"元"转"分"，避免浮点误差（19.99 * 100 = 1998.9999） */
export function toCents(yuanValue) {
  const n = Number(yuanValue)
  if (Number.isNaN(n)) return 0
  return Math.round(n * 100)
}

export function clamp(n, min, max) {
  return Math.min(Math.max(n, min), max)
}
