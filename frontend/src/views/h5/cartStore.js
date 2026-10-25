import { reactive, watch } from 'vue'

/**
 * 会员端购物车。
 *
 * 只放前端：改数量、加商品不打扰后端。
 * 但"算钱"一定调后端（/h5/cart/calculate）——优惠规则在后端，
 * 前端自己算一份迟早对不上，这是电商里最常见的线上事故之一。
 */
const KEY = 'pos_h5_cart'

function read() {
  try {
    const raw = localStorage.getItem(KEY)
    return raw ? JSON.parse(raw) : []
  } catch (e) {
    return []
  }
}

export const cart = reactive({
  items: read(),
  dark: false
})

watch(
  () => cart.items,
  (v) => localStorage.setItem(KEY, JSON.stringify(v)),
  { deep: true }
)

export function addItem(goods, weight) {
  const weighed = !!weight
  const same = cart.items.find((i) => i.skuId === goods.skuId && i.isWeighed === weighed)
  if (same && !weighed) {
    same.qty += 1
    return
  }
  cart.items.push({
    skuId: goods.skuId,
    name: goods.name,
    spec: goods.spec,
    unit: goods.unit,
    price: goods.price,
    isWeighed: weighed,
    weight: weighed ? weight : null,
    qty: weighed ? Math.max(1, Math.round(weight * 1000)) : 1
  })
}

export function setQty(idx, qty) {
  if (qty <= 0) {
    cart.items.splice(idx, 1)
    return
  }
  cart.items[idx].qty = qty
}

export function removeItem(idx) {
  cart.items.splice(idx, 1)
}

export function clearCart() {
  cart.items = []
}

export function totalCount() {
  return cart.items.reduce((s, i) => s + i.qty, 0)
}

/** 转成后端要的 CartRequest.items */
export function toRequestItems() {
  return cart.items.map((i) => ({
    skuId: i.skuId,
    qty: i.qty,
    weight: i.isWeighed ? i.weight : null
  }))
}
