<template>
  <div class="app-container">
    <el-alert
      type="warning"
      :closable="false"
      show-icon
      title="知识库是 AI 诊断与防治建议的唯一依据"
      style="margin-bottom: 12px"
    >
      <template #default>
        系统只会依据本表中的条目给结论，检索不到时宁可回答「没把握」，也不会凭空生成。因此每条记录都必须填「资料来源」，
        且用药说明中的用量要与农药标签一致 —— 建议的可追溯性靠的就是这两列。
      </template>
    </el-alert>

    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="名称" prop="diseaseName">
        <el-input
          v-model="queryParams.diseaseName"
          placeholder="请输入病名 / 虫害名"
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
      <el-form-item label="类别" prop="category">
        <el-select v-model="queryParams.category" placeholder="类别" clearable style="width: 200px">
          <el-option v-for="dict in tz_disease_category" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
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
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['tz:knowledge:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['tz:knowledge:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['tz:knowledge:remove']">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="knowledgeList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="名称" align="center" :show-overflow-tooltip="true">
        <template #default="scope">
          <a class="link-type" style="cursor: pointer" @click="handleView(scope.row)">{{ scope.row.diseaseName }}</a>
        </template>
      </el-table-column>
      <el-table-column label="作物类型" align="center" prop="cropType" width="110">
        <template #default="scope">
          <dict-tag :options="tz_crop_type" :value="scope.row.cropType" />
        </template>
      </el-table-column>
      <el-table-column label="类别" align="center" prop="category" width="110">
        <template #default="scope">
          <dict-tag :options="tz_disease_category" :value="scope.row.category" />
        </template>
      </el-table-column>
      <el-table-column label="典型图片" align="center" width="90">
        <template #default="scope">
          <TzImage
            v-if="scope.row.typicalImage"
            :url="scope.row.typicalImage"
            preview
            style="width: 48px; height: 48px; border-radius: 4px"
          />
          <span v-else class="tz-muted">待补充</span>
        </template>
      </el-table-column>
      <el-table-column label="症状描述" align="center" prop="symptoms" :show-overflow-tooltip="true" />
      <el-table-column label="资料来源" align="center" prop="source" width="180" :show-overflow-tooltip="true" />
      <el-table-column label="状态" align="center" prop="status" width="90">
        <template #default="scope">
          <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="200" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">详情</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['tz:knowledge:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['tz:knowledge:remove']">删除</el-button>
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

    <!-- 添加或修改知识库条目 -->
    <el-dialog :title="title" v-model="open" width="820px" append-to-body>
      <el-form ref="knowledgeRef" :model="form" :rules="rules" label-width="100px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="名称" prop="diseaseName">
              <el-input v-model="form.diseaseName" placeholder="如：柑橘溃疡病" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="类别" prop="category">
              <el-select v-model="form.category" placeholder="请选择类别" style="width: 100%">
                <el-option v-for="dict in tz_disease_category" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
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
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio v-for="dict in sys_normal_disable" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="症状描述" prop="symptoms">
              <el-input
                v-model="form.symptoms"
                type="textarea"
                :rows="4"
                placeholder="按「部位 + 颜色/形态 + 变化过程」写，这段文字直接参与检索打分，写得越具体越容易命中"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="特征性表现" prop="keyFeatures">
              <el-input
                v-model="form.keyFeatures"
                type="textarea"
                :rows="3"
                placeholder="只写本病「看得见、别的病少见」的特征，用分号隔开。如：病斑中央木栓化隆起并呈火山口状开裂；周围有明显黄色晕圈"
              />
              <div class="tz-hint">
                这一栏<b>参与检索且权重最高</b>，是纠偏的关键。硬规矩：
                <b>只写阳性描述（有 / 是 / 呈），不要写「与某某病的区别」</b> ——
                一旦带上别的病名，查那个病的词反而会命中这一条。
                要写鉴别，写到下一栏去。
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="鉴别要点" prop="differential">
              <el-input
                v-model="form.differential"
                type="textarea"
                :rows="4"
                placeholder="与最易混淆的病怎么区分，逐条写清「看哪里、是什么样」。如：与疮痂病区分——疮痂病病斑仅 1~2 毫米、只在叶背呈圆锥形突起、叶正面凹陷如漏斗"
              />
              <div class="tz-hint">
                这一栏**不参与检索**，只在诊断时交给 AI 逐条比对，帮它排掉看着像的病。
                所以这里出现别的病名是对的、也是必要的。
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="典型图片" prop="typicalImage">
              <image-upload v-model="form.typicalImage" :limit="1" :file-size="5" />
              <div class="tz-hint">
                该病害的典型症状照片，供农户对照。**请用实拍照片**，
                示意图（如演示数据里那三张）只适合临时占位，不能作为对外培训材料。
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="发生条件" prop="triggerConditions">
              <el-input v-model="form.triggerConditions" type="textarea" :rows="3" placeholder="气候、栽培管理、天敌等诱发因素" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="防治措施" prop="prevention">
              <el-input v-model="form.prevention" type="textarea" :rows="4" placeholder="农业防治 / 生物防治 / 化学防治，能不给具体用量就不给" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="用药说明" prop="medicineNote">
              <el-input
                v-model="form.medicineNote"
                type="textarea"
                :rows="4"
                placeholder="只写标签上确有的药剂与用量。AI 生成的建议里凡出现用量，都会被拿去和这一栏比对，对不上的一律拦截并替换"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="安全说明" prop="safetyNote">
              <el-input v-model="form.safetyNote" type="textarea" :rows="3" placeholder="安全间隔期、施药防护、抗性管理、天敌保护等" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="资料来源" prop="source">
              <el-input v-model="form.source" placeholder="如：《中国柑橘病虫害防治图鉴》/ 省植保站某年病虫情报 —— 建议可溯的唯一凭据，必填" />
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

    <!-- 详情抽屉：只读展示，方便对照核查内容 -->
    <el-drawer v-model="viewOpen" :title="viewRow.diseaseName || '知识库详情'" size="45%">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="作物类型">{{ viewRow.cropType }}</el-descriptions-item>
        <el-descriptions-item label="类别">
          <dict-tag :options="tz_disease_category" :value="viewRow.category" />
        </el-descriptions-item>
        <el-descriptions-item label="症状描述">
          <pre class="tz-pre">{{ viewRow.symptoms }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="特征性表现">
          <span class="tz-tag-hint">参与检索·权重最高</span>
          <pre class="tz-pre tz-key-features">{{ viewRow.keyFeatures || '（未填写）' }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="鉴别要点">
          <span class="tz-tag-hint">仅供 AI 比对，不参与检索</span>
          <pre class="tz-pre">{{ viewRow.differential || '（未填写）' }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="典型图片">
          <TzImage
            v-if="viewRow.typicalImage"
            :url="viewRow.typicalImage"
            preview
            style="max-width: 100%; max-height: 260px; border-radius: 4px"
          />
          <span v-else class="tz-muted">暂无图片（正式使用前请补实拍照片）</span>
        </el-descriptions-item>
        <el-descriptions-item label="发生条件">
          <pre class="tz-pre">{{ viewRow.triggerConditions }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="防治措施">
          <pre class="tz-pre">{{ viewRow.prevention }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="用药说明">
          <pre class="tz-pre">{{ viewRow.medicineNote }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="安全说明">
          <pre class="tz-pre">{{ viewRow.safetyNote }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="资料来源">
          <span class="tz-source">{{ viewRow.source }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup name="TzKnowledge">
import { listKnowledge, getKnowledge, delKnowledge, addKnowledge, updateKnowledge } from "@/api/tianzhen/knowledge"
import { resolveImageUrl as tzResolveImageUrl } from "@/utils/tzImage"
import TzImage from '@/components/TzImage/index.vue'

const { proxy } = getCurrentInstance()
const { tz_crop_type, tz_disease_category } = useDict("tz_crop_type", "tz_disease_category")
const { sys_normal_disable } = useDict("sys_normal_disable")

const knowledgeList = ref([])
const open = ref(false)
const viewOpen = ref(false)
const viewRow = ref({})
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
    diseaseName: undefined,
    cropType: undefined,
    category: undefined,
    status: undefined
  },
  rules: {
    diseaseName: [{ required: true, message: "名称不能为空", trigger: "blur" }],
    source: [{ required: true, message: "资料来源不能为空：没有来源的建议等于没有建议", trigger: "blur" }]
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询知识库列表 */
function getList() {
  loading.value = true
  listKnowledge(queryParams.value).then(response => {
    knowledgeList.value = response.rows
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
    knowledgeId: undefined,
    cropType: "柑橘",
    diseaseName: undefined,
    category: "1",
    symptoms: undefined,
    keyFeatures: undefined,
    differential: undefined,
    typicalImage: undefined,
    triggerConditions: undefined,
    prevention: undefined,
    medicineNote: undefined,
    safetyNote: undefined,
    source: undefined,
    status: "0",
    remark: undefined
  }
  proxy.resetForm("knowledgeRef")
}

/**
 * 图片地址归一化。实现搬到 @/utils/tzImage —— 这里原先保留绝对地址，
 * 而绝对地址里的 host 只在本机成立，公网访问时图片会全裂。详见该文件注释。
 */
const resolveImageUrl = tzResolveImageUrl

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
  ids.value = selection.map(item => item.knowledgeId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "新增知识库条目"
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  const knowledgeId = row.knowledgeId || ids.value
  getKnowledge(knowledgeId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改知识库条目"
  })
}

/** 查看详情 */
function handleView(row) {
  viewRow.value = row
  viewOpen.value = true
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["knowledgeRef"].validate(valid => {
    if (valid) {
      if (form.value.knowledgeId != undefined) {
        updateKnowledge(form.value).then(() => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addKnowledge(form.value).then(() => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

/** 删除按钮操作 */
function handleDelete(row) {
  const knowledgeIds = row.knowledgeId || ids.value
  proxy.$modal.confirm('是否确认删除知识库条目编号为"' + knowledgeIds + '"的数据项？').then(function () {
    return delKnowledge(knowledgeIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

getList()
</script>

<style scoped>
.tz-pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: inherit;
}
.tz-source {
  word-break: break-all;
}
.tz-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
  margin-top: 4px;
}
.tz-muted {
  color: var(--el-text-color-secondary);
}
/* 详情里给两个易混字段贴个用途标签，免得维护的人随手把鉴别要点写进特征性表现 */
.tz-tag-hint {
  display: inline-block;
  font-size: 12px;
  line-height: 1;
  padding: 3px 6px;
  margin-bottom: 6px;
  border-radius: 3px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-7);
}
/* 特征性表现是纠偏的关键字段，给它一点视觉重量，别和普通正文混在一起 */
.tz-key-features {
  font-weight: 600;
  color: var(--el-color-primary);
}
</style>
