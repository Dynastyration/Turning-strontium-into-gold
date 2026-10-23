<template>
  <section class="view">
    <div class="page-head">
      <div>
        <div class="page-title">数据管理</div>
        <div class="page-desc">按传感器和时间范围查数据，两条曲线能放到一张图上对比，也能导出成 CSV。</div>
      </div>
    </div>

    <div class="filterbar">
      <div class="field">
        <label>传感器</label>
        <select v-model="query.sensorId" class="select" style="min-width: 170px" @change="onSensorChange">
          <option v-for="s in sensors" :key="s.id" :value="s.id">{{ s.name }}</option>
        </select>
      </div>
      <div class="field">
        <label>对比传感器（可选）</label>
        <select v-model="query.compareId" class="select" style="min-width: 170px">
          <option :value="null">不对比</option>
          <option v-for="s in sensors" :key="s.id" :value="s.id">{{ s.name }}</option>
        </select>
      </div>
      <div class="field">
        <label>字段</label>
        <select v-model="query.fieldKey" class="select" style="min-width: 150px">
          <option v-for="f in fields" :key="f.id" :value="f.fieldKey">{{ f.name }} ({{ f.unit }})</option>
        </select>
      </div>
      <div class="field">
        <label>开始时间</label>
        <input v-model="query.from" class="input" type="datetime-local" />
      </div>
      <div class="field">
        <label>结束时间</label>
        <input v-model="query.to" class="input" type="datetime-local" />
      </div>
      <div class="field">
        <label>粒度</label>
        <select v-model="query.interval" class="select">
          <option value="1m">1 分钟</option>
          <option value="5m">5 分钟</option>
          <option value="1h">1 小时</option>
          <option value="1d">1 天</option>
        </select>
      </div>
      <button class="btn btn-primary" :disabled="loading" @click="runQuery">{{ loading ? '查询中…' : '查询' }}</button>
    </div>

    <div class="panel" style="margin-bottom: 16px">
      <div class="panel-head">
        <div>
          <div class="panel-title">监测曲线对比</div>
          <div class="panel-sub">{{ chartSub }}</div>
        </div>
        <span v-if="chartSource" class="badge off">源表 {{ chartSource }}</span>
      </div>
      <div ref="chartEl" class="chart" style="height: 320px" v-html="chartSvg" />
    </div>

    <div class="panel">
      <div class="panel-head">
        <div>
          <div class="panel-title">报表导出任务</div>
          <div class="panel-sub">后台生成，列表每 2 秒刷新</div>
        </div>
        <button class="btn btn-secondary btn-sm" @click="reportDlg = true">新建导出</button>
      </div>
      <div class="tbl-wrap">
        <table class="tbl">
          <thead>
            <tr><th>任务</th><th>类型</th><th>格式</th><th>状态</th><th>进度</th><th>行数</th><th>创建时间</th><th>操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="r in reports" :key="r.id">
              <td class="cell-main">报表 #{{ r.id }}</td>
              <td>{{ BIZ[r.bizType] || '—' }}</td>
              <td class="mono">csv</td>
              <td><StatusBadge :text="REPORT_STATUS[r.status]?.[0] || '—'" :tone="REPORT_STATUS[r.status]?.[1] || 'off'" /></td>
              <td>
                <span v-if="r.status === 2" class="badge ok">已完成</span>
                <span v-else-if="r.status === 1" style="display: inline-flex; align-items: center; gap: 6px">
                  <span style="display: inline-block; width: 80px; height: 6px; background: var(--surface-2); border-radius: 3px">
                    <i style="display: block; height: 100%; border-radius: 3px; background: var(--accent)" :style="{ width: (r.progress || 0) + '%' }" />
                  </span>
                  <span class="mono muted" style="font-size: 11px">{{ r.progress || 0 }}%</span>
                </span>
                <span v-else-if="r.status === 3" class="badge danger">{{ r.errorMsg || '失败' }}</span>
                <span v-else class="muted">—</span>
              </td>
              <td class="num">{{ r.rowCount ? fmtInt(r.rowCount) : '—' }}</td>
              <td class="mono muted">{{ fmtTime(r.createdAt) }}</td>
              <td>
                <button v-if="r.status === 2" class="btn btn-ghost btn-sm" @click="download(r.id)">下载</button>
                <button v-else class="btn btn-ghost btn-sm" disabled>下载</button>
              </td>
            </tr>
            <tr v-if="!reports.length"><td colspan="8" class="muted" style="text-align: center; padding: 28px">还没有导出任务</td></tr>
          </tbody>
        </table>
      </div>
    </div>

    <AppDialog v-model="reportDlg" title="新建导出任务">
      <div class="field" style="margin-bottom: 12px">
        <label>导出内容</label>
        <select v-model.number="reportForm.bizType" class="select">
          <option :value="1">监测数据明细</option>
          <option :value="3">报警记录</option>
          <option :value="4">设备台账</option>
        </select>
      </div>
      <div v-if="reportForm.bizType !== 4" class="form-grid" style="margin-bottom: 12px">
        <div class="field">
          <label>起始日期</label>
          <input v-model="reportForm.from" class="input" type="date" />
        </div>
        <div class="field">
          <label>结束日期</label>
          <input v-model="reportForm.to" class="input" type="date" />
        </div>
      </div>
      <div style="font-size: 12px; color: var(--muted)">
        {{ reportHint }}
      </div>
      <template #foot>
        <button class="btn btn-ghost" @click="reportDlg = false">取消</button>
        <button class="btn btn-primary" @click="createReport">创建任务</button>
      </template>
    </AppDialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { sensorApi, dataApi, metaApi } from '@/api'
