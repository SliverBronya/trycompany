<template>
  <div class="m-page">
    <div class="m-navbar">
      <el-icon :size="20" @click="back"><ArrowLeft /></el-icon>
      <span>我的公司</span>
      <span />
    </div>

    <div v-if="loading" class="m-tip">加载中…</div>

    <!-- ══════════ 未加入公司：引导页 ══════════ -->
    <div v-else-if="!company" class="m-section">
      <div class="m-card" style="text-align:center;padding:22px 16px">
        <div style="font-size:15px;font-weight:600;margin-bottom:6px">你还没有加入任何公司</div>
        <div class="m-muted" style="line-height:1.8">
          加入公司后才能使用巡田、复查等功能。<br />
          收到同事的邀请码就选「用邀请码加入」，<br />想自己开一家就选「创建新公司」。
        </div>
      </div>

      <div class="m-section-title" style="margin-top:16px">用邀请码加入</div>
      <div class="m-card">
        <div class="m-field">
          <input v-model.trim="joinCode" class="m-input" placeholder="输入 8 位邀请码" autocapitalize="characters" />
        </div>
        <div v-if="codeInfo" class="m-block" style="margin-bottom:12px">
          <div class="m-block-title">{{ codeInfo.valid ? '邀请有效' : '邀请不可用' }}</div>
          <div class="m-text">
            {{ codeInfo.companyName || codeInfo.reason }}
            <template v-if="codeInfo.valid && codeInfo.expireTime">
              <br /><span class="m-muted">有效期至 {{ codeInfo.expireTime }}</span>
            </template>
          </div>
        </div>
        <button class="m-btn is-plain" :disabled="checking" @click="checkCode">
          {{ checking ? '查询中…' : '查询邀请' }}
        </button>
        <button class="m-btn" style="margin-top:10px" :disabled="joining || !codeInfo || !codeInfo.valid" @click="join">
          {{ joining ? '加入中…' : '加入该公司' }}
        </button>
      </div>

      <div class="m-section-title" style="margin-top:16px">或创建新公司</div>
      <div class="m-card">
        <div class="m-field">
          <div class="m-field-label">公司名称</div>
          <input v-model.trim="newCompany.companyName" class="m-input" placeholder="如：绿源柑橘合作社" />
        </div>
        <div class="m-field">
          <div class="m-field-label">联系人</div>
          <input v-model.trim="newCompany.contactName" class="m-input" placeholder="可留空" />
        </div>
        <div class="m-field">
          <div class="m-field-label">联系电话</div>
          <input v-model.trim="newCompany.contactPhone" class="m-input" placeholder="可留空" />
        </div>
        <div class="m-field">
          <div class="m-field-label">种植规模</div>
          <input v-model.trim="newCompany.scale" class="m-input" placeholder="可留空，如 500亩" />
        </div>
        <button class="m-btn" :disabled="creating" @click="create">
          {{ creating ? '创建中…' : '创建公司（我会成为管理员）' }}
        </button>
        <div class="m-muted" style="margin-top:8px">免审核，创建后立即生效。</div>
      </div>
    </div>

    <!-- ══════════ 已加入：公司信息 + 成员 + 邀请码 ══════════ -->
    <div v-else class="m-section">
      <div class="m-card">
        <div class="m-row">
          <span class="m-strong">{{ company.companyName }}</span>
          <span class="m-tag is-ok">{{ isOwner ? '管理员' : '成员' }}</span>
        </div>
        <div class="m-muted">
          {{ company.contactName || '—' }}<template v-if="company.contactPhone"> · {{ company.contactPhone }}</template>
          <template v-if="company.scale"> · {{ company.scale }}</template>
        </div>
        <div class="m-muted">成员 {{ members.length }} 人</div>
      </div>

      <div class="m-section-title">成员</div>
      <div v-for="m in members" :key="m.userId" class="m-card">
        <div class="m-row">
          <span class="m-strong">{{ m.nickName || m.userName }}</span>
          <span class="m-tag">{{ m.roles || '成员' }}</span>
        </div>
        <div class="m-muted">{{ m.deptName || '—' }}<template v-if="m.phonenumber"> · {{ m.phonenumber }}</template></div>
        <div class="m-btn-row" v-if="isOwner && m.userId !== myUserId">
          <button class="m-btn is-plain" @click="removeMember(m)">移出公司</button>
        </div>
      </div>

      <template v-if="isOwner">
        <div class="m-section-title">邀请码</div>
        <div class="m-card">
          <div class="m-field">
            <div class="m-field-label">有效天数</div>
            <input v-model.number="invite.days" class="m-input" type="number" min="1" max="90" />
          </div>
          <div class="m-field">
            <div class="m-field-label">可用次数</div>
            <input v-model.number="invite.maxUses" class="m-input" type="number" min="1" max="50" />
          </div>
          <button class="m-btn" :disabled="genInvite" @click="gen">
            {{ genInvite ? '生成中…' : '生成邀请码' }}
          </button>
        </div>
        <div v-for="i in invites" :key="i.inviteId" class="m-card">
          <div class="m-row">
            <span class="m-strong" style="font-family:monospace;letter-spacing:2px">{{ i.code }}</span>
            <span class="m-tag" :class="i.status === '0' ? 'is-ok' : ''">
              {{ i.status === '0' ? '有效' : (i.status === '1' ? '已用完' : '已作废') }}
            </span>
          </div>
          <div class="m-muted">已用 {{ i.usedCount }}/{{ i.maxUses }}<template v-if="i.expireTime"> · 至 {{ i.expireTime }}</template></div>
          <div class="m-btn-row" v-if="i.status === '0'">
            <button class="m-btn is-plain" @click="cancel(i.inviteId)">作废</button>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup name="MCompany">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import request from '@/utils/request'

