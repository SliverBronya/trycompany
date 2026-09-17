<template>
  <div class="tz-home">
    <div ref="sentinelRef" class="tz-sentinel" aria-hidden="true"></div>

    <!-- 悬浮子导航：滚动出首屏后浮现 -->
    <nav class="tz-nav" :class="{ 'is-stuck': stuck }">
      <div class="tz-nav__inner">
        <span class="tz-nav__brand">
          <TzBrandMark compact />
          田诊助手
        </span>
        <div class="tz-nav__links">
          <a
            v-for="item in anchors"
            :key="item.id"
            :href="'#' + item.id"
            @click.prevent="jumpTo(item.id)"
            >{{ item.label }}</a
          >
        </div>
        <button type="button" class="tz-nav__cta" @click="go('/dashboard')">进入工作台</button>
      </div>
    </nav>

    <!-- ============================ 首屏 ============================ -->
    <header class="tz-hero">
      <p class="tz-hero__eyebrow reveal">柑橘植保 · AI 巡田报告助手</p>
      <h1 class="tz-hero__title reveal">
        拍一张病叶照片，<br />拿到一份巡田报告。
      </h1>
      <p class="tz-hero__sub reveal">
        面向基层农技员与种植合作社。上传田间照片、写下看到的症状，系统给出病害诊断、
        可溯源的防治建议，并自动生成一份能归档的巡田报告与复查任务。
      </p>
      <div class="tz-hero__actions reveal">
        <button type="button" class="tz-btn tz-btn--primary" @click="go('/record')">
          进入巡田记录
        </button>
        <button type="button" class="tz-btn tz-btn--ghost" @click="go('/knowledge')">
          查看植保知识库
        </button>
      </div>

      <!-- 产品形态示意：一张现场照片 → 一份结构化结论 -->
      <div class="tz-mock reveal" role="img" aria-label="巡田诊断结果示意：现场照片诊断为疑似柑橘溃疡病，置信度 82%，风险等级中">
        <div class="tz-mock__card">
          <div class="tz-mock__label">现场照片</div>
          <svg class="tz-mock__leaf" viewBox="0 0 64 64" aria-hidden="true">
            <path
              d="M6 58 C 6 26, 30 6, 58 6 C 58 34, 38 58, 6 58 Z"
              fill="#E1F5EE"
              stroke="#0F6E56"
              stroke-width="1.6"
            />
            <path
              d="M7 57 C 25 39, 43 21, 57 7"
              fill="none"
              stroke="#0F6E56"
              stroke-width="1.2"
              stroke-linecap="round"
              opacity="0.55"
            />
            <circle cx="24" cy="34" r="7" fill="#FAEEDA" opacity="0.9" />
            <circle cx="24" cy="34" r="3.2" fill="#BA7517" />
            <circle cx="39" cy="24" r="6" fill="#FAEEDA" opacity="0.9" />
            <circle cx="39" cy="24" r="2.8" fill="#BA7517" />
            <circle cx="18" cy="45" r="5" fill="#FAEEDA" opacity="0.9" />
            <circle cx="18" cy="45" r="2.4" fill="#BA7517" />
          </svg>
        </div>

        <div class="tz-mock__flow" aria-hidden="true">
          <span class="tz-mock__dot"></span>
          <span class="tz-mock__dot"></span>
          <span class="tz-mock__dot"></span>
        </div>

        <div class="tz-mock__card tz-mock__card--result">
          <div class="tz-mock__label">诊断结论</div>
          <div class="tz-mock__name">疑似柑橘溃疡病</div>
          <div class="tz-mock__rows">
            <span>置信度 <b>82%</b></span>
            <span>风险等级 <b>中</b></span>
          </div>
          <div class="tz-mock__badge">预置样张映射</div>
        </div>
      </div>
    </header>

    <!-- ============================ 关键指标 ============================ -->
    <section class="tz-metrics">
      <div class="tz-metrics__grid">
        <div v-for="item in metrics" :key="item.label" class="tz-metrics__item reveal">
          <div class="tz-metrics__value">{{ item.value }}</div>
          <div class="tz-metrics__label" v-html="item.label"></div>
        </div>
      </div>
    </section>

    <!-- ============================ 核心能力 ============================ -->
    <section id="capability" class="tz-section">
      <div class="tz-section__head reveal">
        <h2 class="tz-section__title">一条完整的巡田链路，<br />不是一次孤立的图片识别。</h2>
        <p class="tz-section__desc">
          从地块建档到复查闭环，六个环节都在同一个系统里，数据前后咬合，不需要在两三个工具之间来回搬。
        </p>
      </div>

      <div class="tz-cards">
        <article v-for="item in capabilities" :key="item.title" class="tz-card reveal">
          <div class="tz-card__icon" v-html="item.icon"></div>
          <h3 class="tz-card__title">{{ item.title }}</h3>
          <p class="tz-card__desc">{{ item.desc }}</p>
          <p class="tz-card__meta">{{ item.meta }}</p>
        </article>
      </div>
    </section>

    <!-- ============================ 业务闭环 ============================ -->
    <section id="flow" class="tz-section tz-section--alt">
      <div class="tz-section__head reveal">
        <h2 class="tz-section__title">六步走完一次巡田</h2>
        <p class="tz-section__desc">每一步都有对应的记录留存，事后可回溯、可统计、可交接。</p>
      </div>

      <ol class="tz-flow">
        <li v-for="(step, index) in flow" :key="step.title" class="tz-flow__step reveal">
          <span class="tz-flow__index">{{ String(index + 1).padStart(2, '0') }}</span>
          <h3 class="tz-flow__title">{{ step.title }}</h3>
          <p class="tz-flow__desc">{{ step.desc }}</p>
        </li>
      </ol>
    </section>

    <!-- ============================ 诊断链路（深色） ============================ -->
    <section id="engine" class="tz-section tz-section--dark">
      <div class="tz-section__head reveal">
        <h2 class="tz-section__title">三级兜底，一级拒答</h2>
        <p class="tz-section__desc">
          诊断不是一次 API 调用就完事。系统按顺序逐级尝试，任何一级不通都不会让使用者空手而归 ——
          但也不会凭空编一个结论出来。
        </p>
      </div>

      <div class="tz-steps">
        <article v-for="item in engine" :key="item.title" class="tz-step reveal">
          <div class="tz-step__badge">{{ item.badge }}</div>
          <h3 class="tz-step__title">{{ item.title }}</h3>
          <p class="tz-step__desc">{{ item.desc }}</p>
        </article>
      </div>

      <p class="tz-note reveal">
        无论结论出自哪一级，都会带上来源标签 —— 是查表来的、模型给的，还是降级来的，使用者一眼可见。
      </p>
    </section>

    <!-- ============================ 严谨与合规 ============================ -->
    <section id="trust" class="tz-section">
      <div class="tz-section__head reveal">
        <h2 class="tz-section__title">农技建议，宁可说不知道</h2>
        <p class="tz-section__desc">
          植保结论会直接影响田间用药。系统在这一层刻意做了减法：不做没把握的推断，不说没有出处的用量。
        </p>
      </div>

      <div class="tz-trust">
        <article v-for="item in trust" :key="item.title" class="tz-trust__item reveal">
          <h3 class="tz-trust__title">{{ item.title }}</h3>
          <p class="tz-trust__desc">{{ item.desc }}</p>
        </article>
      </div>
    </section>

    <!-- ============================ 可扩展 ============================ -->
    <section id="extend" class="tz-section tz-section--alt">
      <div class="tz-section__head reveal">
        <h2 class="tz-section__title">柑橘打样，架构按多作物设计</h2>
        <p class="tz-section__desc">
          Demo 以柑橘做垂直验证。诊断与建议逻辑里不含作物硬编码，扩展到其他作物只需补充知识库数据。
        </p>
      </div>

      <div class="tz-crops">
        <article v-for="item in crops" :key="item.name" class="tz-crop reveal" :data-state="item.state">
          <div class="tz-crop__name">{{ item.name }}</div>
          <div class="tz-crop__state">{{ item.stateText }}</div>
          <p class="tz-crop__desc">{{ item.desc }}</p>
        </article>
      </div>
    </section>

    <!-- ============================ 页脚 ============================ -->
    <footer class="tz-footer">
      <div class="tz-footer__inner">
        <div class="tz-footer__brand">田诊助手 · 柑橘版</div>
        <p class="tz-footer__disclaimer">
          本系统由 AI 辅助生成诊断与建议，仅作为巡田参考，不能替代农技人员的现场诊断与农药标签说明。
          知识库用量来自公开发布的植保资料，属推荐值而非法定值，对外使用前应由植保专业人员复核。
        </p>
        <div class="tz-footer__links">
          <a href="#" @click.prevent="go('/dashboard')">首页看板</a>
          <a href="#" @click.prevent="go('/plot')">地块管理</a>
          <a href="#" @click.prevent="go('/followup')">复查任务</a>
          <a href="#" @click.prevent="go('/knowledge')">植保知识库</a>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup name="Index">
