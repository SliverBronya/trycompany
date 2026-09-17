/**
 * 离线演示模式的 mock 层。
 *
 * 目标：把整个站点打成一个 HTML 文件，双击就能演示 —— 没有后端、没有数据库、
 * 没有 Node 环境。做法是给 axios 换一个 adapter，所有请求在浏览器内直接应答。
 *
 * 数据不是编的：`data.json` 是从本机真实跑着的后端导出来的
 * （见 .workbuddy/logos/export-demo.py），所以数字、条目、文案都能和真实系统对上。
 *
 * 几条刻意的设计：
 *
 * 1. **加了 150ms 延迟**。假响应瞬间返回的话，页面上的 loading 态一闪而过，
 *    反而显得"卡了一下"，不像真在请求。延迟让骨架屏和加载态能被看见。
 *
 * 2. **写操作真的会改内存里的数据**。新增巡田记录、完成复查、删除条目之后，
 *    列表立刻跟着变 —— 演示时"点了没反应"比"数据是假的"更容易被看出来。
 *    刷新页面即还原（数据在内存里）。
 *
 * 3. **菜单过滤掉了若依自带的系统管理/监控/工具**。演示要聚焦业务链路，
 *    留一堆跟柑橘植保无关的菜单反而分散注意力。
 *
 * 4. **未覆盖的接口会 console.warn**，而不是静默返回空 —— 免得演示时
 *    某个页面莫名空白却查不出原因。
 */
import service from '@/utils/request'
import raw from './data.json'

const LATENCY = 150

const ok = (extra) => Object.assign({ code: 200, msg: '操作成功' }, extra || {})
const list = (arr) => ok({ rows: arr, total: arr.length })

/* ------------------------------------------------------------------ 演示数据
   全部放在内存里，写操作改的就是这里；刷新页面回到初始状态。 */
const db = {
  records: JSON.parse(JSON.stringify(raw.records.rows || [])),
  plots: JSON.parse(JSON.stringify(raw.plots.rows || [])),
  knowledge: JSON.parse(JSON.stringify(raw.knowledge.rows || [])),
  followups: JSON.parse(JSON.stringify(raw.followups.rows || [])),
  sessions: JSON.parse(JSON.stringify(raw.qaSessions.rows || [])),
  messages: {} // sessionId -> 消息数组，首次访问时用预置问答填充
}

/* 演示版的菜单：去掉若依自带模块，只留业务链路 */
const HIDDEN_MENUS = ['系统管理', '系统监控', '系统工具', '若依官网', '测试菜单']

function demoRouters() {
  const all = (raw.getRouters.data || [])
  const kept = all.filter((m) => HIDDEN_MENUS.indexOf(m.meta && m.meta.title) < 0)
  return ok({ data: kept })
}

/* ------------------------------------------------------------------ 问答预置
   演示时用户可能真的提问。答不了就返回一段说明，而不是空着。 */
const PRESET_ANSWER = {
  answer: '这是离线演示版本，问答由本地预置内容应答，不会真正调用大模型。',
  guidance: '要体验真实的 AI 诊断与问答，请在有后端服务的环境下运行完整版本。'
    + '完整版会调用多模态模型读图、检索本地植保知识库，并把用药与用量与知识库逐条比对。',
  basis: '离线演示模式的设计说明',
  sources: ['离线演示模式'],
  disclaimer: '本回答由离线演示版本生成，仅用于功能展示。',
  answerStatus: '2',
  source: 'demo',
  sourceText: '离线演示：本地预置应答',
  degraded: true,
  degradeReason: '离线演示模式未接入大模型',
  matchedKnowledge: [],
  refused: false,
  latencyMs: LATENCY
}

/* ------------------------------------------------------------------ 路由表 */

const num = (v) => Number(v)

