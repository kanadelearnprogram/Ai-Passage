<template>
  <a-layout class="basic-layout">
    <!-- 顶部导航栏 -->
    <GlobalHeader />
    <!-- 主要内容区域 -->
    <a-layout-content class="main-content">
      <router-view />
    </a-layout-content>

    <!-- 底部版权信息 -->
    <GlobalFooter />
  </a-layout>

</template>

<script setup lang="ts">
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'
</script>

<style scoped>
.basic-layout {
  background: none;
  /* sticky footer：内容少时 footer 也贴住视口底部 */
  min-height: 100vh;
}

.main-content {
  width: 100%;
  padding: 0;
  background: none;
  margin: 0;
  /* 撑满剩余高度，将 footer 推至底部 */
  flex: 1;
}
</style>
