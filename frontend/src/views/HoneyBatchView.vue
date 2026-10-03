<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const list = ref([])
const hives = ref([])
const hiveMap = computed(() => {
  const m = {}
  hives.value.forEach(h => { m[h.id] = h })
  return m
})
const hiveCode = (id) => (hiveMap.value[id] ? hiveMap.value[id].code : ('#' + id))

const dialog = ref(false)
const editing = ref(false)
const form = reactive({ id: null, hiveId: null, batchNo: '', weightKg: 0 })
const STATUSES = ['待质检', '已入库', '已作废']

const statusType = (s) => (s === '已入库' ? 'success' : s === '已作废' ? 'info' : 'warning')

async function load() {
  const [b, h] = await Promise.all([api.listHoneyBatches(), api.listHives()])
  list.value = b
  hives.value = h
  emit('changed')
}
const emit = defineEmits(['changed'])

function openCreate() {
  editing.value = false
  form.id = null
  form.hiveId = hives.value.length ? hives.value[0].id : null
  form.batchNo = ''
  form.weightKg = 0
  dialog.value = true
}
function openEdit(row) {
  editing.value = true
  form.id = row.id; form.hiveId = row.hiveId; form.batchNo = row.batchNo; form.weightKg = row.weightKg
  dialog.value = true
}

async function submit() {
  if (!form.weightKg || Number(form.weightKg) <= 0) {
    ElMessage.error('重量必须大于 0')
    return
  }
  try {
    const payload = { hiveId: form.hiveId, batchNo: form.batchNo, weightKg: Number(form.weightKg) }
    if (editing.value) {
      await api.updateHoneyBatch(form.id, payload)
      ElMessage.success('已保存')
    } else {
      await api.createHoneyBatch(payload)
      ElMessage.success('已新增')
    }
    dialog.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function stockIn(row) {
  try {
    await api.stockInHoneyBatch(row.id)
    ElMessage.success('已入库')
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}
async function remove(row) {
  try {
    await api.deleteHoneyBatch(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="bar">
      <h2>采蜜批次</h2>
      <el-button type="primary" @click="openCreate">新增批次</el-button>
    </div>
    <el-table :data="list" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="蜂箱" width="100">
        <template #default="{ row }">{{ hiveCode(row.hiveId) }}</template>
      </el-table-column>
      <el-table-column prop="batchNo" label="批次号" width="130" />
      <el-table-column prop="weightKg" label="重量(kg)" width="110" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="success" :disabled="row.status !== '待质检'" @click="stockIn(row)">入库</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="editing ? '编辑批次' : '新增批次'" width="420px">
      <el-form label-width="72px">
        <el-form-item label="蜂箱">
          <el-select v-model="form.hiveId" style="width: 100%">
            <el-option v-for="h in hives" :key="h.id" :label="h.code + '（' + (h.location || '—') + '）'" :value="h.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="批次号">
          <el-input v-model="form.batchNo" placeholder="如 B-2026-001" />
        </el-form-item>
        <el-form-item label="重量(kg)">
          <el-input-number v-model="form.weightKg" :min="0.01" :precision="2" :step="0.5" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.bar h2 { margin: 0; font-size: 18px; }
</style>
