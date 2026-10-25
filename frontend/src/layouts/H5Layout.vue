<template>
  <div class="h5-scope">
    <router-view v-slot="{ Component }">
      <component :is="Component" />
    </router-view>

    <van-tabbar route fixed placeholder>
      <van-tabbar-item to="/h5/home" icon="home-o">首页</van-tabbar-item>
      <van-tabbar-item to="/h5/cart" icon="shopping-cart-o" :badge="cartCount || ''">购物车</van-tabbar-item>
      <van-tabbar-item to="/h5/orders" icon="orders-o">订单</van-tabbar-item>
      <van-tabbar-item to="/h5/mine" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { cart } from '../views/h5/cartStore'

// 角标数量来自本地购物车，切端不丢
const cartCount = computed(() => cart.items.reduce((s, i) => s + i.qty, 0))
</script>