const routes = [
  /* ---- 登录与会话 ---- */
  { m: 'post', p: /^\/login$/, h: () => ok({ token: 'demo-token-offline', expires_in: 720 }) },
  { m: 'get', p: /^\/getInfo$/, h: () => raw.getInfo },
  { m: 'get', p: /^\/getRouters$/, h: () => demoRouters() },
  { m: 'get', p: /^\/captchaImage$/, h: () => ok({ captchaEnabled: false, uuid: 'demo' }) },
  { m: 'post', p: /^\/logout$/, h: () => ok() },

  /* ---- 看板 ---- */
  { m: 'get', p: /^\/tz\/dashboard\/stats$/, h: () => raw.dashStats },
  { m: 'get', p: /^\/tz\/dashboard\/charts$/, h: () => raw.dashCharts },

  /* ---- 巡田记录 ---- */
  { m: 'get', p: /^\/tz\/record\/list$/, h: () => list(db.records) },
  {
    m: 'get', p: /^\/tz\/record\/\d+$/, h: (ctx) => {
      const id = num(ctx.url.split('/').pop())
      const hit = db.records.find((r) => num(r.recordId) === id)
      return hit ? ok({ data: hit }) : { code: 404, msg: '演示数据中没有这条记录' }
    }
  },
  {
    m: 'post', p: /^\/tz\/record$/, h: (ctx) => {
      const body = ctx.body || {}
      const id = 9000 + db.records.length
      db.records.unshift(Object.assign({
        recordId: id, companyId: 1, deptId: 100, createTime: nowStr(), status: '0',
        imageUrl: '', riskLevel: '1'
      }, body))
      return ok({ data: { recordId: id } })
    }
  },
  {
    m: 'put', p: /^\/tz\/record$/, h: (ctx) => {
      const body = ctx.body || {}
      const i = db.records.findIndex((r) => num(r.recordId) === num(body.recordId))
      if (i >= 0) Object.assign(db.records[i], body)
      return ok()
    }
  },
  {
    m: 'delete', p: /^\/tz\/record\//, h: (ctx) => {
      const ids = ctx.url.split('/').pop().split(',').map(num)
      db.records = db.records.filter((r) => ids.indexOf(num(r.recordId)) < 0)
      return ok()
    }
  },

  /* ---- 地块 ---- */
  { m: 'get', p: /^\/tz\/plot\/list$/, h: () => list(db.plots) },
  {
    m: 'get', p: /^\/tz\/plot\/optionselect$/i, h: () => ok({
      data: db.plots.map((p) => ({ plotId: p.plotId, plotName: p.plotName }))
    })
  },
  { m: 'get', p: /^\/tz\/plot\/\d+$/, h: (ctx) => {
      const id = num(ctx.url.split('/').pop())
      const hit = db.plots.find((p) => num(p.plotId) === id)
      return hit ? ok({ data: hit }) : { code: 404, msg: '未找到地块' }
    } },

  /* ---- 知识库 ---- */
  { m: 'get', p: /^\/tz\/knowledge\/list$/, h: () => list(db.knowledge) },
  {
    m: 'get', p: /^\/tz\/knowledge\/\d+$/, h: (ctx) => {
      const id = num(ctx.url.split('/').pop())
      const hit = db.knowledge.find((k) => num(k.knowledgeId) === id)
      return hit ? ok({ data: hit }) : { code: 404, msg: '未找到知识库条目' }
    }
  },

  /* ---- 复查任务 ---- */
  { m: 'get', p: /^\/tz\/followup\/list$/, h: () => list(db.followups) },
  { m: 'get', p: /^\/tz\/followup\/\d+$/, h: (ctx) => {
      const id = num(ctx.url.split('/').pop())
      const hit = db.followups.find((f) => num(f.taskId) === id)
      return hit ? ok({ data: hit }) : { code: 404, msg: '未找到复查任务' }
    } },
  {
    m: 'put', p: /^\/tz\/followup\/status$/, h: (ctx) => {
      const body = ctx.body || {}
      const i = db.followups.findIndex((f) => num(f.taskId) === num(body.taskId))
      if (i >= 0) Object.assign(db.followups[i], body, { finishTime: body.status === '1' ? nowStr() : db.followups[i].finishTime })
      return ok()
    }
  },
  {
    m: 'delete', p: /^\/tz\/followup\//, h: (ctx) => {
      const ids = ctx.url.split('/').pop().split(',').map(num)
      db.followups = db.followups.filter((f) => ids.indexOf(num(f.taskId)) < 0)
      return ok()
    }
  },

  /* ---- 问答 ---- */
  { m: 'post', p: /^\/tz\/qa\/ask$/, h: (ctx) => {
      const q = ctx.params.question || '（空问题）'
      let sid = ctx.params.sessionId
      if (!sid) {
        sid = 90000 + db.sessions.length
        db.sessions.unshift({ sessionId: sid, sessionTitle: String(q).slice(0, 20), msgCount: 0, createTime: nowStr() })
      }
      const arr = db.messages[sid] || (db.messages[sid] = [])
      arr.push({ messageId: sid + '-' + arr.length + '-u', role: 'user', content: q })
      arr.push(Object.assign({ messageId: sid + '-' + arr.length + '-a', role: 'assistant' },
        PRESET_ANSWER, { sessionId: sid }))
      const s = db.sessions.find((x) => num(x.sessionId) === num(sid))
      if (s) s.msgCount = arr.length
      return ok({ data: Object.assign({ sessionId: sid, question: q }, PRESET_ANSWER) })
    } },
  {
    m: 'get', p: /^\/tz\/qa\/messages\/\d+$/, h: (ctx) => {
      const sid = num(ctx.url.split('/').pop())
      let arr = db.messages[sid]
      if (!arr) {
        /* 真实历史会话没有逐条存过，用一段说明占位，避免点进去是空白页 */
        arr = db.messages[sid] = [
          { messageId: sid + '-0-u', role: 'user', content: '（历史会话）这个会话的内容未包含在离线演示数据中。' },
          { messageId: sid + '-0-a', role: 'assistant', content: '离线演示版本只预置了本次会话的问答记录。你可以在下方直接提问，系统会用本地预置内容应答。' }
        ]
      }
      return ok({ data: arr })
    }
  },
  { m: 'get', p: /^\/tz\/qa\/session\/list$/, h: () => list(db.sessions) },
  {
    m: 'delete', p: /^\/tz\/qa\/session\//, h: (ctx) => {
      const ids = ctx.url.split('/').pop().split(',').map(num)
      db.sessions = db.sessions.filter((s) => ids.indexOf(num(s.sessionId)) < 0)
      return ok()
    }
  },

  /* ---- 诊断与建议：演示版返回一段说明，而不是假装 AI 出结论 ---- */
  { m: 'get', p: /^\/tz\/diagnosis\/config$/, h: () => ok({
      data: Object.assign({}, (raw.aiConfig.data || {}), {
        llmAvailable: false, mode: 'preset', modeText: '离线演示模式（未接入大模型）'
      })
    }) },
  { m: 'post', p: /^\/tz\/diagnosis\/diagnose$/, h: () => ({
      code: 500,
      msg: '离线演示模式未接入大模型，无法进行真实诊断。完整版会调用多模态模型读图并检索本地知识库。'
    }) },
  { m: 'post', p: /^\/tz\/suggestion\//, h: () => ({
      code: 500,
      msg: '离线演示模式未接入大模型，无法生成防治建议。'
    }) },
  { m: 'post', p: /^\/tz\/ai\/describe/, h: () => ok({
      data: { success: false, description: '', degradeReason: '离线演示模式不调用识图模型' }
    }) },

  /* ---- 字典 ---- */
  { m: 'get', p: /^\/system\/dict\/data\/type\/tz_task_status$/, h: () => raw.dictTaskStatus },
  { m: 'get', p: /^\/system\/dict\/data\/type\/tz_risk_level$/, h: () => raw.dictRisk },
  { m: 'get', p: /^\/system\/dict\/data\/type\/tz_/, h: () => ok({ data: [] }) },
  { m: 'get', p: /^\/system\/dict\/data\/type\//, h: () => ok({ data: [] }) },

  /* ---- 市场行情、供求：真实库里就是空的，如实返回空列表 ---- */
  { m: 'get', p: /^\/tz\/market\/list$/, h: () => ok({ rows: [], total: 0 }) },
  { m: 'get', p: /^\/tz\/supply\/list$/, h: () => ok({ rows: [], total: 0 }) },

  /* ---- 通知 ---- */
  { m: 'get', p: /^\/system\/notice\/list$/i, h: () => ok({ rows: [], total: 0 }) },
  /* 顶栏的公告横条 */
  { m: 'get', p: /^\/system\/notice\/listtop$/i, h: () => ok({ data: [] }) }
]

