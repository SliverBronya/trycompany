<template>
  <div class="m-page">
    <div class="m-navbar">
      <el-icon :size="20" @click="back"><ArrowLeft /></el-icon>
      <span>地块管理</span>
      <el-icon :size="20" @click="showAdd = !showAdd"><Plus /></el-icon>
    </div>

    <div class="m-section">
      <!-- 手机上新增地块放在列表顶部展开，比弹一大个对话框好用 -->
      <div class="m-card" v-if="showAdd">
        <div class="m-field">
          <div class="m-field-label">地块名称</div>
          <input v-model="addForm.plotName" class="m-input" placeholder="如：柑橘示范园1号地" />
        </div>
        <div class="m-field">
          <div class="m-field-label">作物类型</div>
          <select v-model="addForm.cropType" class="m-select">
            <option value="柑橘">柑橘</option>
          </select>
        </div>
        <div class="m-field">
          <div class="m-field-label">面积（亩）</div>
          <input v-model="addForm.area" class="m-input" type="number" placeholder="可留空" />
        </div>
        <div class="m-field">
          <div class="m-field-label">位置</div>
          <input v-model="addForm.location" class="m-input" placeholder="可留空" />
        </div>
        <button class="m-btn" :disabled="saving" @click="submitAdd">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </div>

      <div v-if="loading" class="m-tip">加载中…</div>
      <div v-else-if="!list.length" class="m-empty">还没有地块</div>

      <div v-for="p in list" :key="p.plotId" class="m-card">
        <div class="m-row">
          <span class="m-strong">{{ p.plotName }}</span>
          <span class="m-tag is-ok">{{ p.cropType }}</span>
        </div>
        <div class="m-muted">
          {{ p.area ? p.area + ' 亩' : '面积未填' }}<template v-if="p.location"> · {{ p.location }}</template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listPlot, addPlot } from '@/api/tianzhen/plot'
import { ArrowLeft, Plus } from '@element-plus/icons-vue'

const router = useRouter()
const list = ref([])
const loading = ref(false)
const saving = ref(false)
const showAdd = ref(false)
const addForm = ref({ plotName: '', cropType: '柑橘', area: undefined, location: '', status: '0' })

function back() { router.back() }

async function load() {
  loading.value = true
  try {
    const res = await listPlot({ pageNum: 1, pageSize: 100 })
    list.value = res.rows || []
  } finally {
    loading.value = false
  }
}

async function submitAdd() {
  if (!addForm.value.plotName) {
    ElMessage.warning('请填写地块名称')
    return
  }
  saving.value = true
  try {
    await addPlot(addForm.value)
    ElMessage.success('已添加')
    addForm.value = { plotName: '', cropType: '柑橘', area: undefined, location: '', status: '0' }
    showAdd.value = false
    await load()
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>