import TzBrandMark from '@/components/TzBrandMark'

const router = useRouter()

const sentinelRef = ref(null)
const stuck = ref(false)

const anchors = [
  { id: 'capability', label: '核心能力' },
  { id: 'flow', label: '巡田流程' },
  { id: 'engine', label: '诊断链路' },
  { id: 'trust', label: '严谨性' },
  { id: 'extend', label: '可扩展' }
]

const metrics = [
  { value: '15', label: '条植保知识库条目<br />条条标注公开发布出处' },
  { value: '27ms', label: '预置路径实测往返<br />目标 ≤ 10 秒' },
  { value: '3+1', label: '三级兜底链路<br />加一级明确拒答' },
  { value: '4', label: '类结论来源如实标注<br />从不冒充当 AI 结论' }
]

const capabilities = [
  {
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="4"/><circle cx="9" cy="9" r="2"/><path d="M21 15l-5-5-4 4-3-3-6 6"/></svg>',
    title: '巡田记录，拍照即录入',
    desc: '上传现场照片，勾选发生部位与严重程度，症状用自然语言写。图片在落库时自动登记哈希，后续诊断直接可用。',
    meta: '图片最多 10MB，本地存储'
  },
  {
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M4.2 4.2l2.1 2.1M17.7 17.7l2.1 2.1M2 12h3M19 12h3M4.2 19.8l2.1-2.1M17.7 6.3l2.1-2.1"/></svg>',
    title: 'AI 病害初诊',
    desc: '多模态大模型结合图片与症状描述，给出疑似病害、置信度、风险等级和判断依据；置信度不足时不予采信。',
    meta: '置信度阈值 60，低于即转降级'
  },
  {
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5V5a2 2 0 012-2h11a2 2 0 012 2v14"/><path d="M4 19.5A2.5 2.5 0 016.5 17H19"/><path d="M8 7h7M8 11h5"/></svg>',
    title: 'RAG 防治建议',
    desc: '从本地植保知识库检索匹配条目，生成「当前判断 / 建议措施 / 用药安全 / 复查建议」四段式建议，并附知识库出处。',
    meta: '中文 bigram 检索 + 字段加权'
  },
  {
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H7a2 2 0 00-2 2v16a2 2 0 002 2h10a2 2 0 002-2V7z"/><path d="M14 2v5h5M9 13h6M9 17h4"/></svg>',
    title: '一键生成巡田报告',
    desc: '汇总地块信息、症状、诊断结论与防治建议，生成结构化报告，页面直接查看、一键复制归档。',
    meta: '报告编号自动生成'
  },
  {
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3.5 2"/></svg>',
    title: '复查任务自动排期',
    desc: '生成报告的同时按风险等级自动排出复查任务，到期未处理会标记逾期，风险问题不会被忘在纸面上。',
    meta: '高风险 3 天 / 中 7 天 / 低 14 天'
  },
  {
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3l8 4.5v9L12 21l-8-4.5v-9z"/><path d="M12 12l8-4.5M12 12v9M12 12L4 7.5"/></svg>',
    title: '植保知识库可维护',
    desc: '病虫害与缺素的症状、发生条件、防治措施、用药注意、安全说明都在后台维护，每条都要求写明资料来源。',
    meta: '当前 15 条：病害 6 · 虫害 7 · 缺素 2'
  }
]

