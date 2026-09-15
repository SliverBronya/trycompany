<template>
  <div class="m-page">
    <div class="m-navbar">
      <el-icon :size="20" @click="back"><ArrowLeft /></el-icon>
      <span>{{ data.diseaseName || '知识库' }}</span>
      <span />
    </div>

    <div v-if="loading" class="m-tip">加载中…</div>

    <div v-else class="m-section">
      <img v-if="data.typicalImage" :src="resolve(data.typicalImage)" class="m-photo" alt="典型症状" />

      <div class="m-block" v-if="data.keyFeatures">
        <div class="m-block-title">特征性表现</div>
        <div class="m-text">{{ data.keyFeatures }}</div>
      </div>

      <div class="m-block" v-if="data.symptoms">
        <div class="m-block-title">症状描述</div>
        <div class="m-text">{{ data.symptoms }}</div>
      </div>

      <div class="m-block" v-if="data.differential">
        <div class="m-block-title">鉴别要点</div>
        <div class="m-text">{{ data.differential }}</div>
      </div>

      <div class="m-block" v-if="data.triggerConditions">
        <div class="m-block-title">发生条件</div>
        <div class="m-text">{{ data.triggerConditions }}</div>
      </div>

      <div class="m-block" v-if="data.prevention">
        <div class="m-block-title">防治措施</div>
        <div class="m-text">{{ data.prevention }}</div>
      </div>

      <div class="m-block" v-if="data.medicineNote">
        <div class="m-block-title">用药注意</div>
        <div class="m-text">{{ data.medicineNote }}</div>
      </div>

      <div class="m-block" v-if="data.safetyNote">
        <div class="m-block-title">安全说明</div>
        <div class="m-text">{{ data.safetyNote }}</div>
      </div>

      <div class="m-block" v-if="data.source">
        <div class="m-block-title">资料来源</div>
        <div class="m-text">{{ data.source }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getKnowledge } from '@/api/tianzhen/knowledge'
import { ArrowLeft } from '@element-plus/icons-vue'
import { resolveImageUrl as resolve } from '@/utils/tzImage'

const route = useRoute()
const router = useRouter()
const data = ref({})
const loading = ref(false)

function back() { router.back() }

onMounted(async () => {
  loading.value = true
  try {
    const res = await getKnowledge(route.params.knowledgeId)
    data.value = res.data || {}
  } finally {
    loading.value = false
  }
})
</script>