const router = useRouter()
const loading = ref(true)
const company = ref(null)
const members = ref([])
const invites = ref([])
const isOwner = ref(false)
const myUserId = ref(null)
const joinCode = ref('')
const codeInfo = ref(null)
const checking = ref(false)
const joining = ref(false)
const creating = ref(false)
const genInvite = ref(false)
const invite = ref({ days: 7, maxUses: 1 })
const newCompany = ref({ companyName: '', contactName: '', contactPhone: '', scale: '' })

function back() { router.back() }

async function load() {
  loading.value = true
  try {
    const mine = await request({ url: '/tz/company/mine' })
    company.value = mine.data || null
    if (company.value) {
      // 后端把创建者记在 ownerUserId，据此判断能不能管公司
      const profile = await request({ url: '/system/user/profile' })
      myUserId.value = (profile.data || {}).user?.userId
      isOwner.value = myUserId.value != null && company.value.ownerUserId === myUserId.value
      members.value = (await request({ url: '/tz/company/members' })).data || []
      if (isOwner.value) {
        invites.value = (await request({ url: '/tz/company/invites' })).data || []
      }
    }
  } finally {
    loading.value = false
  }
}

async function checkCode() {
  if (!joinCode.value) { ElMessage.warning('先输入邀请码'); return }
  checking.value = true
  try {
    const res = await request({ url: '/tz/invite/' + encodeURIComponent(joinCode.value) })
    codeInfo.value = res.data || {}
  } catch (e) {
    codeInfo.value = null
  } finally {
    checking.value = false
  }
}

async function join() {
  joining.value = true
  try {
    await request({ url: '/tz/invite/' + encodeURIComponent(joinCode.value) + '/accept', method: 'post' })
    ElMessage.success('已加入，欢迎！')
    joinCode.value = ''
    codeInfo.value = null
    await load()
  } finally {
    joining.value = false
  }
}

async function create() {
  if (!newCompany.value.companyName) { ElMessage.warning('先填公司名称'); return }
  creating.value = true
  try {
    await request({ url: '/tz/company', method: 'post', data: newCompany.value })
    ElMessage.success('公司已创建，你就是管理员')
    newCompany.value = { companyName: '', contactName: '', contactPhone: '', scale: '' }
    await load()
  } finally {
    creating.value = false
  }
}

async function gen() {
  genInvite.value = true
  try {
    await request({
      url: '/tz/company/invites', method: 'post',
      data: { maxUses: invite.value.maxUses, expireDays: invite.value.days }
    })
    ElMessage.success('邀请码已生成')
    await load()
  } finally {
    genInvite.value = false
  }
}

async function cancel(inviteId) {
  await request({ url: '/tz/company/invites/' + inviteId, method: 'delete' })
  ElMessage.success('已作废')
  await load()
}

async function removeMember(m) {
  await request({ url: '/tz/company/members/' + m.userId, method: 'delete' })
  ElMessage.success('已移出')
  await load()
}

onMounted(load)
</script>