import { useAppStore } from '@/stores/app'
import AppDialog from '@/components/AppDialog.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { REPORT_STATUS, fmtInt, fmtTime, stepLabel, toast, errMsg } from '@/utils/format'
import { multiLine } from '@/utils/charts'

const app = useAppStore()

const BIZ: Record<number, string> = { 1: '监测数据明细', 3: '报警记录', 4: '设备台账' }

const sensors = ref<any[]>([])
const types = ref<any[]>([])
const fields = ref<any[]>([])
const loading = ref(false)
// 按容器实际宽度绘制，避免 720 宽的 viewBox 在宽面板里被等比缩小、两侧留白
const chartEl = ref<HTMLElement | null>(null)
const chartBox = reactive({ w: 720, h: 320 })
const chartData = ref<{ series: { name: string; values: (number | null)[] }[]; labels: string[] }>({ series: [], labels: [] })
const chartSvg = computed(() =>
  chartData.value.labels.length ? multiLine(chartData.value.series, chartData.value.labels, { w: chartBox.w, h: chartBox.h }) : ''
)
const chartSub = ref('选好条件后点查询')
const chartSource = ref('')
const reports = ref<any[]>([])

const nowLocal = () => {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`
}

const query = reactive({
  sensorId: null as number | null,
  compareId: null as number | null,
  fieldKey: '',
  from: (() => {
    const d = new Date(Date.now() - 24 * 3600 * 1000)
    const p = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`
  })(),
  to: nowLocal(),
  interval: '1h'
})

const reportDlg = ref(false)
const today = new Date().toISOString().slice(0, 10)
const reportForm = reactive({ bizType: 1, from: today, to: today })

const reportHint = computed(() => {
  if (reportForm.bizType === 4) {
    return '导出全部设备的台账信息，和日期范围无关。'
  }
  const scope = query.sensorId ? '所选传感器' : '全部传感器'
  if (reportForm.bizType === 3) {
    return `导出这几天里${scope}第一次触发的告警。`
  }
  return `把这几天里${scope}的每条采集记录都导出来，生成后 7 天内可以下载。`
})

const fieldsOf = (typeId: number) => types.value.find((t) => t.id === typeId)?.fields || []

function onSensorChange() {
  const s = sensors.value.find((x) => x.id === query.sensorId)
  fields.value = s ? fieldsOf(s.sensorTypeId) : []
  const primary = fields.value.find((f: any) => f.isPrimary === 1) || fields.value[0]
  query.fieldKey = primary?.fieldKey || ''
}

