<template>
  <div class="app-container">
    <div class="tz-page">
      <el-alert type="info" :closable="false" show-icon class="tz-tip" title="本系统只做线索撮合，不参与交易">
        <template #default>
          供求双方在线下自行核实并成交。平台不托管货款、不做担保，请务必当面验货、当面议价。
          联系方式仅对已登录用户可见。
        </template>
      </el-alert>

      <el-card shadow="never" class="tz-filter">
        <div class="tz-filter-row">
          <el-radio-group v-model="queryParams.infoType" @change="handleQuery">
            <el-radio-button :value="undefined">全部</el-radio-button>
            <el-radio-button v-for="dict in tz_info_type" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio-button>
          </el-radio-group>
          <el-select v-model="queryParams.status" placeholder="状态" clearable style="width: 110px" @change="handleQuery">
            <el-option v-for="dict in tz_supply_status" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
          <el-input v-model="queryParams.region" placeholder="产区 / 所在地" clearable style="flex: 1; min-width: 110px" @keyup.enter="handleQuery" />
          <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
          <el-button icon="Plus" @click="handleAdd" v-hasPermi="['tz:supply:add']">发布</el-button>
        </div>
      </el-card>

      <el-card shadow="never" v-loading="loading">
        <el-empty v-if="!supplyList.length && !loading" description="暂无供求信息" />
        <div v-for="row in supplyList" :key="row.infoId" class="tz-item">
          <div class="tz-item-head">
            <dict-tag :options="tz_info_type" :value="row.infoType" />
            <span class="tz-name">{{ row.cropType }}{{ row.variety ? ' · ' + row.variety : '' }}</span>
            <dict-tag :options="tz_supply_status" :value="row.status" />
          </div>
          <div class="tz-item-qty">
            <span v-if="row.quantity">{{ row.quantity }} 吨</span>
            <span v-else>数量待议</span>
            <span class="tz-dot">·</span>
            <span>{{ row.priceExpect || '价格面议' }}</span>
            <span class="tz-dot">·</span>
            <span>{{ row.region || '所在地未标注' }}</span>
          </div>
          <div v-if="row.description" class="tz-desc">{{ row.description }}</div>
          <div class="tz-contact">
            <span>{{ row.contactName || '联系人未填' }}</span>
            <span class="tz-phone">{{ row.contactPhone || '未留电话' }}</span>
            <span class="tz-time">{{ parseTime(row.createTime, '{y}-{m}-{d}') }}</span>
          </div>
          <div class="tz-item-ops">
            <el-button
              v-if="row.status === '0'"
              link
              type="primary"
              icon="Check"
              @click="handleChangeStatus(row, '2', '成交')"
              v-hasPermi="['tz:supply:edit']"
            >标记成交</el-button>
            <el-button
              v-if="row.status === '0'"
              link
              type="info"
              icon="Bottom"
              @click="handleChangeStatus(row, '1', '下架')"
              v-hasPermi="['tz:supply:edit']"
            >下架</el-button>
            <el-button
              v-if="row.status !== '0'"
              link
              type="primary"
              icon="Top"
              @click="handleChangeStatus(row, '0', '重新发布')"
              v-hasPermi="['tz:supply:edit']"
            >重新发布</el-button>
            <el-button link type="primary" icon="Edit" @click="handleUpdate(row)" v-hasPermi="['tz:supply:edit']">修改</el-button>
            <el-button link type="danger" icon="Delete" @click="handleDelete(row)" v-hasPermi="['tz:supply:remove']">删除</el-button>
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
      <el-form ref="supplyRef" :model="form" :rules="rules" label-width="90px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="信息类型" prop="infoType">
              <el-radio-group v-model="form.infoType">
                <el-radio v-for="dict in tz_info_type" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="品种" prop="cropType">
              <el-select v-model="form.cropType" style="width: 100%">
                <el-option v-for="dict in tz_crop_type" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="细分品种" prop="variety">
              <el-input v-model="form.variety" placeholder="如：沃柑" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="数量(吨)" prop="quantity">
              <el-input-number v-model="form.quantity" :min="0" :precision="2" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="期望价格" prop="priceExpect">
              <el-input v-model="form.priceExpect" placeholder="面议 / 3 元/斤以上" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所在地" prop="region">
              <el-input v-model="form.region" placeholder="如：广西桂林" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系人" prop="contactName">
              <el-input v-model="form.contactName" placeholder="请输入联系人" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话" prop="contactPhone">
              <el-input v-model="form.contactPhone" placeholder="请输入联系电话" maxlength="20" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="详细描述" prop="description">
              <el-input v-model="form.description" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="果径、糖度、采摘时间、包装方式、可否看货等" />
            </el-form-item>
          </el-col>
          <el-col :span="24" v-if="form.infoId != undefined">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" style="width: 100%">
                <el-option v-for="dict in tz_supply_status" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
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