function nowStr() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

function parseQuery(url) {
  const i = String(url || '').indexOf('?')
  const out = {}
  if (i < 0) return out
  String(url).slice(i + 1).split('&').forEach((kv) => {
    if (!kv) return
    const [k, v] = kv.split('=')
    out[decodeURIComponent(k)] = decodeURIComponent(v || '')
  })
  return out
}

/** 把 axios 的 adapter 换成内存应答 */
export function installMock() {
  service.defaults.adapter = async (config) => {
    try {
      await new Promise((r) => setTimeout(r, LATENCY))

      const full = String(config.url || '')
      const path = full.split('?')[0]
      const method = String(config.method || 'get').toLowerCase()
      let body = null
      try { body = config.data ? JSON.parse(config.data) : null } catch (e) { body = null }

      const ctx = { url: path, params: parseQuery(full), body }

      for (const r of routes) {
        if (r.m === method && r.p.test(path)) {
          const data = r.h(ctx)
          return {
            data,
            status: 200,
            statusText: 'OK',
            headers: new service.defaults.headers.constructor(),
            config,
            request: { mock: true }
          }
        }
      }

      /* 没覆盖到的接口明确报出来 —— 静默返回空会让演示时的"白屏"变得很难查 */
      console.warn('[离线演示] 未模拟的接口：' + method.toUpperCase() + ' ' + path)
      return {
        data: ok({ rows: [], total: 0, data: null }),
        status: 200,
        statusText: 'OK',
        headers: new service.defaults.headers.constructor(),
        config,
        request: { mock: true }
      }
    } catch (err) {
      /* mock 自己崩了不能让页面白屏 —— 把错误如实吐出来方便定位 */
      console.error('[离线演示] 模拟接口处理出错：', err)
      return {
        data: { code: 500, msg: '离线演示 mock 出错：' + err.message },
        status: 200,
        statusText: 'OK',
        headers: new service.defaults.headers.constructor(),
        config,
        request: { mock: true }
      }
    }
  }
  console.info('[离线演示] 已启用本地模拟接口，共 ' + routes.length + ' 条规则')
}