const flow = [
  { title: '新建地块', desc: '录入地块名称、作物类型、面积与责任人。' },
  { title: '巡田拍照', desc: '上传现场照片，填写发生部位与症状描述。' },
  { title: 'AI 诊断', desc: '逐级尝试，给出疑似病害、置信度与风险等级。' },
  { title: '防治建议', desc: '检索知识库生成四段式建议，并做剂量校验。' },
  { title: '巡田报告', desc: '汇总全链路信息，生成可归档的结构化报告。' },
  { title: '复查任务', desc: '按风险自动排期，形成闭环跟踪。' }
]

const engine = [
  {
    badge: '第 1 级',
    title: '预置样张映射',
    desc: '图片哈希命中已登记的示例样张，直接返回标准结论。不经过大模型，演示与复现 100% 稳定。'
  },
  {
    badge: '第 2 级',
    title: '多模态大模型初诊',
    desc: '图片与症状描述一并送入多模态模型，输出结构化 JSON。返回内容无法解析或置信度低于阈值时不予采信。'
  },
  {
    badge: '第 3 级',
    title: '知识库关键词检索降级',
    desc: '大模型不可用时退回本地检索，按症状描述匹配知识库条目。置信度上限刻意压低，并明确标注为降级结果。'
  },
  {
    badge: '拒答',
    title: '如实说不知道',
    desc: '三级都不成立时，系统不生成任何结论，只说明原因并提示补充症状描述或联系当地农技员现场查看。'
  }
]

