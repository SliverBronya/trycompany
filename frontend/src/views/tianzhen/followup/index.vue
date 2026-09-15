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
            @click="handleFinish(scope.row)"
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

/**
 * 完成复查。
 *
 * 只传 taskId 和新状态，让后端去回写巡田记录为「已复查」——
 * 闭环规则只应该有一处实现，前端再改一次记录状态就是第二处。
 * 后端更新语句是动态的，少传的字段不会被清空，所以这里可以只带这两个字段。
 */
function handleFinish(row) {
  proxy.$modal.confirm('确认已完成「' + row.taskTitle + '」的复查？完成后关联巡田记录会标记为已复查。').then(function () {
    return changeFollowupStatus({ taskId: row.taskId, status: "1" })
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("已完成复查")
  }).catch(() => {})
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