<script setup name="TzSupply">
import { listSupply, getSupply, delSupply, addSupply, updateSupply, changeSupplyStatus } from '@/api/tianzhen/supply'

const { proxy } = getCurrentInstance()
const { tz_crop_type, tz_info_type, tz_supply_status } = useDict("tz_crop_type", "tz_info_type", "tz_supply_status")

const supplyList = ref([])
const open = ref(false)
const loading = ref(true)
const total = ref(0)
const title = ref("")

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    infoType: undefined,
    status: "0",
    region: undefined
  },
  rules: {
    infoType: [{ required: true, message: "请选择信息类型", trigger: "change" }],
    cropType: [{ required: true, message: "品种不能为空", trigger: "change" }],
    contactName: [{ required: true, message: "联系人不能为空", trigger: "blur" }],
    contactPhone: [
      { required: true, message: "联系电话不能为空", trigger: "blur" },
      { pattern: /^[0-9\-+() ]{5,20}$/, message: "请填写有效的联系电话", trigger: "blur" }
    ]
  }
})

const { queryParams, form, rules } = toRefs(data)

function getList() {
  loading.value = true
  listSupply(queryParams.value).then(response => {
    supplyList.value = response.rows || []
    total.value = response.total
    loading.value = false
  }).catch(() => {
    loading.value = false
  })
}

function cancel() {
  open.value = false
  reset()
}

function reset() {
  form.value = {
    infoId: undefined,
    infoType: "1",
    cropType: "柑橘",
    variety: undefined,
    quantity: undefined,
    priceExpect: undefined,
    region: undefined,
    contactName: undefined,
    contactPhone: undefined,
    description: undefined,
    status: "0"
  }
  proxy.resetForm("supplyRef")
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function handleAdd() {
  reset()
  open.value = true
  title.value = "发布供求信息"
}

function handleUpdate(row) {
  reset()
  getSupply(row.infoId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改供求信息"
  })
}

function submitForm() {
  proxy.$refs["supplyRef"].validate(valid => {
    if (valid) {
      const request = form.value.infoId != undefined ? updateSupply(form.value) : addSupply(form.value)
      request.then(() => {
        proxy.$modal.msgSuccess(form.value.infoId != undefined ? "修改成功" : "发布成功")
        open.value = false
        getList()
      })
    }
  })
}

/**
 * 改状态。
 *
 * 走专门的 status 接口，而不是 updateSupply：后者带完整校验，只传 infoId + status 会被
 * 「品种、联系人、电话不能为空」挡回来。也不必先把记录读回来再整体写回 —— 那样容易把
 * 并发修改覆盖掉。后端只更新动态拼进 SQL 的字段，少传的内容不会被清空。
 */
function handleChangeStatus(row, status, action) {
  proxy.$modal.confirm('确认将这条' + (row.infoType === '1' ? '供应' : '求购') + '信息标记为「' + action + '」？').then(function () {
    return changeSupplyStatus({ infoId: row.infoId, status })
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('已' + action)
  }).catch(() => {})
}

function handleDelete(row) {
  proxy.$modal.confirm('是否确认删除该条供求信息？').then(function () {
    return delSupply(row.infoId)
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
  align-items: center;
}
.tz-item {
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.tz-item:last-child {
  border-bottom: none;
}
.tz-item-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.tz-name {
  font-weight: 600;
  font-size: 15px;
}
.tz-item-qty {
  margin-top: 6px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.tz-dot {
  margin: 0 6px;
  color: var(--el-text-color-placeholder);
}
.tz-desc {
  margin-top: 6px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.6;
  word-break: break-word;
}
.tz-contact {
  margin-top: 6px;
  font-size: 13px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
}
.tz-phone {
  color: var(--el-color-primary);
  font-weight: 600;
}
.tz-time {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.tz-item-ops {
  margin-top: 8px;
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
</style>