const trust = [
  {
    title: '不编造农药用量',
    desc: '建议生成后做一次剂量后置校验：知识库收录过的用量放行并可溯源，未收录的一律拦截，替换为「用量待确认，请以农药标签为准」。拦截提示不复述数字本身 —— 一个写在正文里的用量，前面标「已拦截」也拦不住人去照着打。'
  },
  {
    title: '结论必须可溯源',
    desc: '每条诊断与建议都标注来源类型（预置映射 / 大模型 / 降级 / 人工）与命中的知识库条目，界面上原样展示，不隐藏、不美化。'
  },
  {
    title: '全程强制免责声明',
    desc: '所有对外输出都附带同一份声明：AI 结果仅作巡田参考，不能替代农技人员现场诊断与农药标签说明。'
  }
]

const crops = [
  {
    name: '柑橘',
    state: 'active',
    stateText: '当前 Demo 打样',
    desc: '已完成 15 条知识库条目与 3 条完整演示链路：溃疡病、红蜘蛛、缺镁。'
  },
  {
    name: '芒果',
    state: 'reserved',
    stateText: '架构预留',
    desc: '作物类型已进字典，诊断与建议逻辑无需改动，补充知识库条目即可启用。'
  },
  {
    name: '水稻',
    state: 'reserved',
    stateText: '架构预留',
    desc: '同上。新增作物不涉及业务代码改动，这是架构层面预留的能力。'
  }
]

/** 跳转到业务页面。菜单未授权时路由可能不存在，静默失败即可 */
function go(path) {
  router.push(path).catch(() => {})
}

/** 平滑滚动到锚点。滚动容器是外层 .app-main，scrollIntoView 会自行处理 */
function jumpTo(id) {
  const el = document.getElementById(id)
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

let observer = null
let sentinelObserver = null

/** 滚动入场动画 + 导航栏吸顶态 */
function setupObservers() {
  teardownObservers()

  observer = new IntersectionObserver(
    entries => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-in')
          observer.unobserve(entry.target)
        }
      })
    },
    { threshold: 0.12, rootMargin: '0px 0px -40px 0px' }
  )
  document.querySelectorAll('.tz-home .reveal').forEach(el => observer.observe(el))

  if (sentinelRef.value) {
    sentinelObserver = new IntersectionObserver(
      entries => {
        stuck.value = !entries[0].isIntersecting
      },
      { threshold: 0 }
    )
    sentinelObserver.observe(sentinelRef.value)
  }
}

function teardownObservers() {
  if (observer) {
    observer.disconnect()
    observer = null
  }
  if (sentinelObserver) {
    sentinelObserver.disconnect()
    sentinelObserver = null
  }
}

onMounted(setupObservers)
onActivated(setupObservers)
onDeactivated(teardownObservers)
onBeforeUnmount(teardownObservers)
</script>

<style scoped lang="scss">
.tz-home {
  --ink: #1d1d1f;
  --ink-2: #5c5c60;
  --ink-3: #86868b;
  --line: #d2d2d7;
  --bg-alt: #f5f5f7;
  --green: #0f6e56;
  --green-bright: #1d9e75;
  --dark: #0a1a14;

  font-family: -apple-system, BlinkMacSystemFont, 'SF Pro SC', 'PingFang SC', 'Helvetica Neue',
    'Microsoft YaHei', sans-serif;
  color: var(--ink);
  background: #fff;
  -webkit-font-smoothing: antialiased;
}

.tz-sentinel {
  height: 1px;
  margin-bottom: -1px;
}

