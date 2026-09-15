<template>
  <div class="m-page">
    <div class="m-section" style="margin-top:12px">
      <div class="m-section-title">复查任务</div>

      <div v-if="loading" class="m-tip">加载中…</div>
      <div v-else-if="!list.length" class="m-empty">当前没有复查任务</div>

      <div v-for="t in list" :key="t.taskId" class="m-card">
        <div class="m-row">
          <span class="m-strong">{{ t.taskTitle || '复查任务' }}</span>
          <span class="m-tag" :class="t.status === '2' ? 'is-ok' : 'is-mid'">
            {{ t.status === '2' ? '已完成' : '待复查' }}
          </span>
        </div>
        <div class="m-muted">地块：{{ t.plotName || '—' }}</div>
        <div class="m-muted">截止：{{ t.dueDate || '—' }}</div>
        <div class="m-muted" v-if="t.note">{{ t.note }}</div>

        <div class="m-btn-row" v-if="t.status !== '2'">
          <button class="m-btn is-plain" @click="finish(t)">标记完成</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listFollowup, changeFollowupStatus } from '@/api/tianzhen/followup'

const list = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await listFollowup({ pageNum: 1, pageSize: 50 })
    list.value = res.rows || []
  } finally {
    loading.value = false
  }
}

async function finish(t) {
  try {
    await changeFollowupStatus({ taskId: t.taskId, status: '2' })
    ElMessage.success('已标记完成')
    await load()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

onMounted(load)
</script>
