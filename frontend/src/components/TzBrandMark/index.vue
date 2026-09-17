<template>
  <div class="tz-brand-mark" :class="{ 'is-compact': compact }" aria-label="田诊助手">
    <img :src="brandLogo" alt="" aria-hidden="true">
  </div>
</template>

<script setup>
import brandLogo from '@/assets/logo/tianzhen-logo.svg'

defineProps({ compact: Boolean })
</script>

<style scoped>
.tz-brand-mark { display: inline-flex; width: 36px; height: 36px; flex: 0 0 auto; }
.tz-brand-mark.is-compact { width: 30px; height: 30px; }
img { display: block; width: 100%; height: 100%; object-fit: contain; }
</style>