/* ---------------------------------------------------------------- 悬浮子导航 */
.tz-nav {
  position: sticky;
  top: 0;
  z-index: 20;
  height: 56px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: saturate(180%) blur(20px);
  border-bottom: 1px solid transparent;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.35s ease, border-color 0.35s ease;

  &.is-stuck {
    opacity: 1;
    pointer-events: auto;
    border-bottom-color: rgba(0, 0, 0, 0.08);
  }
}

.tz-nav__inner {
  max-width: 1040px;
  height: 100%;
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  align-items: center;
  gap: 32px;
}

.tz-nav__brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 500;
  letter-spacing: -0.01em;
  white-space: nowrap;
}

.tz-nav__links {
  display: flex;
  gap: 26px;
  flex: 1;

  a {
    font-size: 13px;
    color: var(--ink-2);
    text-decoration: none;
    transition: color 0.2s;

    &:hover {
      color: var(--ink);
    }
  }
}

.tz-nav__cta {
  font-size: 13px;
  font-weight: 500;
  color: #fff;
  background: var(--green);
  border: none;
  border-radius: 980px;
  padding: 7px 16px;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.2s, transform 0.2s;

  &:hover {
    background: #0b5544;
    transform: translateY(-1px);
  }
}

/* ---------------------------------------------------------------- 首屏 */
.tz-hero {
  max-width: 1040px;
  margin: 0 auto;
  padding: 76px 24px 96px;
  text-align: center;
}

.tz-hero__eyebrow {
  margin: 0 0 18px;
  font-size: 15px;
  font-weight: 500;
  letter-spacing: 0.01em;
  background: linear-gradient(90deg, var(--green-bright), var(--green));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.tz-hero__title {
  margin: 0;
  font-size: clamp(38px, 5.4vw, 68px);
  line-height: 1.08;
  font-weight: 500;
  letter-spacing: -0.025em;
}

.tz-hero__sub {
  max-width: 640px;
  margin: 26px auto 0;
  font-size: 17px;
  line-height: 1.72;
  color: var(--ink-2);
}

.tz-hero__actions {
  margin-top: 34px;
  display: flex;
  justify-content: center;
  gap: 14px;
  flex-wrap: wrap;
}

.tz-btn {
  font-family: inherit;
  font-size: 15px;
  font-weight: 500;
  border-radius: 980px;
  padding: 12px 26px;
  cursor: pointer;
  transition: all 0.22s ease;

  &--primary {
    color: #fff;
    background: var(--green);
    border: 1px solid var(--green);

    &:hover {
      background: #0b5544;
      border-color: #0b5544;
      transform: translateY(-1px);
    }
  }

  &--ghost {
    color: var(--green);
    background: transparent;
    border: 1px solid rgba(15, 110, 86, 0.35);

    &:hover {
      background: rgba(29, 158, 117, 0.08);
      border-color: var(--green);
    }
  }
}

/* 产品形态示意 */
.tz-mock {
  margin: 64px auto 0;
  max-width: 760px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 22px;
  padding: 30px 28px;
  border-radius: 24px;
  background: var(--bg-alt);
  border: 1px solid rgba(0, 0, 0, 0.04);
}

.tz-mock__card {
  flex: 1 1 0;
  min-width: 0;
  background: #fff;
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04), 0 12px 32px rgba(0, 0, 0, 0.06);
  text-align: left;

  &--result {
    text-align: left;
  }
}

.tz-mock__label {
  font-size: 11px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--ink-3);
  margin-bottom: 12px;
}

.tz-mock__leaf {
  display: block;
  width: 100%;
  max-width: 132px;
  height: auto;
  margin: 0 auto;
}

.tz-mock__name {
  font-size: 19px;
  font-weight: 500;
  letter-spacing: -0.01em;
  margin-bottom: 12px;
}

.tz-mock__rows {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--ink-2);

  b {
    font-weight: 500;
    color: var(--ink);
  }
}

.tz-mock__badge {
  display: inline-block;
  margin-top: 14px;
  font-size: 11px;
  font-weight: 500;
  color: var(--green);
  background: #e1f5ee;
  border-radius: 980px;
  padding: 4px 10px;
}

.tz-mock__flow {
  display: flex;
  gap: 5px;
  flex: 0 0 auto;
}

