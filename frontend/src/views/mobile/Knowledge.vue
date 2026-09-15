<template>
  <div class="m-page">
    <div class="m-section" style="margin-top:12px">
      <input v-model="keyword" class="m-input" placeholder="搜索病害 / 虫害 / 缺素，如：溃疡病、红蜘蛛" @input="search" />

      <div v-if="loading" class="m-tip">加载中…</div>
      <div v-else-if="!list.length" class="m-empty">没有找到相关条目</div>

      <div
        v-for="k in list"
        :key="k.knowledgeId"
        class="m-card m-card-link"
        @click="go('/m/knowledge/' + k.knowledgeId)"
      >
        <div class="m-row">
          <span class="m-strong">{{ k.diseaseName }}</span>
          <span class="m-tag">{{ categoryText(k.category) }}</span>
        </div>
        <!-- 手机上优先展示「特征性表现」：它比完整症状短，也更好判断 -->
        <div class="m-muted m-clamp">{{ k.keyFeatures || k.symptoms }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listKnowledge } from '@/api/tianzhen/knowledge'

const router = useRouter()
const list = ref([])
const loading = ref(false)
const keyword = ref('')
let timer = null

function categoryText(c) {
  return { '1': '病害', '2': '虫害', '3': '营养问题' }[String(c)] || '—'
}
function go(path) { router.push(path) }

async function load() {
  loading.value = true
  try {
    const res = await listKnowledge({ pageNum: 1, pageSize: 100, diseaseName: keyword.value })
    list.value = res.rows || []
  } finally {
    loading.value = false
  }
}

// 输入防抖：每敲一个字就打一次接口，在手机上既费流量又容易卡顿
function search() {
  clearTimeout(timer)
  timer = setTimeout(load, 300)
}

onMounted(load)
</script>

<style scoped>
.m-clamp {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
