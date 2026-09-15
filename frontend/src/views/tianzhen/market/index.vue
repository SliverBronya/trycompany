<template>
  <div class="app-container">
    <div class="tz-page">
      <el-alert type="info" :closable="false" show-icon class="tz-tip" title="行情仅供参考，本系统不参与交易">
        <template #default>
          所有价格均来自人工录入并标注来源，不做自动抓取、不构成报价。实际成交价请以当地收购商当面议定为准。
        </template>
      </el-alert>

      <el-card shadow="never" class="tz-filter">
        <div class="tz-filter-row">
          <el-select v-model="queryParams.variety" placeholder="细分品种" clearable style="width: 130px">
            <el-option v-for="item in varietyOptions" :key="item" :label="item" :value="item" />
          </el-select>
          <el-select v-model="queryParams.priceType" placeholder="价格类型" clearable style="width: 120px">
            <el-option v-for="dict in tz_price_type" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
          <el-input v-model="queryParams.region" placeholder="产区 / 市场" clearable style="flex: 1; min-width: 120px" @keyup.enter="handleQuery" />
          <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
          <el-button icon="Plus" @click="handleAdd" v-hasPermi="['tz:market:add']">录入</el-button>
        </div>
      </el-card>

      <el-card shadow="never" v-loading="loading">
        <el-empty v-if="!marketList.length && !loading" description="暂无行情数据" />
        <div v-for="row in marketList" :key="row.priceId" class="tz-item">
          <div class="tz-item-main">
            <div class="tz-item-title">
              <span class="tz-name">{{ row.cropType }}{{ row.variety ? ' · ' + row.variety : '' }}</span>
              <dict-tag :options="tz_price_type" :value="row.priceType" />
            </div>
            <div class="tz-item-sub">
              <span>{{ row.region || '未标注产区' }}</span>
              <span class="tz-dot">·</span>
              <span>{{ parseTime(row.priceDate, '{y}-{m}-{d}') }}</span>
            </div>
            <div class="tz-src">来源：{{ row.source }}</div>
          </div>
          <div class="tz-item-price">
            <div class="tz-price">{{ row.price }}<span class="tz-unit">{{ row.priceUnit || '元/斤' }}</span></div>
            <!-- 国内行情习惯：红涨绿跌，和欧美相反，这里跟着本地习惯走 -->
            <div v-if="row.changeRate != null" :class="['tz-change', Number(row.changeRate) >= 0 ? 'tz-up' : 'tz-down']">
              {{ Number(row.changeRate) >= 0 ? '▲' : '▼' }} {{ Math.abs(Number(row.changeRate)) }}%
            </div>
          </div>
          <div class="tz-item-ops">
            <el-button link type="primary" icon="Edit" @click="handleUpdate(row)" v-hasPermi="['tz:market:edit']">改</el-button>
            <el-button link type="danger" icon="Delete" @click="handleDelete(row)" v-hasPermi="['tz:market:remove']">删</el-button>
          </div>
        </div>

        <pagination
          v-show="total > 0"
          :total="total"
          v-model:page="queryParams.pageNum"
          v-model:limit="queryParams.pageSize"
          @pagination="getList"
        />
      </el-card>
    </div>

    <el-dialog :title="title" v-model="open" width="560px" append-to-body>
      <el-form ref="marketRef" :model="form" :rules="rules" label-width="90px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="品种" prop="cropType">
              <el-select v-model="form.cropType" style="width: 100%">
                <el-option v-for="dict in tz_crop_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="细分品种" prop="variety">
              <el-input v-model="form.variety" placeholder="如：沃柑 / 砂糖橘" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="价格类型" prop="priceType">
              <el-select v-model="form.priceType" style="width: 100%">
                <el-option v-for="dict in tz_price_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="产区/市场" prop="region">
              <el-input v-model="form.region" placeholder="如：广西桂林" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="价格" prop="price">
              <el-input-number v-model="form.price" :min="0" :precision="2" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计价单位" prop="priceUnit">
              <el-input v-model="form.priceUnit" placeholder="元/斤" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="涨跌幅(%)" prop="changeRate">
              <el-input-number v-model="form.changeRate" :precision="2" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="行情日期" prop="priceDate">
              <el-date-picker v-model="form.priceDate" value-format="YYYY-MM-DD" type="date" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="数据来源" prop="source">
              <el-input v-model="form.source" placeholder="如：某地农产品批发市场某年某月某日价格监测表" />
              <div class="tz-hint">
                必填。行情是给农户做参考的，说不清出处的数字比没有数字更糟 —— 所以这一栏不允许留空。
              </div>
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