.tz-mock__dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--green-bright);
  opacity: 0.35;
  animation: tz-pulse 1.6s ease-in-out infinite;

  &:nth-child(2) {
    animation-delay: 0.2s;
  }
  &:nth-child(3) {
    animation-delay: 0.4s;
  }
}

@keyframes tz-pulse {
  0%,
  100% {
    opacity: 0.25;
    transform: scale(0.9);
  }
  50% {
    opacity: 1;
    transform: scale(1.15);
  }
}

/* ---------------------------------------------------------------- 关键指标 */
.tz-metrics {
  background: var(--bg-alt);
  padding: 56px 24px;
}

.tz-metrics__grid {
  max-width: 1040px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 28px;
}

.tz-metrics__item {
  text-align: center;
}

.tz-metrics__value {
  font-size: clamp(30px, 3.6vw, 44px);
  font-weight: 500;
  letter-spacing: -0.03em;
  color: var(--green);
  line-height: 1.15;
}

.tz-metrics__label {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--ink-2);
}

/* ---------------------------------------------------------------- 通用区块 */
.tz-section {
  padding: 104px 24px;
  /*
    吸顶导航条高 56px。点导航跳转时，区块顶部若直接对齐滚动容器顶端，
    会被这条导航盖掉 56px —— 虽然盖住的是内边距、标题还在下面，
    但区块自己的背景色会被切掉一截，看着像没对齐。
    scroll-margin-top 让浏览器在定位时提前留出这段距离。
  */
  scroll-margin-top: 56px;

  &--alt {
    background: var(--bg-alt);
  }

  &--dark {
    background: var(--dark);
    color: #f5f5f7;

    .tz-section__title {
      color: #f5f5f7;
    }
    .tz-section__desc {
      color: rgba(245, 245, 247, 0.66);
    }
  }
}

.tz-section__head {
  max-width: 720px;
  margin: 0 auto 60px;
  text-align: center;
}

.tz-section__title {
  margin: 0;
  font-size: clamp(28px, 3.6vw, 44px);
  line-height: 1.14;
  font-weight: 500;
  letter-spacing: -0.022em;
}

.tz-section__desc {
  margin: 20px 0 0;
  font-size: 17px;
  line-height: 1.72;
  color: var(--ink-2);
}

/* ---------------------------------------------------------------- 核心能力 */
.tz-cards {
  max-width: 1040px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.tz-card {
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 20px;
  padding: 30px 26px 26px;
  transition: transform 0.28s ease, box-shadow 0.28s ease, border-color 0.28s ease;

  &:hover {
    transform: translateY(-3px);
    border-color: rgba(15, 110, 86, 0.22);
    box-shadow: 0 14px 40px rgba(0, 0, 0, 0.07);
  }
}

.tz-card__icon {
  width: 30px;
  height: 30px;
  color: var(--green);
  margin-bottom: 20px;

  :deep(svg) {
    width: 100%;
    height: 100%;
  }
}

.tz-card__title {
  margin: 0 0 10px;
  font-size: 18px;
  font-weight: 500;
  letter-spacing: -0.012em;
}

.tz-card__desc {
  margin: 0;
  font-size: 14px;
  line-height: 1.72;
  color: var(--ink-2);
}

.tz-card__meta {
  margin: 16px 0 0;
  padding-top: 14px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  font-size: 12px;
  color: var(--ink-3);
}

/* ---------------------------------------------------------------- 业务闭环 */
.tz-flow {
  max-width: 1040px;
  margin: 0 auto;
  padding: 0;
  list-style: none;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  background: var(--line);
  border: 1px solid var(--line);
  border-radius: 20px;
  overflow: hidden;
}

.tz-flow__step {
  background: #fff;
  padding: 28px 24px;

  &:hover {
    background: #fcfcfd;
  }
}

.tz-flow__index {
  display: block;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.08em;
  color: var(--green-bright);
  margin-bottom: 14px;
}

.tz-flow__title {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 500;
}

.tz-flow__desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--ink-2);
}

/* ---------------------------------------------------------------- 诊断链路 */
.tz-steps {
  max-width: 1040px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 18px;
}

.tz-step {
  border: 1px solid rgba(245, 245, 247, 0.14);
  border-radius: 18px;
  padding: 26px 22px;
  background: rgba(245, 245, 247, 0.03);
}

.tz-step__badge {
  display: inline-block;
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.04em;
  color: var(--green-bright);
  border: 1px solid rgba(29, 158, 117, 0.4);
  border-radius: 980px;
  padding: 3px 10px;
  margin-bottom: 18px;
}

