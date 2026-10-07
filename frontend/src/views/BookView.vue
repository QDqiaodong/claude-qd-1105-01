<script setup>
import { computed, ref, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import router from '../router'

const emit = defineEmits(['changed'])

// ------------------------------------------------------------------
// 查询条件 / 分页全部落在 URL query 上（hash 路由：/#/books?isbn=...&page=2），
// 刷新、从详情返回、从销售单跳入后，列表结果都保持一致。
// 详情通过 ?focus=<bookId> 标记，按 id 直查后端，与列表排序 / 分页无关。
// ------------------------------------------------------------------
const q0 = router.currentRoute.value.query
const str = (v) => (typeof v === 'string' ? v : '')
const num = (v, dft) => {
  const n = Number(v)
  return Number.isInteger(n) && n > 0 ? n : dft
}

const rows = ref([])
const total = ref(0)
const keyword = ref(str(q0.keyword))
const isbnQuery = ref(str(q0.isbn))
const statusFilter = ref(str(q0.status))
const page = ref(num(q0.page, 1))
const size = ref(num(q0.size, 10))
const loading = ref(false)
const duplicateIsbns = ref([])

const STATUS_OPTIONS = ['在售', '缺货', '下架']

const showNew = ref(false)
const newRow = ref({ isbn: '', title: '', author: '', price: 0, stock: 0, status: '在售' })

// 详情抽屉
const focusId = ref(num(q0.focus, 0) || null)
const detail = ref(null)
const detailVisible = ref(false)
const detailLoading = ref(false)

// 竞态保护：高频连续查询时，只有最后一次请求能落地，
// 较慢的前一次响应直接丢弃，绝不允许旧结果覆盖新结果。
let loadSeq = 0
let detailSeq = 0

function syncQuery() {
  const query = {}
  if (keyword.value.trim()) query.keyword = keyword.value.trim()
  if (isbnQuery.value.trim()) query.isbn = isbnQuery.value.trim()
  if (statusFilter.value) query.status = statusFilter.value
  if (page.value > 1) query.page = String(page.value)
  if (size.value !== 10) query.size = String(size.value)
  if (focusId.value) query.focus = String(focusId.value)
  // 相同导航会被 vue-router 拒绝，吞掉即可
  router.replace({ path: '/books', query }).catch(() => {})
}

async function load() {
  const seq = ++loadSeq
  loading.value = true
  syncQuery()
  try {
    const res = await api.listBooks({
      status: statusFilter.value,
      keyword: keyword.value,
      isbn: isbnQuery.value,
      page: page.value,
      size: size.value
    })
    if (seq !== loadSeq) return // 已有更新的查询，丢弃这份过期结果
    // 过滤条件变化后当前页可能超出范围：回到最后一页重取，保证结果一致
    if (res.records.length === 0 && res.total > 0) {
      const lastPage = Math.max(1, Math.ceil(res.total / size.value))
      if (page.value > lastPage) {
        page.value = lastPage
        return load()
      }
    }
    rows.value = res.records
    total.value = res.total
    duplicateIsbns.value = res.duplicateIsbns || []
  } catch (e) {
    if (seq !== loadSeq) return
    ElMessage.error(e.message)
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function onPageChange(p) {
  page.value = p
  load()
}

function onSizeChange(s) {
  size.value = s
  page.value = 1
  load()
}

// ---------------- 详情（按 id 直查，与列表排序 / 分页无关） ----------------
async function openDetail(id) {
  focusId.value = id
  syncQuery()
  const seq = ++detailSeq
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const d = await api.getBook(id)
    if (seq !== detailSeq) return
    detail.value = d
  } catch (e) {
    if (seq !== detailSeq) return
    // 图书不存在 / 已被删除：明确提示，不留半开的抽屉
    detailVisible.value = false
    focusId.value = null
    syncQuery()
    ElMessage.error(e.message)
  } finally {
    if (seq === detailSeq) detailLoading.value = false
  }
}

function closeDetail() {
  detailSeq++ // 使进行中的详情请求失效
  detailVisible.value = false
  detail.value = null
  focusId.value = null
  syncQuery()
}

// 从销售单明细等入口跳入（组件已挂载时 focus 变化）
watch(() => router.currentRoute.value.query.focus, (v) => {
  const id = num(v, 0)
  if (id && id !== focusId.value) {
    openDetail(id)
  }
})

// ---------------- 行内编辑 / 新增 ----------------
async function saveRow(row) {
  try {
    await api.updateBook(row.id, {
      isbn: row.isbn,
      title: row.title,
      author: row.author,
      price: row.price,
      stock: row.stock,
      status: row.status
    })
    ElMessage.success('已保存')
    await load()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openNew() {
  newRow.value = { isbn: '', title: '', author: '', price: 0, stock: 0, status: '在售' }
  showNew.value = true
}

async function submitNew() {
  try {
    await api.createBook({ ...newRow.value })
    ElMessage.success('新增成功')
    showNew.value = false
    await load()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
  }
}

// ---------------- 展示辅助 ----------------
function statusType(s) {
  if (s === '在售') return 'success'
  if (s === '缺货') return 'warning'
  return 'info'
}

function isDup(isbn) {
  return !!isbn && duplicateIsbns.value.includes(isbn)
}

// 空结果必须给出明确原因，不能只是一张空表
const emptyText = computed(() => {
  const parts = []
  if (isbnQuery.value.trim()) parts.push(`ISBN 包含「${isbnQuery.value.trim()}」`)
  if (keyword.value.trim()) parts.push(`匹配「${keyword.value.trim()}」`)
  if (statusFilter.value) parts.push(`状态为「${statusFilter.value}」`)
  return parts.length
    ? `未找到${parts.join('、')}的图书，请检查输入或调整条件`
    : '暂无图书'
})

function fmt(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : '—'
}

onMounted(async () => {
  await load()
  // 带 ?focus= 进入（如从销售单明细点入）：列表加载完直接打开该图书详情
  if (focusId.value) {
    openDetail(focusId.value)
  }
})
</script>

<template>
  <div>
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索书名 / 作者 / ISBN" clearable style="width: 220px"
        @keyup.enter="search" @clear="search" />
      <el-input v-model="isbnQuery" placeholder="ISBN（完整或部分）" clearable style="width: 180px"
        @keyup.enter="search" @clear="search" />
      <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 130px" @change="search">
        <el-option v-for="s in STATUS_OPTIONS" :key="s" :label="s" :value="s" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="openNew">新增图书</el-button>
    </div>

    <!-- 重复 ISBN 档案：明确提示，避免用户改错书 -->
    <el-alert
      v-if="duplicateIsbns.length"
      class="dup-alert"
      type="warning"
      show-icon
      :closable="false"
      :title="`发现重复 ISBN 档案：${duplicateIsbns.join('、')} —— 同一 ISBN 对应多本图书，请核对书号后再操作`"
    />

    <el-table :data="rows" v-loading="loading" border stripe style="margin-top: 12px">
      <el-table-column label="ISBN" min-width="170">
        <template #default="{ row }">
          <el-input v-model="row.isbn" size="small" placeholder="未登记" />
          <el-tag v-if="isDup(row.isbn)" type="warning" size="small" effect="plain" class="dup-tag">重复</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="书名" min-width="150">
        <template #default="{ row }">
          <el-input v-model="row.title" size="small" />
        </template>
      </el-table-column>
      <el-table-column prop="author" label="作者" min-width="110">
        <template #default="{ row }">
          <el-input v-model="row.author" size="small" />
        </template>
      </el-table-column>
      <el-table-column prop="price" label="定价" width="130">
        <template #default="{ row }">
          <el-input-number v-model="row.price" :min="0" :precision="2" :step="1" size="small" controls-position="right" style="width: 110px" />
        </template>
      </el-table-column>
      <el-table-column prop="stock" label="库存" width="130">
        <template #default="{ row }">
          <el-input-number v-model="row.stock" :min="0" :step="1" size="small" controls-position="right" style="width: 110px" />
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="120">
        <template #default="{ row }">
          <el-select v-model="row.status" size="small" style="width: 90px">
            <el-option v-for="s in STATUS_OPTIONS" :key="s" :label="s" :value="s" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="saveRow(row)">保存</el-button>
          <el-button link size="small" @click="openDetail(row.id)">详情</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty :description="emptyText" :image-size="60" />
      </template>
    </el-table>

    <div class="pager">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-sizes="[10, 20, 50]"
        :current-page="page"
        :page-size="size"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>

    <!-- 行内新增表单（非弹窗） -->
    <el-card v-if="showNew" class="new-card" shadow="never">
      <template #header>新增图书</template>
      <div class="new-form">
        <el-input v-model="newRow.isbn" placeholder="ISBN（可空，旧书未登记）" style="width: 180px" />
        <el-input v-model="newRow.title" placeholder="书名" style="width: 160px" />
        <el-input v-model="newRow.author" placeholder="作者" style="width: 120px" />
        <el-input-number v-model="newRow.price" :min="0" :precision="2" placeholder="定价" style="width: 120px" controls-position="right" />
        <el-input-number v-model="newRow.stock" :min="0" placeholder="库存" style="width: 110px" controls-position="right" />
        <el-button type="primary" @click="submitNew">确定</el-button>
        <el-button @click="showNew = false">取消</el-button>
      </div>
    </el-card>

    <!-- 图书详情抽屉：按 id 直查，缺失字段显示为空 -->
    <el-drawer
      :model-value="detailVisible"
      title="图书详情"
      size="380px"
      @update:model-value="(v) => { if (!v) closeDetail() }"
    >
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-alert
            v-if="isDup(detail.isbn)"
            type="warning"
            show-icon
            :closable="false"
            style="margin-bottom: 12px"
            title="该 ISBN 存在多本重复档案，请核对后再操作"
          />
          <el-descriptions :column="1" border>
            <el-descriptions-item label="书名">{{ detail.title || '—' }}</el-descriptions-item>
            <el-descriptions-item label="作者">{{ detail.author || '—' }}</el-descriptions-item>
            <el-descriptions-item label="ISBN">
              <span v-if="detail.isbn">{{ detail.isbn }}</span>
              <span v-else class="missing">未登记</span>
            </el-descriptions-item>
            <el-descriptions-item label="定价">
              {{ detail.price != null ? `¥${Number(detail.price).toFixed(2)}` : '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="库存">{{ detail.stock != null ? detail.stock : '—' }}</el-descriptions-item>
            <el-descriptions-item label="销售状态">
              <el-tag v-if="detail.status" :type="statusType(detail.status)" size="small">{{ detail.status }}</el-tag>
              <span v-else>—</span>
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ fmt(detail.createdAt) }}</el-descriptions-item>
          </el-descriptions>
        </template>
        <el-empty v-else-if="!detailLoading" description="未加载到图书信息" :image-size="60" />
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.toolbar { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.dup-alert { margin-top: 10px; }
.dup-tag { margin-top: 4px; }
.pager { margin-top: 12px; display: flex; justify-content: flex-end; }
.new-card { margin-top: 16px; }
.new-form { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.missing { color: var(--muted); }
</style>