<script setup name="TzMarket">
import { listMarket, getMarket, delMarket, addMarket, updateMarket } from '@/api/tianzhen/market'

const { proxy } = getCurrentInstance()
const { tz_crop_type, tz_price_type } = useDict("tz_crop_type", "tz_price_type")

const marketList = ref([])
const open = ref(false)
const loading = ref(true)
const total = ref(0)
const title = ref("")

// 细分品种没有对应字典（它是产区自定的），从已有数据里现取一份候选
const varietyOptions = ref([])

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    variety: undefined,
    priceType: undefined,
    region: undefined
  },
  rules: {
    cropType: [{ required: true, message: "品种不能为空", trigger: "change" }],
    price: [{ required: true, message: "价格不能为空", trigger: "blur" }],
    source: [{ required: true, message: "数据来源不能为空：说不清出处的价格不如不给", trigger: "blur" }]
  }
})

const { queryParams, form, rules } = toRefs(data)

function getList() {
  loading.value = true
  listMarket(queryParams.value).then(response => {
    marketList.value = response.rows || []
    total.value = response.total
    loading.value = false
    collectVarieties()
  }).catch(() => {
    loading.value = false
  })
}

function collectVarieties() {
  const names = new Set(varietyOptions.value)
  marketList.value.forEach(row => {
    if (row.variety) {
      names.add(row.variety)
    }
  })
  varietyOptions.value = Array.from(names)
}

function cancel() {
  open.value = false
  reset()
}

function reset() {
  form.value = {
    priceId: undefined,
    cropType: "柑橘",
    variety: undefined,
    priceType: "1",
    region: undefined,
    price: undefined,
    priceUnit: "元/斤",
    changeRate: undefined,
    priceDate: undefined,
    source: undefined
  }
  proxy.resetForm("marketRef")
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function handleAdd() {
  reset()
  open.value = true
  title.value = "录入行情"
}

function handleUpdate(row) {
  reset()
  getMarket(row.priceId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改行情"
  })
}

function submitForm() {
  proxy.$refs["marketRef"].validate(valid => {
    if (valid) {
      const request = form.value.priceId != undefined ? updateMarket(form.value) : addMarket(form.value)
      request.then(() => {
        proxy.$modal.msgSuccess(form.value.priceId != undefined ? "修改成功" : "录入成功")
        open.value = false
        getList()
      })
    }
  })
}

function handleDelete(row) {
  proxy.$modal.confirm('是否确认删除该条行情数据？').then(function () {
    return delMarket(row.priceId)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

getList()
</script>

<style scoped>
.tz-page {
  max-width: 720px;
  margin: 0 auto;
}
.tz-tip {
  margin-bottom: 12px;
}
.tz-filter {
  margin-bottom: 12px;
}
.tz-filter-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.tz-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.tz-item:last-child {
  border-bottom: none;
}
.tz-item-main {
  flex: 1;
  min-width: 0;
}
.tz-item-title {
  display: flex;
  align-items: center;
  gap: 8px;
}
.tz-name {
  font-weight: 600;
  font-size: 15px;
}
.tz-item-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.tz-dot {
  margin: 0 6px;
}
.tz-src {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  word-break: break-all;
}
.tz-item-price {
  text-align: right;
  min-width: 92px;
}
.tz-price {
  font-size: 20px;
  font-weight: 700;
  color: var(--el-color-danger);
}
.tz-unit {
  font-size: 12px;
  font-weight: 400;
  margin-left: 2px;
  color: var(--el-text-color-secondary);
}
.tz-change {
  font-size: 12px;
}
.tz-up {
  color: var(--el-color-danger);
}
.tz-down {
  color: var(--el-color-success);
}
.tz-item-ops {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.tz-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
  margin-top: 4px;
}
</style>
