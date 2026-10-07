<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

// ------------------------------------------------------------------
// 采蜜批次 · 蜂蜜批次质检放行
// 状态机：待质检 --初检合格--> 已放行 --> 已入库
//         待质检 --初检不合格--> 隔离待整改 --整改登记 + 复检合格--> 已放行
//         隔离待整改 --复检仍不合格--> 隔离拒收（终态，拒绝入库）
// 动作是否可执行、不可执行原因均由后端按批次当前状态计算，前端只负责展示。
// ------------------------------------------------------------------

const list = ref([])
const hives = ref([])
const loading = ref(false)

const hiveMap = computed(() => {
  const m = {}
  hives.value.forEach(h => { m[h.id] = h })
  return m
})
const hiveCode = (id) => (hiveMap.value[id] ? hiveMap.value[id].code : ('#' + id))

const BATCH_STATUSES = ['待质检', '已放行', '隔离待整改', '隔离拒收', '已入库', '已作废']
const QC_CONCLUSIONS = ['未质检', '初检合格', '初检不合格', '复检合格', '复检不合格']

const filter = reactive({ status: '', qcConclusion: '' })
const emit = defineEmits(['changed'])

// 行级动作锁：防止重复点击，key = 批次id:动作
const busy = reactive({})
function isBusy(id, act) { return !!busy[id + ':' + act] }
async function runAction(id, act, fn) {
  const k = id + ':' + act
  if (busy[k]) return
  busy[k] = true
  try {
    await fn()
  } finally {
    busy[k] = false
  }
}

async function loadList() {
  loading.value = true
  try {
    list.value = await api.listHoneyBatches({
      status: filter.status, qcConclusion: filter.qcConclusion
    })
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function loadHives() {
  hives.value = await api.listHives()
}

watch(() => [filter.status, filter.qcConclusion], loadList)

// ---------------- 标签样式 ----------------
function statusTagType(s) {
  return s === '已入库' ? 'success'
    : s === '已放行' ? 'primary'
      : s === '待质检' ? 'warning'
        : (s === '隔离待整改' || s === '隔离拒收') ? 'danger' : 'info'
}
function statusTagEffect(s) {
  return s === '隔离拒收' ? 'dark' : 'light'
}
// 注意「不合格」也包含「合格」二字，必须先判不合格
const conclusionType = (c) =>
  !c || c === '未质检' ? 'info' : c.includes('不合格') ? 'danger' : 'success'

function fmtTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 16)
}
/** 放行判定（与后端一致）：含水率 ≤ 20% 且感官合格 */
function Dict_isPass(q) {
  return Number(q.moisture) <= 20 && q.sensory === '合格'
}
function nowIso() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

// ---------------- 新增 / 编辑批次 ----------------
const batchDialog = ref(false)
const editing = ref(false)
const batchForm = reactive({ id: null, hiveId: null, batchNo: '', weightKg: 0 })

function canEdit(row) {
  return row.status === '待质检' && row.qcCount === 0
}
function editReason(row) {
  return canEdit(row) ? '' : '批次已进入质检流转或已有质检记录，基础信息不可修改'
}

function openCreate() {
  editing.value = false
  batchForm.id = null
  batchForm.hiveId = hives.value.length ? hives.value[0].id : null
  batchForm.batchNo = ''
  batchForm.weightKg = 0
  batchDialog.value = true
}
function openEdit(row) {
  if (!canEdit(row)) return
  editing.value = true
  batchForm.id = row.id
  batchForm.hiveId = row.hiveId
  batchForm.batchNo = row.batchNo
  batchForm.weightKg = row.weightKg
  batchDialog.value = true
}

