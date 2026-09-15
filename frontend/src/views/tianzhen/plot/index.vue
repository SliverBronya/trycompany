<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="地块名称" prop="plotName">
        <el-input
          v-model="queryParams.plotName"
          placeholder="请输入地块名称"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="作物类型" prop="cropType">
        <el-select v-model="queryParams.cropType" placeholder="作物类型" clearable style="width: 200px">
          <el-option v-for="dict in tz_crop_type" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="责任人" prop="ownerName">
        <el-input
          v-model="queryParams.ownerName"
          placeholder="请输入责任人"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="状态" clearable style="width: 200px">
          <el-option v-for="dict in sys_normal_disable" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['tz:plot:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['tz:plot:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['tz:plot:remove']">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="plotList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="地块名称" align="center" prop="plotName" :show-overflow-tooltip="true" />
      <el-table-column label="作物类型" align="center" prop="cropType" width="110">
        <template #default="scope">
          <dict-tag :options="tz_crop_type" :value="scope.row.cropType" />
        </template>
      </el-table-column>
      <el-table-column label="面积(亩)" align="center" prop="area" width="100" />
      <el-table-column label="地块位置" align="center" prop="location" :show-overflow-tooltip="true" />
      <el-table-column label="责任人" align="center" prop="ownerName" width="100" />
      <el-table-column label="种植年份" align="center" prop="plantYear" width="100" />
      <el-table-column label="状态" align="center" prop="status" width="90">
        <template #default="scope">
          <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="100">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['tz:plot:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['tz:plot:remove']">删除</el-button>
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

    <!-- 添加或修改地块对话框 -->
    <el-dialog :title="title" v-model="open" width="680px" append-to-body>
      <el-form ref="plotRef" :model="form" :rules="rules" label-width="90px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="地块名称" prop="plotName">
              <el-input v-model="form.plotName" placeholder="如：东坡 3 号园" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="作物类型" prop="cropType">
              <el-select v-model="form.cropType" placeholder="请选择作物类型" style="width: 100%">
                <el-option v-for="dict in tz_crop_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="面积(亩)" prop="area">
              <el-input-number v-model="form.area" :min="0" :precision="2" :step="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="种植年份" prop="plantYear">
              <el-input-number v-model="form.plantYear" :min="1900" :max="2100" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="责任人" prop="ownerName">
              <el-input v-model="form.ownerName" placeholder="请输入责任人" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio v-for="dict in sys_normal_disable" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="地块位置" prop="location">
              <el-input v-model="form.location" placeholder="请输入地块位置" />
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
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="TzPlot">
import { listPlot, getPlot, delPlot, addPlot, updatePlot } from "@/api/tianzhen/plot"

const { proxy } = getCurrentInstance()
const { tz_crop_type } = useDict("tz_crop_type")

// 地块启用状态沿用若依通用的 sys_normal_disable（0=正常 1=停用）。
// 不复刻一份 tz_plot_status：同一个语义两处维护，迟早有一处忘了改。
const { sys_normal_disable } = useDict("sys_normal_disable")

const plotList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    plotName: undefined,
    cropType: undefined,
    ownerName: undefined,
    status: undefined
  },
  rules: {
    plotName: [{ required: true, message: "地块名称不能为空", trigger: "blur" }],
    cropType: [{ required: true, message: "作物类型不能为空", trigger: "change" }]
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询地块列表 */
function getList() {
  loading.value = true
  listPlot(queryParams.value).then(response => {
    plotList.value = response.rows
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
    plotId: undefined,
    plotName: undefined,
    cropType: undefined,
    area: undefined,
    location: undefined,
    ownerName: undefined,
    plantYear: undefined,
    status: "0",
    remark: undefined
  }
  proxy.resetForm("plotRef")
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
  ids.value = selection.map(item => item.plotId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "添加地块"
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  const plotId = row.plotId || ids.value
  getPlot(plotId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改地块"
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["plotRef"].validate(valid => {
    if (valid) {
      if (form.value.plotId != undefined) {
        updatePlot(form.value).then(() => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addPlot(form.value).then(() => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

/**
 * 删除按钮操作。
 *
 * 地块下还挂着巡田记录时后端会拒绝删除并给出原因，这里不做前端预判：
 * 预判要么多查一次接口，要么就得在前端复制一份「什么算被引用」的规则，
 * 后端才是唯一说了算的地方。
 */
function handleDelete(row) {
  const plotIds = row.plotId || ids.value
  proxy.$modal.confirm('是否确认删除地块编号为"' + plotIds + '"的数据项？').then(function () {
    return delPlot(plotIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

getList()
</script>
