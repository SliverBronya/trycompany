<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="地块" prop="plotId">
        <el-select v-model="queryParams.plotId" placeholder="请选择地块" clearable filterable style="width: 200px">
          <el-option v-for="item in plotOptions" :key="item.plotId" :label="item.plotName" :value="item.plotId" />
        </el-select>
      </el-form-item>
      <el-form-item label="诊断结果" prop="diagnosisName">
        <el-input
          v-model="queryParams.diagnosisName"
          placeholder="请输入诊断结果"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="风险等级" prop="riskLevel">
        <el-select v-model="queryParams.riskLevel" placeholder="风险等级" clearable style="width: 200px">
          <el-option v-for="dict in tz_risk_level" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="状态" clearable style="width: 200px">
          <el-option v-for="dict in tz_record_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="巡田时间">
        <el-date-picker
          v-model="dateRange"
          value-format="YYYY-MM-DD"
          type="daterange"
          range-separator="-"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 240px"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['tz:record:add']">新建巡田</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['tz:record:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-tag v-if="aiConfig.mode === 'preset'" type="warning" effect="plain">
          未配置大模型 Key，当前为预置映射 + 知识库检索模式
        </el-tag>
        <el-tag v-else type="success" effect="plain">{{ aiConfig.modeText }}</el-tag>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="recordList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="缩略图" align="center" width="90">
        <template #default="scope">
          <el-image
            v-if="scope.row.imageUrl"
            :src="resolveImageUrl(scope.row.imageUrl)"
            :preview-src-list="[resolveImageUrl(scope.row.imageUrl)]"
            preview-teleported
            fit="cover"
            style="width: 48px; height: 48px; border-radius: 4px"
          />
          <span v-else class="tz-muted">无图</span>
        </template>
      </el-table-column>
      <el-table-column label="地块" align="center" prop="plotName" width="130" :show-overflow-tooltip="true" />
      <el-table-column label="巡田时间" align="center" prop="scoutTime" width="110">
        <template #default="scope">
          <span>{{ parseTime(scope.row.scoutTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="部位" align="center" prop="plantPart" width="80">
        <template #default="scope">
          <dict-tag :options="tz_plant_part" :value="scope.row.plantPart" />
        </template>
      </el-table-column>
      <el-table-column label="症状描述" align="center" prop="symptomText" :show-overflow-tooltip="true">
        <template #default="scope">
          <span v-if="scope.row.symptomText">{{ scope.row.symptomText }}</span>
          <span v-else class="tz-muted">未填写</span>
        </template>
      </el-table-column>
      <el-table-column label="诊断结果" align="center" prop="diagnosisName" width="130" :show-overflow-tooltip="true">
        <template #default="scope">
          <a v-if="scope.row.diagnosisName" class="link-type" style="cursor: pointer" @click="showDiagnosis(scope.row)">
            {{ scope.row.diagnosisName }}
          </a>
          <span v-else class="tz-muted">未诊断</span>
        </template>
      </el-table-column>
      <el-table-column label="来源" align="center" prop="diagnosisSource" width="100">
        <template #default="scope">
          <dict-tag v-if="scope.row.diagnosisSource" :options="tz_diagnosis_source" :value="scope.row.diagnosisSource" />
        </template>
      </el-table-column>
      <el-table-column label="风险" align="center" prop="riskLevel" width="90">
        <template #default="scope">
          <dict-tag v-if="scope.row.riskLevel" :options="tz_risk_level" :value="scope.row.riskLevel" />
        </template>
      </el-table-column>
      <el-table-column label="剂量拦截" align="center" prop="dosageGuardHit" width="90">
        <template #default="scope">
          <el-tag v-if="scope.row.dosageGuardHit === '1'" type="danger" effect="plain">已拦截</el-tag>
          <span v-else class="tz-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="110">
        <template #default="scope">
          <dict-tag :options="tz_record_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="260" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="MagicStick" @click="handleDiagnose(scope.row)" v-hasPermi="['tz:record:diagnose']">
            {{ scope.row.diagnosisName ? '重新诊断' : 'AI 诊断' }}
          </el-button>
          <el-button
            link
            type="primary"
            icon="Document"
            :disabled="!scope.row.diagnosisName"
            @click="handleSuggestion(scope.row)"
            v-hasPermi="['tz:record:diagnose']"
          >防治建议</el-button>
          <el-button
            link
            type="primary"
            icon="Tickets"
            :disabled="!scope.row.diagnosisName"
            @click="handleReport(scope.row)"
            v-hasPermi="['tz:record:report']"
          >巡田报告</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['tz:record:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['tz:record:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 新建 / 修改巡田记录 -->
    <el-dialog :title="title" v-model="open" width="720px" append-to-body>
      <el-form ref="recordRef" :model="form" :rules="rules" label-width="90px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="地块" prop="plotId">
              <el-select v-model="form.plotId" placeholder="请选择地块" filterable style="width: 100%">
                <el-option v-for="item in plotOptions" :key="item.plotId" :label="item.plotName" :value="item.plotId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="巡田时间" prop="scoutTime">
              <el-date-picker
                v-model="form.scoutTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="默认取当前时间"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发生部位" prop="plantPart">
              <el-select v-model="form.plantPart" placeholder="请选择部位" style="width: 100%">
                <el-option v-for="dict in tz_plant_part" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="严重度" prop="severity">
              <el-select v-model="form.severity" placeholder="请选择严重度" style="width: 100%">
                <el-option v-for="dict in tz_severity" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="病叶照片" prop="imageUrl">
              <image-upload v-model="form.imageUrl" :limit="1" :file-size="5" />
              <div class="tz-hint">
                必填。上传后系统会先自动识别这张照片、把下面的「症状描述」填好，你只需在上面核对修改。
                照片本身也是诊断的主要依据：先按 MD5 匹配预置样张（保证离线可用），未命中且已配置大模型 Key 时，
                再交给多模态模型初诊。
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="症状描述" prop="symptomText">
              <el-input
                v-model="form.symptomText"
                type="textarea"
                :rows="4"
                placeholder="上传照片后会自动生成，也可以直接在这里手写；例如：叶片出现近圆形病斑，中央木栓化隆起，周围有黄色晕圈"
              />
              <div class="tz-desc-state">
                <span v-if="describe.running" class="tz-hint">
                  <el-icon class="is-loading"><Loading /></el-icon>
                  正在识别照片并生成描述，通常几秒钟…
                </span>
                <span v-else-if="describe.sourceText" class="tz-hint tz-hint-ok">
                  <el-icon><CircleCheck /></el-icon>
                  {{ describe.sourceText }}
                </span>
                <span v-else-if="describe.failReason" class="tz-hint tz-hint-warn">
                  <el-icon><WarningFilled /></el-icon>
                  未能自动生成描述：{{ describe.failReason }}
                </span>
                <span v-else class="tz-hint">
                  可选填。这段文字是知识库检索的依据，写得越具体越容易命中；描述太笼统时系统会明说「没把握」，
                  而不是硬给一个结论。
                </span>
                <el-button
                  link
                  type="primary"
                  size="small"
                  icon="MagicStick"
                  :loading="describe.running"
                  :disabled="!form.imageUrl"
                  @click="runDescribe(true)"
                  v-hasPermi="['tz:record:add', 'tz:record:edit']"
                >{{ describe.sourceText || describe.failReason ? '重新识别照片' : 'AI 识别照片' }}</el-button>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :disabled="describe.running" @click="submitForm">
            {{ describe.running ? "识别中…" : "确 定" }}
          </el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 诊断结果 -->
    <el-dialog v-model="diagnosisOpen" title="AI 诊断结论" width="720px" append-to-body>
      <div v-loading="diagnosisLoading">
        <el-alert
          v-if="diagnosis.degraded"
          type="warning"
          :closable="false"
          show-icon
          :title="'本次结论为降级结果：' + (diagnosis.degradeReason || '大模型调用未成功')"
          style="margin-bottom: 12px"
        />
        <el-descriptions :column="2" border>
          <el-descriptions-item label="诊断结论">
            <span class="tz-strong">{{ diagnosis.diagnosisName || '—' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="风险等级">
            <dict-tag v-if="diagnosis.riskLevel" :options="tz_risk_level" :value="diagnosis.riskLevel" />
            <span v-else>—</span>
          </el-descriptions-item>
          <el-descriptions-item label="置信度">
            <span v-if="diagnosis.confidence != null">{{ diagnosis.confidence }}</span>
            <span v-else>—</span>
          </el-descriptions-item>
          <el-descriptions-item label="结论来源">
            <dict-tag v-if="diagnosis.source" :options="tz_diagnosis_source" :value="diagnosis.source" />
            <el-tooltip v-if="diagnosis.sourceText" :content="diagnosis.sourceText" placement="top">
              <el-icon style="margin-left: 6px"><QuestionFilled /></el-icon>
            </el-tooltip>
          </el-descriptions-item>
          <el-descriptions-item label="模型" :span="2">{{ diagnosis.model || '未调用大模型' }}</el-descriptions-item>
          <el-descriptions-item label="耗时" :span="2">
            <span v-if="diagnosis.latencyMs != null">{{ diagnosis.latencyMs }} ms</span>
            <span v-else>—</span>
          </el-descriptions-item>
          <el-descriptions-item label="判断依据" :span="2">
            <pre class="tz-pre">{{ diagnosis.diagnosisBasis || '—' }}</pre>
          </el-descriptions-item>
          <el-descriptions-item v-if="diagnosis.alternatives" label="其他可能" :span="2">
            <pre class="tz-pre">{{ diagnosis.alternatives }}</pre>
          </el-descriptions-item>
          <el-descriptions-item v-if="diagnosis.matchedKnowledge && diagnosis.matchedKnowledge.length" label="命中的知识条目" :span="2">
            <el-tag v-for="(name, idx) in diagnosis.matchedKnowledge" :key="idx" effect="plain" style="margin: 0 6px 6px 0">
              {{ name }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <el-alert
          v-if="diagnosis.disclaimer"
          type="info"
          :closable="false"
          show-icon
          :title="diagnosis.disclaimer"
          style="margin-top: 12px"
        />
      </div>
    </el-dialog>

    <!-- 防治建议 -->
    <el-dialog v-model="suggestionOpen" title="防治建议" width="760px" append-to-body>
      <div v-loading="suggestionLoading">
        <el-alert
          v-if="suggestion.dosageGuardHit"
          type="error"
          :closable="false"
          show-icon
          title="本次建议中的部分用量未通过剂量校验，已被拦截替换"
          style="margin-bottom: 12px"
        >
          <template #default>
            <div>{{ suggestion.guardExplain }}</div>
            <div v-if="suggestion.rejectedDosages && suggestion.rejectedDosages.length" style="margin-top: 6px">
              被拦截的表述：
              <el-tag v-for="(d, idx) in suggestion.rejectedDosages" :key="idx" type="danger" effect="plain" style="margin: 0 6px 6px 0">
                {{ d }}
              </el-tag>
            </div>
          </template>
        </el-alert>
        <el-alert
          v-if="suggestion.degraded"
          type="warning"
          :closable="false"
          show-icon
          :title="'本次建议为降级结果：' + (suggestion.degradeReason || '大模型调用未成功')"
          style="margin-bottom: 12px"
        />
        <el-descriptions :column="2" border style="margin-bottom: 12px">
          <el-descriptions-item label="诊断结论">{{ suggestion.diagnosisName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="建议来源">
            <dict-tag v-if="suggestion.source" :options="tz_diagnosis_source" :value="suggestion.source" />
            <el-tooltip v-if="suggestion.sourceText" :content="suggestion.sourceText" placement="top">
              <el-icon style="margin-left: 6px"><QuestionFilled /></el-icon>
            </el-tooltip>
          </el-descriptions-item>
          <el-descriptions-item label="依据知识条目">{{ suggestion.knowledgeName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="条目来源">{{ suggestion.knowledgeSource || '—' }}</el-descriptions-item>
        </el-descriptions>
        <pre class="tz-pre tz-box">{{ suggestion.suggestion || '—' }}</pre>
        <el-alert
          v-if="suggestion.disclaimer"
          type="info"
          :closable="false"
          show-icon
          :title="suggestion.disclaimer"
          style="margin-top: 12px"
        />
      </div>
      <template #footer>
        <el-button @click="suggestionOpen = false">关 闭</el-button>
        <el-button type="primary" @click="copyText(suggestion.suggestion, '防治建议')">复制建议</el-button>
      </template>
    </el-dialog>

    <!-- 巡田报告 -->
    <el-drawer v-model="reportOpen" title="巡田报告" size="60%" direction="rtl">
      <div v-loading="reportLoading">
        <el-alert
          v-if="report.suggestionReused === false"
          type="info"
          :closable="false"
          show-icon
          title="生成报告时尚未有防治建议，已自动生成一份并一并写入"
          style="margin-bottom: 12px"
        />
        <el-alert
          v-if="report.followUpTaskId"
          type="success"
          :closable="false"
          show-icon
          style="margin-bottom: 12px"
          :title="(report.followUpTaskCreated ? '已自动生成复查任务：' : '已存在待办复查任务：') + report.followUpTaskTitle"
        >
          <template #default>
            截止日期 {{ parseTime(report.followUpDueDate, '{y}-{m}-{d}') }}，可在「复查任务」页处理。
          </template>
        </el-alert>
        <!-- 报告正文由后端生成，其中每处动态内容都已转义；此处 v-html 是渲染入口而非信任入口 -->
        <div class="tz-report" v-html="report.reportText"></div>
      </div>
      <template #footer>
        <el-button @click="reportOpen = false">关 闭</el-button>
        <el-button type="primary" @click="printReport">打 印</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup name="TzRecord">
import { listRecord, getRecord, delRecord, addRecord, updateRecord, describeRecordImage } from "@/api/tianzhen/record"
import { plotOptionSelect } from "@/api/tianzhen/plot"
import { diagnose, getDiagnosisResult } from "@/api/tianzhen/diagnosis"
import { generateSuggestion, getSuggestionResult } from "@/api/tianzhen/suggestion"
import { generateReport, getReportResult } from "@/api/tianzhen/report"
import { getAiConfig } from "@/api/tianzhen/ai"
import { resolveImageUrl as tzResolveImageUrl } from "@/utils/tzImage"

const { proxy } = getCurrentInstance()
const { tz_plant_part, tz_severity, tz_risk_level, tz_record_status, tz_diagnosis_source } =
  useDict("tz_plant_part", "tz_severity", "tz_risk_level", "tz_record_status", "tz_diagnosis_source")

const recordList = ref([])
const plotOptions = ref([])
const aiConfig = ref({})
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const dateRange = ref([])

const diagnosisOpen = ref(false)
const diagnosisLoading = ref(false)
const diagnosis = ref({})

const suggestionOpen = ref(false)
const suggestionLoading = ref(false)
const suggestion = ref({})

const reportOpen = ref(false)
const reportLoading = ref(false)
const report = ref({})

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    plotId: undefined,
    diagnosisName: undefined,
    riskLevel: undefined,
    status: undefined
  },
  rules: {
    plotId: [{ required: true, message: "地块不能为空", trigger: "change" }],
    // 照片必填：诊断以图像为主要依据，预置样张匹配更是只看图片哈希，没图连保底路径都走不了。
    // 症状描述反过来不再强制 —— 照片一上传系统就会自动写一版，用户在它上面改；
    // 想自己手写也行，两样都没有时记录照样存得下，只是知识库检索的依据弱一些。
    imageUrl: [{ required: true, message: "请上传现场照片：没有照片无法进行图像诊断", trigger: "change" }]
  }
})

const { queryParams, form, rules } = toRefs(data)

/**
 * 已经针对哪张照片识别过描述。
 *
 * 用它是为了把「用户新传了一张图」和「编辑时把已有图回填进表单」区分开：
 * 后者不该触发识别 —— 一打开修改弹窗就重跑一次，既白花一次调用，
 * 又会把用户上次存下来的描述当场覆盖掉。
 */
const describedImageUrl = ref("")

/** 上一次由系统自动填入的描述，用来判断描述框里的话是不是用户自己写的 */
const lastAutoText = ref("")

/** 照片识别的界面状态 */
const describe = reactive({
  running: false,
  sourceText: "",
  failReason: ""
})

/** 查询巡田记录列表 */
function getList() {
  loading.value = true
  listRecord(proxy.addDateRange(queryParams.value, dateRange.value)).then(response => {
    recordList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

/** 地块下拉选项 */
function getPlotOptions() {
  plotOptionSelect().then(response => {
    plotOptions.value = response.data || []
  })
}

/**
 * 图片地址归一化。实现搬到 @/utils/tzImage —— 这里原先保留绝对地址，
 * 而绝对地址里的 host 只在本机成立，公网访问时图片会全裂。详见该文件注释。
 */
const resolveImageUrl = tzResolveImageUrl

/** 取消按钮 */
function cancel() {
  open.value = false
  reset()
}

/** 表单重置 */
function reset() {
  form.value = {
    recordId: undefined,
    plotId: undefined,
    scoutTime: undefined,
    imageUrl: undefined,
    plantPart: undefined,
    severity: undefined,
    symptomText: undefined,
    remark: undefined
  }
  // 识别状态跟着表单一起清。describedImageUrl 必须清 —— 不清的话，
  // 下次「新建」再传同一张图会被判成「已经识别过」，描述就再也填不出来了
  describedImageUrl.value = ""
  lastAutoText.value = ""
  describe.running = false
  describe.sourceText = ""
  describe.failReason = ""
  proxy.resetForm("recordRef")
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  dateRange.value = []
  proxy.resetForm("queryRef")
  handleQuery()
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.recordId)
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "新建巡田记录"
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  const recordId = row.recordId || ids.value[0]
  getRecord(recordId).then(response => {
    // 先把回填的图片记为「已处理」，再把数据写进表单。watch 回调是在微任务里跑的，
    // 等到它执行时看到的已经是「这张图不需要识别」，于是打开弹窗不会触发识别。
    describedImageUrl.value = response.data.imageUrl || ""
    form.value = response.data
    open.value = true
    title.value = "修改巡田记录"
  })
}

/**
 * 照片一换就自动识别一次。
 *
 * image-upload 是自定义组件，不会像 el-input 那样往外抛 change 事件，
 * 所以这里除了触发识别，还要主动重校验一次：不然用户先点「确定」被告知缺照片、
 * 补传之后，那条红字会一直挂到下次提交才消失。
 */
watch(
  () => form.value.imageUrl,
  val => {
    nextTick(() => {
      const formRef = proxy.$refs.recordRef
      if (formRef) {
        // 传回调而不是靠 promise：校验不通过时它有回调就不会 reject，
        // 省掉一层没人处理的 catch
        formRef.validateField("imageUrl", () => {})
      }
    })

    if (!val || val === describedImageUrl.value) return
    describedImageUrl.value = val
    runDescribe(false)
  }
)

/** 描述框里有没有「用户自己的话」——上一次自动生成的那段不算，覆盖它不必再问 */
function hasUserText() {
  const current = (form.value.symptomText || "").trim()
  return current.length > 0 && current !== lastAutoText.value
}

/**
 * 触发一次照片识别。
 *
 * @param manual true 表示用户自己点了按钮，false 表示照片刚上传、系统自动触发
 */
function runDescribe(manual) {
  const imageUrl = form.value.imageUrl
  if (!imageUrl) {
    if (manual) proxy.$modal.msgWarning("请先上传现场照片")
    return
  }
  // 已经写过东西就先问一句。识别结果是直接覆盖写入输入框的，
  // 不打招呼就把用户刚敲的描述冲掉，比慢几秒更让人恼火
  if (hasUserText()) {
    proxy.$modal
      .confirm("识别结果会覆盖当前的症状描述，是否继续？")
      .then(() => doDescribe(imageUrl))
      .catch(() => {})
    return
  }
  doDescribe(imageUrl)
}

function doDescribe(imageUrl) {
  describe.running = true
  describe.sourceText = ""
  describe.failReason = ""
  describeRecordImage(imageUrl, form.value.plantPart)
    .then(response => {
      const data = response.data || {}
      // 连着换图时，先发出去的那次可能后回来。只认当前这张图的结果，
      // 否则用户会看到描述被上一张照片的识别结果盖掉
      if (data.imageUrl && data.imageUrl !== form.value.imageUrl) return
      describe.running = false
      if (data.success && data.description) {
        form.value.symptomText = data.description
        lastAutoText.value = data.description
        describe.sourceText = data.sourceText || "已根据照片自动生成描述，可自行修改"
      } else {
        describe.failReason = data.degradeReason || "大模型没有返回可用的描述"
      }
    })
    .catch(() => {
      describe.running = false
      describe.failReason = "识别请求未成功，请稍后重试，或直接手动填写描述"
    })
}

/**
 * 提交按钮。
 *
 * 新建成功后追问一句「是否立即诊断」：现场登记完顺手就要结论，
 * 让用户自己再回列表找到那一行去点诊断，是把流程拆断了。
 */
function submitForm() {
  proxy.$refs["recordRef"].validate(valid => {
    if (valid) {
      if (form.value.recordId != undefined) {
        updateRecord(form.value).then(() => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addRecord(form.value).then(response => {
          proxy.$modal.msgSuccess("新建成功")
          open.value = false
          getList()
          const recordId = response.data && response.data.recordId
          if (recordId) {
            proxy.$modal.confirm("记录已保存，是否立即执行 AI 诊断？").then(() => {
              runDiagnose(recordId)
            }).catch(() => {})
          }
        })
      }
    }
  })
}

/** AI 诊断 */
function handleDiagnose(row) {
  runDiagnose(row.recordId)
}

function runDiagnose(recordId) {
  const loadingInstance = proxy.$modal.loading("正在诊断，请稍候…")
  diagnose(recordId, true).then(response => {
    proxy.$modal.closeLoading()
    diagnosis.value = response.data || {}
    diagnosisOpen.value = true
    getList()
  }).catch(() => {
    proxy.$modal.closeLoading()
  })
}

/** 查看已有诊断结论 */
function showDiagnosis(row) {
  diagnosisLoading.value = true
  diagnosisOpen.value = true
  getDiagnosisResult(row.recordId).then(response => {
    diagnosis.value = response.data || {}
    diagnosisLoading.value = false
  }).catch(() => {
    diagnosisLoading.value = false
  })
}

/**
 * 防治建议。
 *
 * 已有建议时先读缓存，读不到再生成 —— 避免每次打开都重跑一遍大模型，
 * 白白花钱还会让同样的记录给出前后不一致的建议。
 */
function handleSuggestion(row) {
  suggestionLoading.value = true
  suggestionOpen.value = true
  suggestion.value = {}
  getSuggestionResult(row.recordId).then(response => {
    if (response.data && response.data.suggestion) {
      suggestion.value = response.data
      suggestionLoading.value = false
    } else {
      buildSuggestion(row.recordId)
    }
  }).catch(() => {
    buildSuggestion(row.recordId)
  })
}

function buildSuggestion(recordId) {
  suggestionLoading.value = true
  generateSuggestion(recordId, true).then(response => {
    suggestion.value = response.data || {}
    suggestionLoading.value = false
    getList()
  }).catch(() => {
    suggestionLoading.value = false
    suggestionOpen.value = false
  })
}

/** 巡田报告 */
function handleReport(row) {
  reportLoading.value = true
  reportOpen.value = true
  report.value = {}
  getReportResult(row.recordId).then(response => {
    if (response.data && response.data.reportText) {
      report.value = response.data
      reportLoading.value = false
    } else {
      buildReport(row.recordId)
    }
  }).catch(() => {
    buildReport(row.recordId)
  })
}

function buildReport(recordId) {
  reportLoading.value = true
  generateReport(recordId, true).then(response => {
    report.value = response.data || {}
    reportLoading.value = false
    getList()
  }).catch(() => {
    reportLoading.value = false
    reportOpen.value = false
  })
}

/** 打印报告：只把正文送进打印窗口，避免把整个后台界面一起打出来 */
function printReport() {
  const win = window.open("", "_blank")
  if (!win) {
    proxy.$modal.msgWarning("浏览器拦截了打印窗口，请允许弹出窗口后重试")
    return
  }
  win.document.write(
    '<html><head><title>巡田报告</title><style>' +
      'body{font-family:"Microsoft YaHei",sans-serif;padding:24px;line-height:1.7;}' +
      'table{border-collapse:collapse;width:100%;}th,td{border:1px solid #999;padding:6px 8px;text-align:left;}' +
      'th{background:#f5f5f5;width:120px;}h3{border-left:4px solid #409eff;padding-left:8px;}' +
      '</style></head><body>' +
      report.value.reportText +
      "</body></html>"
  )
  win.document.close()
  win.focus()
  win.print()
}

/**
 * 复制纯文本。
 *
 * 报告正文是 HTML，直接复制会带上标签；用 DOMParser 取文本节点再写剪贴板，
 * 用户粘进微信或 Word 才是干净的。
 */
function copyText(html, label) {
  if (!html) {
    proxy.$modal.msgWarning("暂无可复制的内容")
    return
  }
  const container = document.createElement("div")
  container.innerHTML = html
  const text = container.textContent || container.innerText || ""
  if (navigator.clipboard && window.isSecureContext) {
    navigator.clipboard.writeText(text).then(() => {
      proxy.$modal.msgSuccess(label + "已复制")
    }).catch(() => {
      proxy.$modal.msgWarning("复制失败，请手动选择文本")
    })
  } else {
    proxy.$modal.msgWarning("当前环境不支持一键复制，请手动选择文本")
  }
}

/** 删除按钮操作 */
function handleDelete(row) {
  const recordIds = row.recordId || ids.value
  proxy.$modal.confirm('是否确认删除巡田记录编号为"' + recordIds + '"的数据项？其名下的复查任务会一并删除。').then(function () {
    return delRecord(recordIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

getPlotOptions()
getList()
getAiConfig().then(response => {
  aiConfig.value = response.data || {}
}).catch(() => {})
</script>

<style scoped>
.tz-pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: inherit;
}
.tz-box {
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  padding: 12px;
  background: var(--el-fill-color-blank);
}
.tz-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
  margin-top: 4px;
}
/* 「症状描述」下面那行状态文字与识别按钮：横排，文字占了剩余宽度 */
.tz-desc-state {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  flex-wrap: wrap;
}
.tz-desc-state .tz-hint {
  flex: 1 1 auto;
  margin-top: 4px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
/* 识别成功/失败时把颜色带出来，否则用户分不清「填上了」和「没填上」 */
.tz-hint-ok {
  color: var(--el-color-success);
}
.tz-hint-warn {
  color: var(--el-color-warning);
}
.tz-muted {
  color: var(--el-text-color-secondary);
}
.tz-strong {
  font-weight: 600;
}
.tz-report {
  line-height: 1.8;
}
.tz-report :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 8px 0;
}
.tz-report :deep(th),
.tz-report :deep(td) {
  border: 1px solid var(--el-border-color);
  padding: 6px 8px;
  text-align: left;
}
.tz-report :deep(th) {
  background: var(--el-fill-color-light);
  width: 130px;
}
.tz-report :deep(h3) {
  border-left: 4px solid var(--el-color-primary);
  padding-left: 8px;
  margin: 16px 0 8px;
}
</style>
