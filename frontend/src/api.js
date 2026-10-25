/**
 * 统一请求封装。
 *
 * 后端所有接口都返回 { code, message, data }：code=0 才算成功。
 * 这里把它拆开，业务代码直接拿到 data，不用每处判断 code。
 *
 * 401 的处理：清 token 并跳登录页。注意不能抛异常后不管，
 * 否则页面会卡在"加载中"——所以统一 reject，调用方 catch 里可以静默。
 */
const BASE = import.meta.env.VITE_API_BASE || '/api'

export function getToken() {
  return localStorage.getItem('pos_token') || ''
}

function qs(params) {
  if (!params) return ''
  const usable = Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== '')
  if (!usable.length) return ''
  return '?' + usable.map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`).join('&')
}

export async function request(path, options = {}) {
  const { method = 'GET', body, params, raw } = options
  const headers = {}
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  const res = await fetch(BASE + path + qs(params), {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  })

  if (res.status === 401) {
    localStorage.removeItem('pos_token')
    localStorage.removeItem('pos_user')
    if (!location.hash.includes('/login')) {
      location.hash = '#/login'
    }
    throw new Error('登录已过期')
  }

  let payload
  try {
    payload = await res.json()
  } catch (e) {
    throw new Error(`服务端返回异常（HTTP ${res.status}）`)
  }

  if (raw) return payload
  if (payload.code !== 0) {
    throw new Error(payload.message || '请求失败')
  }
  return payload.data
}

export const http = {
  get: (p, params) => request(p, { params }),
  post: (p, body, params) => request(p, { method: 'POST', body, params }),
  put: (p, body, params) => request(p, { method: 'PUT', body, params }),
  del: (p) => request(p, { method: 'DELETE' })
}

/** 接口清单集中在这里，页面里不出现裸字符串路径，改地址只改一处 */
export const api = {
  // 登录
  login: (body) => http.post('/auth/login', body),
  me: () => http.get('/auth/me'),
  logout: () => http.post('/auth/logout'),

  // 商品
  goodsPage: (params) => http.get('/goods/page', params),
  goodsDetail: (id) => http.get(`/goods/${id}`),
  goodsByBarcode: (code) => http.get(`/goods/barcode/${code}`),
  goodsCreate: (body) => http.post('/goods', body),
  goodsUpdate: (id, body) => http.put(`/goods/${id}`, body),
  goodsChangePrice: (id, newPrice, reason) => http.put(`/goods/${id}/price`, null, { newPrice, reason }),
  categories: () => http.get('/goods/categories'),

  // 库存
  stockPage: (params) => http.get('/stock/page', params),
  stockWarning: (limit = 50) => http.get('/stock/warning', { limit }),
  stockReplenish: (items, remark) => http.post('/stock/replenish', items, { remark }),
  stockLoss: (items, remark) => http.post('/stock/loss', items, { remark }),
  stockCheck: (items, remark) => http.post('/stock/check', items, { remark }),
  stockBills: (params) => http.get('/stock/bill/page', params),
  stockLogs: (skuId) => http.get(`/stock/logs/${skuId}`),
  stockInvariants: () => http.get('/stock/invariants'),

  // 会员
  memberPage: (params) => http.get('/member/page', params),
  memberLookup: (key) => http.get('/member/lookup', { key }),
  memberDetail: (id) => http.get(`/member/${id}`),
  memberPoints: (id) => http.get(`/member/${id}/points-log`),
  memberWallet: (id) => http.get(`/member/${id}/wallet-log`),
  memberCoupons: (id, usableOnly = true) => http.get(`/member/${id}/coupons`, { usableOnly }),
  memberRegister: (params) => http.post('/member/register', null, params),
  memberRecharge: (id, amount) => http.post(`/member/${id}/recharge`, null, { amount }),
  couponTemplates: () => http.get('/member/coupon-templates'),
  issueCoupon: (memberId, couponId) => http.post(`/member/coupons/${couponId}/claim`, null, { memberId }),

  // 促销
  promotionCalculate: (cart) => http.post('/promotion/calculate', cart),
  promotionActivities: () => http.get('/promotion/activities'),
  promotionCreateActivity: (body) => http.post('/promotion/activities', body),
  promotionToggleActivity: (id, status) =>
    http.put(`/promotion/activities/${id}/status`, null, { status }),
  promotionCoupons: () => http.get('/promotion/coupons'),
  promotionCreateCoupon: (body) => http.post('/promotion/coupons', body),

  // 订单
  orderCreate: (cart) => http.post('/order/create', cart),
  orderDetail: (no) => http.get(`/order/${no}`),
  orderPage: (params) => http.get('/order/page', params),
  orderCancel: (no, reason = '收银台取消') => http.post(`/order/${no}/cancel`, null, { reason }),
  orderRefund: (no, reason = '顾客退货') => http.post(`/order/${no}/refund`, null, { reason }),
  orderToday: () => http.get('/order/today-summary'),

  // 支付
  payCreate: (orderNo, channel) => http.post('/pay/create', null, { orderNo, channel }),
  payCash: (orderNo, tendered) => http.post('/pay/cash', null, { orderNo, tendered }),
  payMockCallback: (payNo, success = true, tradeNo, delayMs = 0) =>
    http.post('/pay/mock-callback', null, { payNo, success, tradeNo, delayMs }),
  payByOrder: (orderNo) => http.get(`/pay/${orderNo}`),

  // 交班 / 挂单
  shiftOpen: (openingCash, deviceCode) => http.post('/shift/open', null, { openingCash, deviceCode }),
  shiftClose: (closingCash, remark) => http.post('/shift/close', null, { closingCash, remark }),
  shiftCurrent: () => http.get('/shift/current'),
  shiftPage: (params) => http.get('/shift/page', params),
  shiftDetail: (id) => http.get(`/shift/${id}`),
  shiftOrders: (id) => http.get(`/shift/${id}/orders`),
  holdList: () => http.get('/hold/list'),
  holdCreate: (cart) => http.post('/hold', cart),
  holdResume: (no) => http.post(`/hold/${no}/resume`),
  holdCancel: (no) => http.post(`/hold/${no}/cancel`),

  // 报表
  reportOverview: (days = 7) => http.get('/report/overview', { days }),
  reportMember: (days = 30) => http.get('/report/member', { days }),
  reportRebuild: (days = 30) => http.post('/report/rebuild', null, { days }),

  // 管理端
  dashboard: () => http.get('/admin/dashboard'),
  userPage: (keyword) => http.get('/admin/user/page', { keyword }),
  userCreate: (body) => http.post('/admin/user', body),
  userUpdate: (id, body) => http.put(`/admin/user/${id}`, body),
  userResetPwd: (id, password) => http.post(`/admin/user/${id}/password`, null, { password }),
  userStatus: (id, status) => http.post(`/admin/user/${id}/status`, null, { status }),
  userDelete: (id) => http.del(`/admin/user/${id}`),
  userAssignRoles: (id, roleIds) => http.post(`/admin/user/${id}/roles`, roleIds),
  roleList: () => http.get('/admin/role/list'),
  rolePerms: (id) => http.get(`/admin/role/${id}/permissions`),
  roleAssignPerms: (id, ids) => http.post(`/admin/role/${id}/permissions`, ids),
  permTree: () => http.get('/admin/permission/tree'),
  dictAll: () => http.get('/admin/dict/all'),
  dictTypes: () => http.get('/admin/dict/types'),
  dictItems: (dictType) => http.get('/admin/dict/items', { dictType }),
  dictSaveItem: (body) => http.post('/admin/dict/item', body),
  dictItemStatus: (id, status) => http.post(`/admin/dict/item/${id}/status`, null, { status }),
  storeList: () => http.get('/admin/store/list'),
  storeSave: (body) => http.post('/admin/store', body),
  configList: () => http.get('/admin/config/list'),
  configSave: (key, value) => http.post('/admin/config', null, { key, value }),
  deviceList: () => http.get('/admin/device/list'),
  deviceSave: (body) => http.post('/admin/device', body),
  deviceStatus: (id, status) => http.post(`/admin/device/${id}/status`, null, { status }),
  operLogs: (params) => http.get('/admin/log/oper', params),
  loginLogs: (params) => http.get('/admin/log/login', params),

  // 演示数据
  demoReset: () => http.post('/demo/reset'),
  demoStatus: () => http.get('/demo/status'),
  demoSeedOrders: (days = 30, total = 300) => http.post('/demo/seed-orders', null, { days, total }),

  // 会员端
  h5Login: (phone, code) => http.post('/h5/auth/login', { phone, code }),
  h5Home: () => http.get('/h5/home'),
  h5Categories: () => http.get('/h5/categories'),
  h5Goods: (params) => http.get('/h5/goods', params),
  h5GoodsDetail: (id) => http.get(`/h5/goods/${id}`),
  h5Calculate: (cart) => http.post('/h5/cart/calculate', cart),
  h5CreateOrder: (cart) => http.post('/h5/order', cart),
  h5Pay: (orderNo, channel = 'WECHAT') => http.post(`/h5/order/${orderNo}/pay`, null, { channel }),
  h5Cancel: (orderNo) => http.post(`/h5/order/${orderNo}/cancel`),
  h5Orders: (status) => http.get('/h5/orders', { status }),
  h5OrderDetail: (no) => http.get(`/h5/orders/${no}`),
  h5Me: () => http.get('/h5/me'),
  h5Points: () => http.get('/h5/me/points'),
  h5Wallet: () => http.get('/h5/me/wallet'),
  h5Coupons: (onlyUsable) => http.get('/h5/me/coupons', { onlyUsable })
}
