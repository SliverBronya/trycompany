<template>
  <div class="m-page">
    <div class="m-section" style="margin-top:12px">
      <div class="m-section-title">巡田记录</div>

      <div class="m-row" style="margin-bottom:10px">
        <select v-model="status" class="m-select" style="flex:1" @change="load">
          <option value="">全部状态</option>
          <option value="0">待诊断</option>
          <option value="1">已诊断</option>
          <option value="2">已生成报告</option>
        </select>
        <button class="m-btn" style="width:auto;padding:0 16px;margin-left:8px" @click="load">刷新</button>
      </div>

      <div v-if="loading" class="m-tip">加载中…</div>
      <div v-else-if="!list.length" class="m-empty">还没有记录<br />点右下角按钮拍一张</div>

      <!--
        列表带上缩略图。原来这里只有文字，翻历史记录时看不到当初拍的是什么 ——
        而巡田记录的判断依据本来就是那张照片，桌面端列表一直有缩略图列，手机端漏了。
      -->
      <div
        v-for="r in list"
        :key="r.recordId"
        class="m-rec-item"
        @click="go('/m/record/' + r.recordId)"
      >
        <TzImage v-if="r.imageUrl" :url="r.imageUrl" class-name="m-rec-thumb" />
        <div v-else class="m-rec-thumb m-rec-thumb--empty">
          <el-icon :size="18"><Picture /></el-icon>
        </div>

        <div class="m-rec-body">
          <div class="m-row">
            <span class="m-strong">{{ r.diagnosisName || '待诊断' }}</span>
            <span class="m-tag" :class="riskClass(r.riskLevel)">{{ riskText(r.riskLevel) }}</span>
          </div>
          <div class="m-muted m-ellipsis">{{ r.plotName || '—' }} · {{ r.scoutTime || '' }}</div>
          <div class="m-muted m-ellipsis" v-if="r.symptomText">{{ r.symptomText }}</div>
        </div>
      </div>
    </div>

    <!-- 悬浮新建按钮：手机上拇指最容易够到右下角 -->
    <button class="m-fab" @click="go('/m/record/new')">
      <el-icon :size="26"><Camera /></el-icon>
    </button>
  </div>
</template>

<script setup name="MRecords">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listRecord } from '@/api/tianzhen/record'
import { Camera, Picture } from '@element-plus/icons-vue'
import TzImage from '@/components/TzImage/index.vue'

const router = useRouter()
const list = ref([])
const loading = ref(false)
const status = ref('')

function riskText(level) {
  return { '3': '高', '2': '中', '1': '低' }[String(level)] || '—'
}
function riskClass(level) {
  return { '3': 'is-high', '2': 'is-mid', '1': 'is-low' }[String(level)] || ''
}
function go(path) { router.push(path) }

async function load() {
  loading.value = true
  try {
    const res = await listRecord({ pageNum: 1, pageSize: 50, status: status.value })
    list.value = res.rows || []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
/* 记录列表项的样式已提到 mobile.scss —— 首页「最近巡田」用的是同一套，
   放在这里 scoped 的话那边拿不到，只能复制一份，迟早改漏。
   这里只保留本页专属的悬浮按钮。 */
.m-fab {
  position: fixed;
  right: 16px;
  bottom: calc(74px + env(safe-area-inset-bottom));
  width: 54px;
  height: 54px;
  border-radius: 50%;
  border: none;
  background: var(--tz-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 14px rgba(47, 107, 79, 0.32);
  z-index: 90;
  -webkit-tap-highlight-color: transparent;
}
.m-fab:active { background: var(--tz-primary-deep); }
</style>
