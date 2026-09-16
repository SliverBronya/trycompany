<template>
  <!-- 桌面端要「点击放大预览」，所以那里用 preview 模式走 el-image；
       手机端直接渲染 <img> 更轻，也不需要在表格里塞预览组件。 -->
  <el-image
    v-if="preview"
    :src="src"
    :preview-src-list="src ? [src] : []"
    :preview-teleported="true"
    :class="className"
    :style="style"
    fit="cover"
  >
    <template #error>
      <div class="tz-img-fallback"><span>图片加载失败</span></div>
    </template>
  </el-image>

  <img
    v-else-if="src"
    :src="src"
    :alt="alt"
    :class="className"
    :style="style"
    @click="$emit('click')"
  />
  <div v-else-if="failed" class="tz-img-fallback" :style="style">
    <span>图片加载失败</span>
  </div>
</template>

<script setup>
/**
 * 带隧道兼容的图片组件。
 *
 * 为什么不直接用 `<img :src>`：**`<img>` 标签没法带请求头**。
 * 而通过 ngrok 免费版访问时，它会对浏览器型请求插一页警告 HTML ——
 * `<img>` 拿到的就是那页 HTML，显示成裂图；加 `Sec-Fetch-Dest` 之类也没用（实测过）。
 *
 * 所以这里先用带请求头的方式把图取成二进制，再在本地生成 blob 地址给 `<img>` / `el-image` 用。
 * 取不到就退回直连地址 —— 本机、局域网这些不走隧道的情况，直接渲染本来就是好的，
 * 不该因为加了这层反而坏掉。
 */
import { ref, watch, onUnmounted } from 'vue'
import { resolveImageUrl } from '@/utils/tzImage'
import { tunnelHeaders } from '@/utils/tzServer'

const props = defineProps({
  url: { type: String, default: '' },
  alt: { type: String, default: '' },
  className: { type: String, default: '' },
  style: { type: [String, Object], default: '' },
  /** 是否渲染成可点击放大的 el-image（桌面端表格用） */
  preview: { type: Boolean, default: false }
})
defineEmits(['click'])

const src = ref('')
const failed = ref(false)
let blobUrl = null

function revoke() {
  if (blobUrl) {
    URL.revokeObjectURL(blobUrl)
    blobUrl = null
  }
}

async function load() {
  revoke()
  failed.value = false
  const full = resolveImageUrl(props.url)
  if (!full) {
    src.value = ''
    return
  }
  try {
    const res = await fetch(full, { headers: tunnelHeaders() })
    if (!res.ok) throw new Error('HTTP ' + res.status)
    const blob = await res.blob()
    // 警告页也是 200，所以必须校验类型，否则会把 HTML 当图片塞给 <img>
    if (!/^image\//.test(blob.type)) throw new Error('返回的不是图片（可能是隧道的警告页）')
    blobUrl = URL.createObjectURL(blob)
    src.value = blobUrl
  } catch (e) {
    // 退回直连：不走隧道时这条路是好的
    src.value = full
    // 直连也失败才算真失败
    const probe = new Image()
    probe.onerror = () => { failed.value = true; src.value = '' }
    probe.src = full
  }
}

watch(() => props.url, load, { immediate: true })
onUnmounted(revoke)
</script>

<style scoped>
.tz-img-fallback {
  min-height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eef2f0;
  color: #8b9691;
  font-size: 13px;
  border-radius: 12px;
}
</style>
