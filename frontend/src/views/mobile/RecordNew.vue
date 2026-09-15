<template>
  <div class="m-page">
    <div class="m-navbar">
      <el-icon :size="20" @click="back"><ArrowLeft /></el-icon>
      <span>拍照巡田</span>
      <span />
    </div>

    <div class="m-section">
      <!-- 1. 照片：手机上直接调起摄像头，而不是先选文件 -->
      <div class="m-field">
        <div class="m-field-label">现场照片（必填）</div>

        <img v-if="form.imageUrl" :src="resolve(form.imageUrl)" class="m-photo" alt="现场照片" />
        <label v-else class="m-photo-picker">
          <el-icon :size="30"><Camera /></el-icon>
          <span>点击拍照</span>
        </label>

        <input
          ref="fileRef"
          type="file"
          accept="image/*"
          capture="environment"
          style="display:none"
          @change="onPick"
        />

        <div class="m-btn-row">
          <button class="m-btn is-plain" @click="pick">拍照 / 选图</button>
        </div>
        <div v-if="uploading" class="m-tip" style="padding:8px 0">照片上传中…</div>
      </div>

      <!-- 2. 症状描述：AI 看完照片自动写，用户可以改 -->
      <div class="m-field">
        <div class="m-field-label">
          症状描述
          <span v-if="descState" class="m-tag" :class="descState === 'ok' ? 'is-ok' : ''">{{ descStateText }}</span>
        </div>
        <textarea
          v-model="form.symptomText"
          class="m-textarea"
          placeholder="拍照后系统会先写一版描述，你可以在此基础上修改；也可以留空直接提交"
        />
        <button
          v-if="form.imageUrl"
          class="m-btn is-plain"
          style="margin-top:8px"
          :disabled="describing"
          @click="describe"
        >
          {{ describing ? '识别中…' : '重新识别照片' }}
        </button>
      </div>

      <!-- 3. 归属信息 -->
      <div class="m-field">
        <div class="m-field-label">地块</div>
        <select v-model="form.plotId" class="m-select">
          <option v-for="p in plots" :key="p.plotId" :value="p.plotId">{{ p.plotName }}</option>
        </select>
      </div>

      <div class="m-field">
        <div class="m-field-label">发病部位</div>
        <select v-model="form.plantPart" class="m-select">
          <option value="1">叶片</option>
          <option value="2">枝干</option>
          <option value="3">果实</option>
          <option value="4">根部</option>
        </select>
      </div>

      <div class="m-field">
        <div class="m-field-label">严重程度</div>
        <select v-model="form.severity" class="m-select">
          <option value="1">轻</option>
          <option value="2">中</option>
          <option value="3">重</option>
        </select>
      </div>

      <button class="m-btn" :disabled="submitting || !form.imageUrl || !form.plotId" @click="submit">
        {{ submitting ? '提交中…' : '提交并诊断' }}
      </button>
      <div v-if="!form.imageUrl" class="m-tip" style="padding:8px 0">诊断以照片为主要依据，请先拍照</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { plotOptionSelect } from '@/api/tianzhen/plot'
import { addRecord, describeRecordImage } from '@/api/tianzhen/record'
import { diagnose } from '@/api/tianzhen/diagnosis'
import { ArrowLeft, Camera } from '@element-plus/icons-vue'
import { resolveImageUrl as resolve } from '@/utils/tzImage'

const router = useRouter()
const fileRef = ref()
const plots = ref([])
const uploading = ref(false)
const describing = ref(false)
const submitting = ref(false)
const descState = ref('')      // ok | fail | ''
const descStateText = ref('')
const form = ref({ imageUrl: '', symptomText: '', plotId: undefined, plantPart: '1', severity: '2' })

function back() { router.back() }
function pick() { fileRef.value && fileRef.value.click() }

async function onPick(e) {
  const file = e.target.files && e.target.files[0]
  if (!file) return
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    const res = await fetch(import.meta.env.VITE_APP_BASE_API + '/common/upload', {
      method: 'POST',
      headers: { Authorization: 'Bearer ' + getToken() },
      body: fd
    }).then(r => r.json())
    if (res.code !== 200) throw new Error(res.msg || '上传失败')
    form.value.imageUrl = res.url || res.fileName
    // 照片换了新的，上一版描述就作废了
    form.value.symptomText = ''
    descState.value = ''
    await describe()
  } catch (err) {
    ElMessage.error('照片上传失败：' + (err.message || ''))
  } finally {
    uploading.value = false
    e.target.value = ''
  }
}

/** 调用识图模型先写一版描述。失败不阻塞：用户可以自己写，也可以直接提交 */
async function describe() {
  if (!form.value.imageUrl) return
  describing.value = true
  descState.value = ''
  try {
    const res = await describeRecordImage(form.value.imageUrl, form.value.plantPart)
    const d = res.data || {}
    if (d.success) {
      form.value.symptomText = d.description || ''
      descState.value = 'ok'
      descStateText.value = '已由识图生成'
    } else {
      descState.value = 'fail'
      descStateText.value = '未能自动生成'
      ElMessage.info(d.degradeReason || '这次没能自动生成描述，可以自己写几句')
    }
  } catch (err) {
    descState.value = 'fail'
    descStateText.value = '识别失败'
  } finally {
    describing.value = false
  }
}

async function submit() {
  submitting.value = true
  try {
    const res = await addRecord(form.value)
    const recordId = (res.data || {}).recordId
    if (!recordId) throw new Error('未返回记录 ID')
    // 提交后直接把诊断也跑了，省得用户再点一次
    try {
      await diagnose(recordId, true)
    } catch (e) {
      // 诊断失败不回滚——记录已经存下来了，进详情页还能重试
    }
    router.replace('/m/record/' + recordId)
  } catch (err) {
    ElMessage.error((err && err.msg) || '提交失败，请检查是否填写完整')
  } finally {
    submitting.value = false
  }
}

function getToken() {
  // 与项目其它地方保持一致：若依把 token 存在 cookie 里
  const m = document.cookie.match(/(?:^|;\s*)Admin-Token=([^;]*)/)
  return m ? decodeURIComponent(m[1]) : ''
}

onMounted(async () => {
  const res = await plotOptionSelect()
  plots.value = res.data || []
  if (plots.value.length) form.value.plotId = plots.value[0].plotId
})
</script>