/** 两个传感器类型可能不同，用字段键各自解析出同名字段 id。 */
function resolveFieldIds(): number[] {
  const ids: number[] = []
  const pick = (sensorId: number | null) => {
    if (!sensorId) return
    const s = sensors.value.find((x) => x.id === sensorId)
    if (!s) return
    const f = fieldsOf(s.sensorTypeId).find((x: any) => x.fieldKey === query.fieldKey)
    if (f && !ids.includes(f.id)) ids.push(f.id)
  }
  pick(query.sensorId)
  pick(query.compareId)
  return ids
}

async function runQuery() {
  const ids = [query.sensorId, query.compareId].filter((x): x is number => !!x)
  if (!ids.length) {
    toast('请选择传感器')
    return
  }
  loading.value = true
  try {
    const r: any = await dataApi.compare({
      sensorIds: ids,
      fieldIds: resolveFieldIds(),
      from: query.from.replace('T', ' '),
      to: query.to.replace('T', ' '),
      interval: query.interval
    })
    const series = (r?.series || []).map((x: any) => ({ name: `${x.sensorName} ${x.fieldName}(${x.unit})`, values: x.values }))
    const labels = (r?.timeline || []).map((t: string) => t.slice(5, 16))
    chartData.value = { series, labels }
    chartSource.value = r?.source || ''
    chartSub.value = `按 ${stepLabel(r?.interval)}汇总，缺值处断开，共 ${series.length} 条曲线`
  } catch (e: any) {
    toast(errMsg(e, '查询失败'))
  } finally {
    loading.value = false
  }
}

async function loadReports() {
  try {
    reports.value = ((await dataApi.reports()) as any[]) || []
  } catch {
    reports.value = []
  }
}

/** 结束日期当天的最后一刻之后，用于把「结束日期」也算进区间。 */
function nextDay(d: string): string {
  const [y, m, dd] = d.split('-').map(Number)
  const t = new Date(y, m - 1, dd + 1)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${t.getFullYear()}-${p(t.getMonth() + 1)}-${p(t.getDate())}`
}

async function createReport() {
  try {
    const params =
      reportForm.bizType === 4
        ? JSON.stringify({})
        : JSON.stringify({
            // 勾了对比传感器就一起导出，没勾就只导出主传感器（未选则全部）
            sensorIds: [query.sensorId, query.compareId].filter((x): x is number => !!x),
            from: `${reportForm.from} 00:00:00`,
            to: `${nextDay(reportForm.to)} 00:00:00`
          })
    await dataApi.createReport({ bizType: reportForm.bizType, params, fmt: 1 })
    reportDlg.value = false
    toast('导出任务已进入队列')
    loadReports()
  } catch (e: any) {
    toast(errMsg(e, '创建失败'))
  }
}

function download(id: number) {
  const token = localStorage.getItem('bn_token') || ''
  window.open(`/api/v1/reports/${id}/download?token=${encodeURIComponent(token)}`, '_blank')
}

let poll = 0
let chartRO: ResizeObserver | null = null
onMounted(async () => {
  if (chartEl.value) {
    chartRO = new ResizeObserver(([entry]) => {
      const r = entry.contentRect
      chartBox.w = Math.max(240, Math.round(r.width))
      chartBox.h = Math.max(160, Math.round(r.height))
    })
    chartRO.observe(chartEl.value)
  }
  types.value = ((await metaApi.sensorTypes().catch(() => [])) as any[]) || []
  const data: any = await sensorApi.page({ page: 1, size: 200, projectId: app.projectId ?? undefined }).catch(() => ({ list: [] }))
  sensors.value = data?.list || []
  if (sensors.value.length) {
    query.sensorId = sensors.value[0].id
    onSensorChange()
    runQuery()
  }
  await loadReports()
  poll = window.setInterval(() => {
    if (reports.value.some((r) => r.status === 0 || r.status === 1)) loadReports()
  }, 2000)
})

onUnmounted(() => {
  window.clearInterval(poll)
  chartRO?.disconnect()
})
</script>