const batchSubmitting = ref(false)
async function submitBatch() {
  if (!batchForm.batchNo || !batchForm.batchNo.trim()) {
    ElMessage.error('批次号不能为空')
    return
  }
  if (!batchForm.weightKg || Number(batchForm.weightKg) <= 0) {
    ElMessage.error('重量必须大于 0')
    return
  }
  batchSubmitting.value = true
  try {
    const payload = {
      hiveId: batchForm.hiveId,
      batchNo: batchForm.batchNo.trim(),
      weightKg: Number(batchForm.weightKg)
    }
    if (editing.value) {
      await api.updateHoneyBatch(batchForm.id, payload)
      ElMessage.success('已保存')
    } else {
      await api.createHoneyBatch(payload)
      ElMessage.success('已新增')
    }
    batchDialog.value = false
    await loadList()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    batchSubmitting.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `批次 ${row.batchNo} 删除后不可恢复，确认删除？`,
      '删除确认', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch { return }
  await runAction(row.id, 'delete', async () => {
    try {
      await api.deleteHoneyBatch(row.id)
      ElMessage.success('已删除')
      await loadList()
    } catch (e) {
      ElMessage.error(e.message)
    }
  })
}

// ---------------- 入库 ----------------
async function stockIn(row) {
  await runAction(row.id, 'stockIn', async () => {
    try {
      await ElMessageBox.confirm(
        `批次 ${row.batchNo} 质检已放行（${row.activeQc ? row.activeQc.sampleNo : ''}），确认办理入库？`,
        '入库确认', { type: 'warning', confirmButtonText: '确认入库', cancelButtonText: '取消' }
      )
    } catch { return }
    try {
      await api.stockInHoneyBatch(row.id)
      ElMessage.success('已入库')
      await loadList()
      await refreshTraceIfOpen(row.id)
    } catch (e) {
      ElMessage.error(e.message)
      await loadList()
    }
  })
}

// ---------------- 质检登记（初检 / 复检） ----------------
const qcDialog = ref(false)
const qcMode = ref('初检')
const qcBatch = ref(null)
const qcSubmitting = ref(false)
const qcForm = reactive({
  sampleNo: '', moisture: 19, sensory: '合格', inspector: '', inspectTime: nowIso(), note: ''
})

function openQc(row, mode) {
  qcBatch.value = row
  qcMode.value = mode
  qcForm.sampleNo = ''
  qcForm.moisture = 19
  qcForm.sensory = '合格'
  qcForm.inspector = ''
  qcForm.inspectTime = nowIso()
  qcForm.note = ''
  qcDialog.value = true
}

const moistureFail = computed(() => Number(qcForm.moisture) > 20)
const sensoryFail = computed(() => qcForm.sensory === '不合格')
const qcPass = computed(() => !moistureFail.value && !sensoryFail.value)

async function submitQc() {
  if (!qcForm.sampleNo.trim()) { ElMessage.error('抽样编号不能为空'); return }
  if (qcForm.moisture === null || qcForm.moisture === '' || Number(qcForm.moisture) < 0) {
    ElMessage.error('含水率不能为空'); return
  }
  if (Number(qcForm.moisture) > 100) { ElMessage.error('含水率取值应为 0~100 之间的百分数'); return }
  if (!qcForm.inspector.trim()) { ElMessage.error('请填写检测人'); return }
  if (!qcForm.inspectTime) { ElMessage.error('请填写检测时间'); return }
  if (!qcForm.note.trim()) { ElMessage.error('请填写检测说明（含处理意见）'); return }

  qcSubmitting.value = true
  try {
    const payload = {
      sampleNo: qcForm.sampleNo.trim(),
      moisture: Number(qcForm.moisture),
      sensory: qcForm.sensory,
      inspector: qcForm.inspector.trim(),
      inspectTime: qcForm.inspectTime,
      note: qcForm.note.trim()
    }
    if (qcMode.value === '初检') {
      await api.submitHoneyQc(qcBatch.value.id, payload)
      ElMessage.success(qcPass.value ? '初检合格，批次已放行' : '初检不合格，批次已隔离待整改')
    } else {
      await api.submitReinspection(qcBatch.value.id, payload)
      ElMessage.success(qcPass.value ? '复检合格，批次已放行' : '复检仍不合格，批次保持隔离并拒绝入库')
    }
    qcDialog.value = false
    await loadList()
    await refreshTraceIfOpen(qcBatch.value.id)
  } catch (e) {
    ElMessage.error(e.message)
    await loadList()
  } finally {
    qcSubmitting.value = false
  }
}

// ---------------- 整改说明 ----------------
const rectDialog = ref(false)
const rectBatch = ref(null)
const rectSubmitting = ref(false)
const rectForm = reactive({ content: '', handler: '' })

function openRect(row) {
  rectBatch.value = row
  rectForm.content = ''
  rectForm.handler = ''
  rectDialog.value = true
}

async function submitRect() {
  if (!rectForm.content.trim()) { ElMessage.error('请补充整改说明'); return }
  if (!rectForm.handler.trim()) { ElMessage.error('请填写整改登记人'); return }
  rectSubmitting.value = true
  try {
    await api.submitRectification(rectBatch.value.id, {
      content: rectForm.content.trim(),
      handler: rectForm.handler.trim()
    })
    ElMessage.success('整改说明已登记，可以发起复检')
    rectDialog.value = false
    await loadList()
    await refreshTraceIfOpen(rectBatch.value.id)
  } catch (e) {
    ElMessage.error(e.message)
    await loadList()
  } finally {
    rectSubmitting.value = false
  }
}

// ---------------- 质检回溯 ----------------
const traceDialog = ref(false)
const traceRowId = ref(null)
const detail = ref(null)

async function openTrace(row) {
  traceRowId.value = row.id
  traceDialog.value = true
  await loadDetail()
}
async function loadDetail() {
  if (traceRowId.value == null) return
  try {
    detail.value = await api.getHoneyBatch(traceRowId.value)
  } catch (e) {
    ElMessage.error(e.message)
  }
}
async function refreshTraceIfOpen(id) {
  if (traceDialog.value && traceRowId.value === id) {
    await loadDetail()
  }
}

onMounted(async () => {
  await loadHives()
  await loadList()
})
</script>

<template>
  <div>
    <div class="bar">
      <h2>采蜜批次 · 质检放行</h2>
      <el-button type="primary" @click="openCreate">新增批次</el-button>
    </div>

    <!-- 筛选：批次状态 + 质检结论 -->
    <div class="filters">
      <span class="f-label">批次状态</span>
      <el-select v-model="filter.status" placeholder="全部状态" clearable style="width: 150px">
        <el-option v-for="s in BATCH_STATUSES" :key="s" :label="s" :value="s" />
      </el-select>
      <span class="f-label">质检结论</span>
      <el-select v-model="filter.qcConclusion" placeholder="全部结论" clearable style="width: 150px">
        <el-option v-for="c in QC_CONCLUSIONS" :key="c" :label="c" :value="c" />
      </el-select>
      <el-button :loading="loading" @click="loadList">刷新</el-button>
      <span class="f-count">共 {{ list.length }} 个批次</span>
    </div>

    <el-table :data="list" border stripe v-loading="loading" row-key="id">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column label="蜂箱" width="90">
        <template #default="{ row }">{{ hiveCode(row.hiveId) }}</template>
      </el-table-column>
      <el-table-column prop="batchNo" label="批次号" width="120" />
      <el-table-column prop="weightKg" label="重量(kg)" width="90" />
      <el-table-column label="批次状态" width="112">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" :effect="statusTagEffect(row.status)">
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="质检结论" width="112">
        <template #default="{ row }">
          <el-tag :type="conclusionType(row.qcConclusion)">{{ row.qcConclusion }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="当前生效质检" min-width="280">
        <template #default="{ row }">
          <div v-if="!row.activeQc" class="muted">尚无质检记录</div>
          <div v-else class="qc-cell">
            <div>
              <el-tag size="small" :type="row.activeQc.round === '初检' ? 'primary' : 'warning'">
                {{ row.activeQc.round }}
              </el-tag>
              <span class="mono">{{ row.activeQc.sampleNo }}</span>
              <el-tag
                size="small"
                :type="conclusionType(row.qcConclusion)"
              >{{ row.activeQc.moisture }}% / 感官{{ row.activeQc.sensory }}</el-tag>
            </div>
            <div class="muted">
              {{ row.activeQc.inspector }} · {{ fmtTime(row.activeQc.inspectTime) }}
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="330" fixed="right">
        <template #default="{ row }">
          <div class="acts">
            <el-tooltip
              :content="row.registerQcReason || ''"
              :disabled="row.canRegisterQc" placement="top"
            >
              <span class="act-wrap">
                <el-button
                  size="small" type="primary"
                  :disabled="!row.canRegisterQc"
                  :loading="isBusy(row.id, 'qc')"
                  @click="openQc(row, '初检')"
                >登记质检</el-button>
              </span>
            </el-tooltip>

            <el-tooltip
              :content="row.rectifyReason || ''"
              :disabled="row.canRectify" placement="top"
            >
              <span class="act-wrap">
                <el-button
                  size="small" type="warning"
                  :disabled="!row.canRectify"
                  :loading="isBusy(row.id, 'rect')"
                  @click="openRect(row)"
                >整改登记</el-button>
              </span>
            </el-tooltip>

            <el-tooltip
              :content="row.reinspectReason || ''"
              :disabled="row.canReinspect" placement="top"
            >
              <span class="act-wrap">
                <el-button
                  size="small" type="warning" plain
                  :disabled="!row.canReinspect"
                  @click="openQc(row, '复检')"
                >发起复检</el-button>
              </span>
            </el-tooltip>

            <el-tooltip
              :content="row.stockInReason || ''"
              :disabled="row.canStockIn" placement="top"
            >
              <span class="act-wrap">
                <el-button
                  size="small" type="success"
                  :disabled="!row.canStockIn"
                  :loading="isBusy(row.id, 'stockIn')"
                  @click="stockIn(row)"
                >入库</el-button>
              </span>
            </el-tooltip>

            <el-button size="small" @click="openTrace(row)">质检回溯</el-button>

            <el-tooltip
              :content="editReason(row)" :disabled="canEdit(row)" placement="top"
            >
              <span class="act-wrap">
                <el-button
                  size="small" text
                  :disabled="!canEdit(row)" @click="openEdit(row)"
                >编辑</el-button>
              </span>
            </el-tooltip>

            <el-tooltip
              :content="editReason(row)" :disabled="canEdit(row)" placement="top"
            >
              <span class="act-wrap">
                <el-button
                  size="small" text type="danger"
                  :disabled="!canEdit(row)"
                  :loading="isBusy(row.id, 'delete')"
                  @click="remove(row)"
                >删除</el-button>
              </span>
            </el-tooltip>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增 / 编辑批次 -->
    <el-dialog v-model="batchDialog" :title="editing ? '编辑批次' : '新增批次'" width="420px">
      <el-form label-width="72px">
        <el-form-item label="蜂箱">
          <el-select v-model="batchForm.hiveId" style="width: 100%">
            <el-option
              v-for="h in hives" :key="h.id"
              :label="h.code + '（' + (h.location || '—') + '）'" :value="h.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="批次号">
          <el-input v-model="batchForm.batchNo" placeholder="如 B-2026-011" />
        </el-form-item>
        <el-form-item label="重量(kg)">
          <el-input-number
            v-model="batchForm.weightKg" :min="0.01" :precision="2" :step="0.5" style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchDialog = false">取消</el-button>
        <el-button type="primary" :loading="batchSubmitting" @click="submitBatch">保存</el-button>
      </template>
    </el-dialog>

    <!-- 初检 / 复检登记 -->
    <el-dialog
      v-model="qcDialog"
      :title="qcMode === '初检' ? '登记抽样检测（初检）' : '整改后复检登记'"
      width="520px"
    >
      <div v-if="qcBatch" class="qc-batch">
        批次 <b>{{ qcBatch.batchNo }}</b>（{{ hiveCode(qcBatch.hiveId) }} ·
        {{ qcBatch.weightKg }}kg）当前状态：
        <el-tag
          size="small"
          :type="statusTagType(qcBatch.status)"
          :effect="statusTagEffect(qcBatch.status)"
        >{{ qcBatch.status }}</el-tag>
      </div>

      <el-form label-width="92px" class="mt8">
        <el-form-item label="质检轮次">
          <el-tag :type="qcMode === '初检' ? 'primary' : 'warning'">{{ qcMode }}</el-tag>
          <span v-if="qcMode === '复检'" class="muted ml8">整改后最多发起一次复检</span>
        </el-form-item>
        <el-form-item label="抽样编号">
          <el-input v-model="qcForm.sampleNo" placeholder="如 S-2026-010，全局不可重复" maxlength="32" />
        </el-form-item>
        <el-form-item label="含水率(%)">
          <el-input-number
            v-model="qcForm.moisture" :min="0" :max="100" :precision="2" :step="0.1"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="感官结论">
          <el-radio-group v-model="qcForm.sensory">
            <el-radio value="合格">合格</el-radio>
            <el-radio value="不合格">不合格</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="检测人">
          <el-input v-model="qcForm.inspector" placeholder="检测人员姓名" maxlength="32" />
        </el-form-item>
        <el-form-item label="检测时间">
          <el-date-picker
            v-model="qcForm.inspectTime" type="datetime"
            value-format="YYYY-MM-DD[T]HH:mm:ss" placeholder="选择检测时间" style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="检测说明">
          <el-input
            v-model="qcForm.note" type="textarea" :rows="3" maxlength="255" show-word-limit
            placeholder="检测情况与处理意见（必填）"
          />
        </el-form-item>
      </el-form>

      <!-- 实时放行判定 -->
      <el-alert
        v-if="qcPass" type="success" :closable="false" show-icon
        :title="qcMode === '初检'
          ? '判定合格：含水率 ≤ 20% 且感官合格，提交后批次放行，可办理入库'
          : '复检判定合格：提交后批次放行，可办理入库'"
      />
      <el-alert
        v-else type="error" :closable="false" show-icon class="mt8"
        :title="qcMode === '初检'
          ? '判定不合格，提交后批次将置为「隔离待整改」：须登记整改说明后才能发起复检'
          : '复检仍不合格，提交后批次将置为「隔离拒收」，保持隔离并明确拒绝入库（复检机会仅此一次）'"
      >
        <div>不合格项：
          <span v-if="moistureFail">含水率 {{ qcForm.moisture }}% 超过 20% 上限；</span>
          <span v-if="sensoryFail">感官结论不合格；</span>
        </div>
      </el-alert>

      <template #footer>
        <el-button @click="qcDialog = false">取消</el-button>
        <el-button
          :type="qcPass ? 'success' : 'danger'"
          :loading="qcSubmitting" @click="submitQc"
        >提交{{ qcMode }}结果</el-button>
      </template>
    </el-dialog>

    <!-- 整改说明登记 -->
    <el-dialog v-model="rectDialog" title="隔离批次整改登记" width="520px">
      <div v-if="rectBatch && rectBatch.activeQc" class="qc-batch">
        <div>
          批次 <b>{{ rectBatch.batchNo }}</b>
          <el-tag size="small" type="danger" effect="light">隔离待整改</el-tag>
        </div>
        <div class="muted mt4">
          不合格质检：{{ rectBatch.activeQc.round }} ·
          <span class="mono">{{ rectBatch.activeQc.sampleNo }}</span> ·
          含水率 {{ rectBatch.activeQc.moisture }}% ·
          感官{{ rectBatch.activeQc.sensory }}
        </div>
        <div class="muted">{{ rectBatch.activeQc.note }}</div>
      </div>
      <el-form label-width="92px" class="mt8">
        <el-form-item label="整改说明">
          <el-input
            v-model="rectForm.content" type="textarea" :rows="4" maxlength="500" show-word-limit
            placeholder="说明已采取的整改措施；补充前批次不能再次送检"
          />
        </el-form-item>
        <el-form-item label="整改登记人">
          <el-input v-model="rectForm.handler" placeholder="整改负责人姓名" maxlength="32" />
        </el-form-item>
      </el-form>
      <el-alert
        type="info" :closable="false"
        title="登记整改说明后「发起复检」按钮才可用；整改后最多发起一次复检。"
      />
      <template #footer>
        <el-button @click="rectDialog = false">取消</el-button>
        <el-button type="warning" :loading="rectSubmitting" @click="submitRect">
          提交整改说明
        </el-button>
      </template>
    </el-dialog>

    <!-- 质检回溯 -->
    <el-dialog v-model="traceDialog" title="质检回溯 · 整改与复检历史" width="720px">
      <div v-if="detail">
        <div class="trace-head">
          <div>
            <b>{{ detail.batchNo }}</b>
            <span class="muted">（{{ hiveCode(detail.hiveId) }} · {{ detail.weightKg }}kg）</span>
          </div>
          <el-tag :type="statusTagType(detail.status)" :effect="statusTagEffect(detail.status)">
            {{ detail.status }}
          </el-tag>
          <el-tag :type="conclusionType(detail.qcConclusion)">{{ detail.qcConclusion }}</el-tag>
        </div>

        <div class="trace-title">质检记录（{{ detail.qcRecords.length }}）</div>
        <el-empty
          v-if="!detail.qcRecords.length" description="暂无质检记录" :image-size="50"
        />
        <div
          v-for="q in detail.qcRecords" :key="q.id"
          class="trace-card"
          :class="q.active === 1 ? 'is-active' : 'is-history'"
        >
          <div class="trace-card-head">
            <el-tag size="small" :type="q.round === '初检' ? 'primary' : 'warning'">{{ q.round }}</el-tag>
            <span class="mono">{{ q.sampleNo }}</span>
            <el-tag
              size="small"
              :type="Dict_isPass(q) ? 'success' : 'danger'"
            >{{ Dict_isPass(q) ? '合格 · 放行' : '不合格' }}</el-tag>
            <el-tag
              size="small"
              :type="q.active === 1 ? 'success' : 'info'"
              effect="plain"
            >{{ q.active === 1 ? '生效中' : '历史记录' }}</el-tag>
          </div>
          <div class="trace-card-body">
            <span>含水率：<b :class="Number(q.moisture) > 20 ? 'bad' : ''">{{ q.moisture }}%</b>（上限 20%）</span>
            <span>感官：<b :class="q.sensory === '不合格' ? 'bad' : ''">{{ q.sensory }}</b></span>
            <span>检测人：{{ q.inspector }}</span>
            <span>检测时间：{{ fmtTime(q.inspectTime) }}</span>
          </div>
          <div class="trace-note">说明 / 处理意见：{{ q.note }}</div>
        </div>

        <div class="trace-title">整改说明（{{ detail.rectifications.length }}）</div>
        <el-empty
          v-if="!detail.rectifications.length" description="暂无整改说明" :image-size="50"
        />
        <div v-for="r in detail.rectifications" :key="r.id" class="trace-card rect-card">
          <div class="trace-card-head">
            <el-tag size="small" type="warning">整改</el-tag>
            <span class="muted">{{ fmtTime(r.createdAt) }} · {{ r.handler }}</span>
          </div>
          <div class="trace-note">{{ r.content }}</div>
        </div>
      </div>
      <template #footer>
        <el-button @click="traceDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.bar h2 { margin: 0; font-size: 18px; }

.filters {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  margin-bottom: 12px; padding: 10px 12px;
  background: #fff; border: 1px solid var(--line); border-radius: 10px;
}
.f-label { font-size: 13px; color: var(--muted); }
.f-count { margin-left: auto; font-size: 13px; color: var(--muted); }

.acts { display: flex; flex-wrap: wrap; gap: 4px; align-items: center; }
.act-wrap { display: inline-flex; }

.muted { color: var(--muted); font-size: 12px; }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 12px; }
.mt8 { margin-top: 8px; }
.mt4 { margin-top: 4px; }
.ml8 { margin-left: 8px; }
.qc-cell { display: flex; flex-direction: column; gap: 2px; }
.qc-batch {
  background: #faf7ee; border: 1px solid var(--line);
  border-radius: 8px; padding: 8px 10px; font-size: 13px;
}

.trace-head {
  display: flex; align-items: center; gap: 10px;
  padding-bottom: 10px; border-bottom: 1px dashed var(--line); margin-bottom: 10px;
}
.trace-title { font-weight: 600; margin: 14px 0 8px; font-size: 14px; }
.trace-card {
  border: 1px solid var(--line); border-radius: 10px;
  padding: 10px 12px; margin-bottom: 8px; background: #fff;
}
.trace-card.is-active { border-left: 4px solid #67c23a; }
.trace-card.is-history { border-left: 4px solid #c0c4cc; opacity: 0.85; }
.rect-card { border-left: 4px solid #e6a23c; }
.trace-card-head { display: flex; align-items: center; gap: 8px; }
.trace-card-body {
  display: flex; flex-wrap: wrap; gap: 14px;
  margin: 8px 0 4px; font-size: 13px; color: #4a4334;
}
.trace-note { font-size: 13px; color: #5a4f2e; margin-top: 4px; }
.bad { color: #f56c6c; }
</style>
