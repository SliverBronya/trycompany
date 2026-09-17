<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="任务标题" prop="taskTitle">
        <el-input
          v-model="queryParams.taskTitle"
          placeholder="请输入任务标题"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="状态" clearable style="width: 200px">
          <el-option v-for="dict in tz_task_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="截止日期" prop="dueDate">
        <el-date-picker
          v-model="queryParams.dueDate"
          value-format="YYYY-MM-DD"
          type="date"
          placeholder="选择截止日期"
          clearable
          style="width: 200px"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['tz:followup:edit']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['tz:followup:remove']">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="taskList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="任务标题" align="center" prop="taskTitle" :show-overflow-tooltip="true" />
      <el-table-column label="地块" align="center" prop="plotName" width="140" :show-overflow-tooltip="true" />
      <el-table-column label="诊断结果" align="center" prop="diagnosisName" width="130" :show-overflow-tooltip="true" />
      <el-table-column label="截止日期" align="center" prop="dueDate" width="110">
        <template #default="scope">
          <span>{{ parseTime(scope.row.dueDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="100">
        <template #default="scope">
          <dict-tag :options="tz_task_status" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="复查结论" align="center" width="112">
        <template #default="scope">
          <span
            v-if="scope.row.reviewResult"
            :class="['tz-review', 'tz-review--' + scope.row.reviewResult]"
          >{{ reviewLabel(scope.row.reviewResult) }}</span>
          <span v-else class="tz-dash">—</span>
        </template>
      </el-table-column>
      <el-table-column label="完成时间" align="center" prop="finishTime" width="160">
        <template #default="scope">
          <span>{{ parseTime(scope.row.finishTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="复查备注" align="center" prop="note" :show-overflow-tooltip="true" />
      <el-table-column label="操作" align="center" width="230" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button
            link
            type="primary"
            icon="Select"
            :disabled="scope.row.status === '1'"
            @click="openFinish(scope.row)"
            v-hasPermi="['tz:followup:finish']"
          >完成复查</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['tz:followup:edit']">改期</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['tz:followup:remove']">删除</el-button>
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

    <!-- 添加或修改复查任务 -->
    <el-dialog :title="title" v-model="open" width="640px" append-to-body>
      <el-form ref="taskRef" :model="form" :rules="rules" label-width="100px">
        <el-row>
          <el-col :span="24">
            <el-form-item label="关联记录" prop="recordId">
              <el-input-number v-model="form.recordId" :min="1" :controls="false" placeholder="巡田记录 ID" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="任务标题" prop="taskTitle">
              <el-input v-model="form.taskTitle" placeholder="如：3 天后复查东坡 3 号园病斑扩展情况" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="截止日期" prop="dueDate">
              <el-date-picker v-model="form.dueDate" value-format="YYYY-MM-DD" type="date" placeholder="选择截止日期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
                <el-option v-for="dict in tz_task_status" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="复查备注" prop="note">
              <el-input v-model="form.note" type="textarea" :rows="3" placeholder="复查时观察到的实际情况" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!--
      完成复查。

      原来这一步只是一个 confirm 框，点完把 status 从 0 改成 1 —— 复查完什么也没留下：
      看不出病斑是扩大了还是缩小了，也就无从判断上次的防治到底有没有效果，
      下次遇到同类情况更翻不出可参考的历史。这就是「功能单一、只有是或否」的根源。

      现在把它变成一次真实的复查记录：结论 + 照片 + 已采取的措施 + 后续安排。
      同一地块的多次复查因此连成一条发展曲线。
    -->
    <el-dialog title="完成复查" v-model="finishOpen" width="660px" append-to-body>
      <div class="finish-head">
        <div class="finish-task">{{ finishForm.taskTitle }}</div>
        <div class="finish-meta">
          关联诊断：{{ finishForm.diagnosisName || '—' }}
          <span class="finish-dot">·</span>
          地块：{{ finishForm.plotName || '—' }}
        </div>
      </div>

      <el-form ref="finishRef" :model="finishForm" :rules="finishRules" label-width="94px">
        <el-form-item label="复查结论" prop="reviewResult">
          <el-radio-group v-model="finishForm.reviewResult">
            <el-radio-button
              v-for="opt in REVIEW_RESULTS"
              :key="opt.value"
              :value="opt.value"
            >{{ opt.label }}</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="复查照片">
          <div class="finish-photo">
            <image-upload v-model="finishForm.reviewImage" :limit="1" :file-size="5" />
            <div class="finish-hint">拍一张现在的病斑，之后可以和原诊断照片对着看</div>
          </div>
        </el-form-item>

        <el-form-item label="已采取的措施">
          <el-input
            v-model="finishForm.measureTaken"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="两次巡田之间实际做了什么：用了什么药、什么浓度、什么时候打的，以及其它农事操作"
          />
        </el-form-item>

        <el-form-item label="后续安排" prop="nextAction">
          <el-select v-model="finishForm.nextAction" placeholder="请选择后续怎么安排" style="width: 100%">
            <el-option
              v-for="opt in NEXT_ACTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <!-- 只有选了「仍需再次复查」才需要填日期，避免无谓的必填项 -->
        <el-form-item label="下次复查" v-if="finishForm.nextAction === 'recheck'">
          <el-date-picker
            v-model="finishForm.nextDate"
            value-format="YYYY-MM-DD"
            type="date"
            placeholder="选择下次复查日期"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="复查备注">
          <el-input
            v-model="finishForm.note"
            type="textarea"
            :rows="2"
            maxlength="500"
            placeholder="其他需要记录的情况"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitFinish">确认完成复查</el-button>
          <el-button @click="finishOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="TzFollowUp">
import { listFollowup, getFollowup, delFollowup, addFollowup, updateFollowup, changeFollowupStatus } from "@/api/tianzhen/followup"

const { proxy } = getCurrentInstance()
const { tz_task_status } = useDict("tz_task_status")

const taskList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const multiple = ref(true)
const total = ref(0)
const title = ref("")

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    taskTitle: undefined,
    status: undefined,
    dueDate: undefined
  },
  rules: {
    recordId: [{ required: true, message: "关联巡田记录不能为空", trigger: "blur" }],
    taskTitle: [{ required: true, message: "任务标题不能为空", trigger: "blur" }]
  }
})

const { queryParams, form, rules } = toRefs(data)

/**
 * 复查结论选项。
 *
 * 刻意用「相对上次诊断的发展趋势」而不是「好 / 不好」二分：
 * 农技员真正要判断的是防治有没有起效，而"无明显变化"和"正在好转"
 * 是两种完全不同的信息，合并成一个"好转"就把判断依据弄丢了。
 */
const REVIEW_RESULTS = [
  { value: 'controlled', label: '已控制住' },
  { value: 'shrinking', label: '正在好转' },
  { value: 'stable', label: '无明显变化' },
  { value: 'worsening', label: '仍在加重' },
  { value: 'unknown', label: '无法判断' }
]

/** 后续安排。选项里带上"下一轮怎么办"，复查才算真的闭环 */
const NEXT_ACTIONS = [
  { value: 'none', label: '无需处理，按常规巡田即可' },
  { value: 'observe', label: '继续观察，暂不处理' },
  { value: 'recheck', label: '仍需再次复查' },
  { value: 'treat', label: '需要再次施药' },
  { value: 'expert', label: '建议请专家现场查看' }
]

const finishOpen = ref(false)
const finishForm = ref({})
const finishRules = {
  reviewResult: [{ required: true, message: '请选择本次复查结论', trigger: 'change' }],
  nextAction: [{ required: true, message: '请选择后续安排', trigger: 'change' }]
}

function reviewLabel(value) {
  const hit = REVIEW_RESULTS.find(item => item.value === value)
  return hit ? hit.label : value
}

/** 查询复查任务列表 */
function getList() {
  loading.value = true
  listFollowup(queryParams.value).then(response => {
    taskList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

/** 取消按钮 */
function cancel() {
  open.value = false
  reset()
}

/** 表单重置 */
function reset() {
  form.value = {
    taskId: undefined,
    recordId: undefined,
    taskTitle: undefined,
    dueDate: undefined,
    status: "0",
    note: undefined
  }
  proxy.resetForm("taskRef")
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.taskId)
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "新增复查任务"
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  const taskId = row.taskId || ids.value
  getFollowup(taskId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改复查任务"
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["taskRef"].validate(valid => {
    if (valid) {
      if (form.value.taskId != undefined) {
        updateFollowup(form.value).then(() => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addFollowup(form.value).then(() => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

/** 打开完成复查对话框；行上的展示字段带进来做上下文提示，提交前会剔除 */
function openFinish(row) {
  finishForm.value = {
    taskId: row.taskId,
    status: '1',
    reviewResult: undefined,
    reviewImage: undefined,
    measureTaken: undefined,
    nextAction: undefined,
    nextDate: undefined,
    note: undefined,
    // 以下三个不是实体字段，仅用于对话框顶部说明这次复查的是哪一条
    taskTitle: row.taskTitle,
    diagnosisName: row.diagnosisName,
    plotName: row.plotName
  }
  finishOpen.value = true
}

/**
 * 提交复查记录。
 *
 * 只传 taskId / status 让后端去回写巡田记录为「已复查」——
 * 闭环规则只应该有一处实现，前端再改一次记录状态就是第二处。
 * 后端更新语句是动态的，没传的字段不会被清空，所以这里可以只带要改的。
 */
function submitFinish() {
  proxy.$refs['finishRef'].validate(valid => {
    if (!valid) return
    const payload = { ...finishForm.value }
    delete payload.taskTitle
    delete payload.diagnosisName
    delete payload.plotName
    changeFollowupStatus(payload).then(() => {
      finishOpen.value = false
      getList()
      proxy.$modal.msgSuccess('已完成复查，结论已记入台账')
    })
  })
}

/** 删除按钮操作 */
function handleDelete(row) {
  const taskIds = row.taskId || ids.value
  proxy.$modal.confirm('是否确认删除复查任务编号为"' + taskIds + '"的数据项？').then(function () {
    return delFollowup(taskIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

getList()
</script>

<style scoped>
/* 复查结论标签。只给「已控制/好转」和「仍在加重」上色，中间态保持安静 ——
   三种状态全都上色就等于都没上色，视线反而抓不住最该注意的那条。 */
.tz-review {
  display: inline-block;
  padding: 1px 9px;
  border-radius: 10px;
  font-size: 12.5px;
  line-height: 19px;
  background: var(--tz-surface-3);
  color: var(--tz-ink-2);
}

.tz-review--controlled,
.tz-review--shrinking {
  background: var(--tz-risk-low-bg);
  color: var(--tz-risk-low);
}

.tz-review--worsening {
  background: var(--tz-risk-high-bg);
  color: var(--tz-risk-high);
}

.tz-dash {
  color: var(--tz-ink-4);
}

.finish-head {
  margin: -4px 0 18px;
  padding-bottom: 13px;
  border-bottom: 1px solid var(--tz-line-soft);
}

.finish-task {
  font-size: 15px;
  font-weight: 600;
  color: var(--tz-ink);
  line-height: 1.5;
}

.finish-meta {
  margin-top: 5px;
  font-size: 12.5px;
  color: var(--tz-ink-3);
}

.finish-dot {
  margin: 0 6px;
  color: var(--tz-ink-4);
}

.finish-hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--tz-ink-4);
}

.finish-photo {
  width: 100%;
}
</style>