.tz-step__title {
  margin: 0 0 10px;
  font-size: 16px;
  font-weight: 500;
  color: #f5f5f7;
}

.tz-step__desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.72;
  color: rgba(245, 245, 247, 0.62);
}

.tz-note {
  max-width: 720px;
  margin: 52px auto 0;
  padding-top: 26px;
  border-top: 1px solid rgba(245, 245, 247, 0.12);
  text-align: center;
  font-size: 15px;
  line-height: 1.72;
  color: rgba(245, 245, 247, 0.82);
}

/* ---------------------------------------------------------------- 严谨性 */
.tz-trust {
  max-width: 1040px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.tz-trust__item {
  padding: 28px 26px;
  border-radius: 20px;
  background: var(--bg-alt);
}

.tz-trust__title {
  margin: 0 0 12px;
  font-size: 17px;
  font-weight: 500;
  letter-spacing: -0.012em;
}

.tz-trust__desc {
  margin: 0;
  font-size: 13.5px;
  line-height: 1.78;
  color: var(--ink-2);
}

/* ---------------------------------------------------------------- 可扩展 */
.tz-crops {
  max-width: 1040px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.tz-crop {
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 20px;
  padding: 28px 26px;

  &[data-state='active'] {
    border-color: rgba(15, 110, 86, 0.3);
    box-shadow: 0 10px 34px rgba(15, 110, 86, 0.08);
  }
}

.tz-crop__name {
  font-size: 22px;
  font-weight: 500;
  letter-spacing: -0.018em;
}

.tz-crop__state {
  margin-top: 8px;
  font-size: 12px;
  font-weight: 500;
  color: var(--green);

  .tz-crop[data-state='reserved'] & {
    color: var(--ink-3);
  }
}

.tz-crop__desc {
  margin: 14px 0 0;
  font-size: 13.5px;
  line-height: 1.72;
  color: var(--ink-2);
}

/* ---------------------------------------------------------------- 页脚 */
.tz-footer {
  background: var(--bg-alt);
  border-top: 1px solid var(--line);
  padding: 56px 24px 64px;
}

.tz-footer__inner {
  max-width: 1040px;
  margin: 0 auto;
}

.tz-footer__brand {
  font-size: 15px;
  font-weight: 500;
}

.tz-footer__disclaimer {
  max-width: 760px;
  margin: 14px 0 0;
  font-size: 12.5px;
  line-height: 1.8;
  color: var(--ink-3);
}

.tz-footer__links {
  margin-top: 26px;
  padding-top: 22px;
  border-top: 1px solid var(--line);
  display: flex;
  flex-wrap: wrap;
  gap: 26px;

  a {
    font-size: 13px;
    color: var(--ink-2);
    text-decoration: none;
    transition: color 0.2s;

    &:hover {
      color: var(--green);
    }
  }
}

/* ---------------------------------------------------------------- 滚动入场 */
.reveal {
  opacity: 0;
  transform: translateY(22px);
  transition: opacity 0.75s cubic-bezier(0.16, 1, 0.3, 1),
    transform 0.75s cubic-bezier(0.16, 1, 0.3, 1);

  &.is-in {
    opacity: 1;
    transform: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .reveal {
    opacity: 1;
    transform: none;
    transition: none;
  }

  .tz-mock__dot {
    animation: none;
  }
}

/* ---------------------------------------------------------------- 响应式 */
@media (max-width: 900px) {
  .tz-nav__links {
    display: none;
  }

  .tz-cards,
  .tz-flow,
  .tz-trust,
  .tz-crops {
    grid-template-columns: repeat(2, 1fr);
  }

  .tz-steps {
    grid-template-columns: repeat(2, 1fr);
  }

  .tz-metrics__grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 32px 20px;
  }
}

@media (max-width: 640px) {
  .tz-hero {
    padding: 44px 20px 64px;
  }

  .tz-section {
    padding: 68px 20px;
  }

  .tz-mock {
    flex-direction: column;
    padding: 22px 18px;
  }

  .tz-mock__flow {
    transform: rotate(90deg);
  }

  .tz-cards,
  .tz-flow,
  .tz-trust,
  .tz-crops,
  .tz-steps {
    grid-template-columns: 1fr;
  }

  .tz-footer__links {
    gap: 18px;
  }
}
</style>
