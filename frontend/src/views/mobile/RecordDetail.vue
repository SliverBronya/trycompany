<template>
  <div class="m-page">
    <div class="m-navbar">
      <el-icon :size="20" @click="back"><ArrowLeft /></el-icon>
      <span>巡田详情</span>
      <span />
    </div>

    <div v-if="loading" class="m-tip">加载中…</div>

    <template v-else>
      <img v-if="record.imageUrl" :src="resolve(record.imageUrl)" class="m-photo" style="border-radius:0" />

      <div class="m-section">
        <div class="m-card">
          <div class="m-row">
            <span class="m-strong">{{ record.diagnosisName || '尚未诊断' }}</span>
            <span class="m-tag" :class="riskClass(record.riskLevel)">{{ riskText(record.riskLevel) }}</span>
          </div>
          <div class="m-muted">
            {{ record.plotName || '—' }} · {{ record.scoutTime || '' }}
            <template v-if="record.confidence"> · 置信 {{ record.confidence }}</template>
          </div>
          <!-- 来源要说清：降级结论不是 AI 看图得出的，不能混为一谈 -->
          <div class="m-muted" v-if="record.diagnosisSource">
            结论来源：{{ sourceText(record.diagnosisSource) }}
          </div>
        </div>

        <div class="m-block" v-if="record.symptomText">
          <div class="m-block-title">症状描述</div>
          <div class="m-text">{{ record.symptomText }}</div>
        </div>

        <div class="m-block" v-if="record.diagnosisBasis">
          <div class="m-block-title">判断依据</div>
          <div class="m-text">{{ record.diagnosisBasis }}</div>
        </div>

        <div class="m-block" v-if="record.suggestion">
          <div class="m-block-title">防治建议</div>
          <div class="m-text">{{ record.suggestion }}</div>
          <div class="m-muted" v-if="record.dosageGuardHit === '1'">
            本条建议中含用量的表述已与知识库比对，未收录的部分已被移除。
          </div>
        </div>

        <div class="m-btn-row">
          <button class="m-btn" :disabled="diagnosing" @click="runDiagnose">
            {{ diagnosing ? '诊断中…' : (record.diagnosisName ? '重新诊断' : '开始诊断') }}
          </button>
        </div>
        <div class="m-btn-row" v-if="record.diagnosisName">
          <button class="m-btn is-plain" :disabled="suggesting" @click="runSuggestion">
            {{ suggesting ? '生成中…' : (record.suggestion ? '重新生成建议' : '获取防治建议') }}
          </button>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getRecord } from '@/api/tianzhen/record'
import { diagnose } from '@/api/tianzhen/diagnosis'
import { generateSuggestion } from '@/api/tianzhen/suggestion'
import { ArrowLeft } from '@element-plus/icons-vue'
import { resolveImageUrl as resolve } from '@/utils/tzImage'

const route = useRoute()
const router = useRouter()
const record = ref({})
const loading = ref(false)
const diagnosing = ref(false)
const suggesting = ref(false)
const recordId = route.params.recordId

function back() { router.back() }
function riskText(level) {
  return { '3': '高', '2': '中', '1': '低' }[String(level)] || '—'
}
function riskClass(level) {
  return { '3': 'is-high', '2': 'is-mid', '1': 'is-low' }[String(level)] || ''
}
function sourceText(src) {
  return { preset: '预置样张映射', llm: '多模态大模型初诊', fallback: '知识库检索降级', manual: '人工录入' }[src] || src
}

async function load() {
  loading.value = true
  try {
    const res = await getRecord(recordId)
    record.value = res.data || {}
  } finally {
    loading.value = false
  }
}

async function runDiagnose() {
  diagnosing.value = true
  try {
    await diagnose(recordId, true)
    await load()
    ElMessage.success('诊断完成')
  } catch (e) {
    ElMessage.error((e && e.msg) || '未能给出可靠结论，建议补充描述或重拍照片')
  } finally {
    diagnosing.value = false
  }
}

async function runSuggestion() {
  suggesting.value = true
  try {
    await generateSuggestion(recordId, true)
    await load()
    ElMessage.success('建议已生成')
  } catch (e) {
    ElMessage.error((e && e.msg) || '建议生成失败')
  } finally {
    suggesting.value = false
  }
}

onMounted(load)
</script>
