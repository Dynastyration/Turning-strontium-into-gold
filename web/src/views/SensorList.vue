<template>
  <section class="view">
    <div class="page-head">
      <div>
        <div class="page-title">传感器管理</div>
        <div class="page-desc">传感器跟着设备上报自动建档，点开卡片能看字段、最新值和最近的曲线。</div>
      </div>
      <div style="display: flex; gap: 10px">
        <button class="btn btn-secondary" @click="openGroups">分组管理</button>
      </div>
    </div>

    <div class="filterbar">
      <div class="field grow">
        <label>关键词（名称、地址码或设备编号）</label>
        <input v-model="filter.keyword" class="input" placeholder="如 应变计或 YX000625" @keyup.enter="search" />
      </div>
      <div class="field">
        <label>传感器类型</label>
        <select v-model="filter.typeId" class="select">
          <option value="">全部</option>
          <option v-for="t in types" :key="t.id" :value="t.id">{{ t.name }}</option>
        </select>
      </div>
      <div class="field">
        <label>状态</label>
        <select v-model="filter.status" class="select">
          <option value="">全部</option>
          <option value="1">正常</option>
          <option value="2">报警中</option>
          <option value="0">离线</option>
          <option value="3">停用</option>
        </select>
      </div>
      <button class="btn btn-primary" @click="search">查询</button>
      <button class="btn btn-ghost" @click="reset">重置</button>
    </div>

    <div v-if="deviceFilter" class="badge accent" style="margin-bottom: 12px">
      只看设备 {{ deviceFilterNum || '已选中' }} 的传感器
      <button class="btn btn-ghost btn-sm" style="height: 20px; padding: 0 6px" @click="clearDevice">清除</button>
    </div>

    <div class="sensor-grid">
      <div v-for="s in list" :key="s.id" class="sensor-card" @click="openDetail(s)">
        <div class="top">
          <div>
            <div class="nm">{{ s.name }}</div>
            <div class="ty">{{ s.sensorTypeName || '—' }}，{{ s.deviceNum }}，<span class="nw">地址码 {{ s.address }}</span></div>
          </div>
          <StatusBadge :text="SNS_STATUS[s.status]?.[0] || '未知'" :tone="SNS_STATUS[s.status]?.[1] || 'off'" dot />
        </div>
        <div class="val">
          {{ s.lastValue == null ? '—' : Number(s.lastValue).toFixed(2) }}<small> {{ primaryUnit(s) }}</small>
        </div>
        <Spark :values="sparks[s.id] || []" :gid="'sp' + s.id" />
        <div class="ft">
          <span>{{ s.groupName || '未分组' }}</span>
          <span class="mono" style="margin-left: auto">{{ fmtClock(s.lastTs) }}</span>
        </div>
      </div>
      <div v-if="!list.length" class="muted" style="padding: 32px; font-size: 13px">没有匹配的传感器</div>
    </div>

    <div class="pager" style="margin-top: 14px">
      <span class="pager-info">共 {{ total }} 只，分 {{ pages }} 页，当前第 {{ page }} 页</span>
      <span class="pager-btns">
        <button :disabled="page <= 1" @click="goto(page - 1)">‹</button>
        <button v-for="p in pageList" :key="p" :class="{ on: p === page }" @click="goto(p)">{{ p }}</button>
        <button :disabled="page >= pages" @click="goto(page + 1)">›</button>
      </span>
    </div>

    <AppDrawer v-model="drawer" :title="detail?.name || '传感器'">
      <template v-if="detail">
        <div style="display: flex; align-items: center; gap: 10px; margin-bottom: 16px">
          <StatusBadge :text="SNS_STATUS[detail.status]?.[0] || '未知'" :tone="SNS_STATUS[detail.status]?.[1] || 'off'" dot />
          <span class="badge info">{{ detail.sensorTypeName || '—' }}</span>
          <span class="badge off">{{ detail.groupName || '未分组' }}</span>
        </div>
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 16px">
          <div class="stat-cell">
            <div class="k">最新值（{{ primary?.name || '主字段' }}）</div>
            <div class="v" style="font-size: 20px">{{ primary?.value == null ? '—' : Number(primary.value).toFixed(primary?.decimals ?? 2) }} {{ primary?.unit || '' }}</div>
            <div class="d">{{ fmtTime(detail.lastTs, true) }}</div>
          </div>
          <div class="stat-cell">
            <div class="k">所属设备</div>
            <div class="v" style="font-size: 20px">{{ detail.deviceNum }}</div>
            <div class="d">地址码 {{ detail.address }}</div>
          </div>
        </div>

        <div class="panel" style="background: var(--bg)">
          <div class="panel-head">
            <div class="panel-title">近 24 小时曲线</div>
            <div class="panel-sub">{{ seriesInfo }}</div>
          </div>
          <div class="chart" style="height: 180px" v-html="seriesSvg" />
        </div>

        <div class="form-grid" style="margin-top: 16px">
          <div class="field full">
            <label>传感器名称</label>
            <input v-model="editForm.name" class="input" />
          </div>
          <div class="field">
            <label>安装部位</label>
            <input v-model="editForm.installPosition" class="input" />
          </div>
          <div class="field">
            <label>报警开关</label>
            <div style="display: flex; align-items: center; gap: 10px; height: 34px">
              <span class="switch" :class="{ on: editForm.alarmEnabled === 1 }" @click="editForm.alarmEnabled = editForm.alarmEnabled ? 0 : 1" />
              <span class="muted" style="font-size: 12px">{{ editForm.alarmEnabled ? '启用' : '停用' }}</span>
            </div>
          </div>
        </div>

        <div class="panel" style="background: var(--bg); margin-top: 16px">
          <div class="panel-head"><div class="panel-title">字段与最新值</div></div>
          <table class="tbl">
            <thead><tr><th>字段</th><th>键</th><th>最新值</th><th>单位</th></tr></thead>
            <tbody>
              <tr v-for="f in latest" :key="f.fieldKey">
                <td>{{ f.name }}</td>
                <td class="mono muted">{{ f.fieldKey }}</td>
                <td class="mono">{{ f.value == null ? '—' : Number(f.value).toFixed(f.decimals ?? 2) }}</td>
                <td class="muted">{{ f.unit }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
      <template #foot>
        <button class="btn btn-ghost" @click="drawer = false">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存' }}</button>
      </template>
    </AppDrawer>

    <AppDialog v-model="groupDlg" title="分组管理">
      <table class="tbl">
        <thead><tr><th>分组</th><th>项目</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="g in groups" :key="g.id">
            <td>{{ g.name }}</td>
            <td class="muted">{{ g.projectId }}</td>
            <td><button class="btn btn-ghost btn-sm" @click="removeGroup(g)">删除</button></td>
          </tr>
          <tr v-if="!groups.length"><td colspan="3" class="muted">还没有分组</td></tr>
        </tbody>
      </table>
      <div class="field" style="margin-top: 14px">
        <label>新建分组</label>
        <div style="display: flex; gap: 8px">
          <input v-model="newGroup" class="input" placeholder="如 跨中组" @keyup.enter="addGroup" />
          <button class="btn btn-primary" @click="addGroup">添加</button>
        </div>
      </div>
      <template #foot>
        <button class="btn btn-ghost" @click="groupDlg = false">关闭</button>
      </template>
    </AppDialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { sensorApi, metaApi, dataApi } from '@/api'
import { useAppStore } from '@/stores/app'
import AppDrawer from '@/components/AppDrawer.vue'
import AppDialog from '@/components/AppDialog.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import Spark from '@/components/Spark.vue'
import { SNS_STATUS, fmtTime, fmtClock, stepLabel, toast, errMsg } from '@/utils/format'
import { multiLine } from '@/utils/charts'

const app = useAppStore()
const route = useRoute()
const router = useRouter()

const list = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const size = 12
const types = ref<any[]>([])
const groups = ref<any[]>([])
const sparks = ref<Record<number, number[]>>({})
const filter = reactive({ keyword: '', typeId: '', status: '' })
const applied = reactive({ keyword: '', typeId: '', status: '' })
const deviceFilter = ref<number | null>(null)
// 从设备页跳过来时带上设备编号，提示条上直接说编号比说库里的 id 有用
const deviceFilterNum = ref('')

const drawer = ref(false)
const saving = ref(false)
const detail = ref<any>(null)
const latest = ref<any[]>([])
const seriesSvg = ref('')
const seriesInfo = ref('')
const editForm = reactive({ name: '', installPosition: '', alarmEnabled: 1 })

const groupDlg = ref(false)
const newGroup = ref('')

const pages = computed(() => Math.max(1, Math.ceil(total.value / size)))
const pageList = computed(() => {
  const arr: number[] = []
  const from = Math.max(1, page.value - 2)
  for (let p = from; p <= Math.min(pages.value, from + 4); p++) arr.push(p)
  return arr
})
const primary = computed(() => latest.value.find((f) => f.isPrimary) || latest.value[0])
const primaryUnit = (s: any) => {
  const t = types.value.find((x) => x.id === s.sensorTypeId)
  const f = t?.fields?.find((x: any) => x.isPrimary === 1) || t?.fields?.[0]
  return f?.unit || ''
}

async function load() {
  const data: any = await sensorApi.page({
    keyword: applied.keyword || undefined,
    typeId: applied.typeId || undefined,
    status: applied.status === '' ? undefined : applied.status,
    deviceId: deviceFilter.value || undefined,
    page: page.value,
    size
  })
  list.value = data?.list || []
  total.value = data?.total || 0
  loadSparks()
}

/** 卡片上的迷你曲线取自真实曲线接口，只取主字段、1 小时粒度。 */
async function loadSparks() {
  const to = new Date()
  const from = new Date(to.getTime() - 24 * 3600 * 1000)
  const fmt = (d: Date) => d.toISOString().slice(0, 19).replace('T', ' ')
  const next: Record<number, number[]> = {}
  await Promise.all(
    list.value.map(async (s) => {
      try {
        const r: any = await dataApi.compare({
          sensorIds: [s.id],
          fieldIds: [],
          from: fmt(from),
          to: fmt(to),
          interval: '1h'
        })
        const values = (r?.series?.[0]?.values || []).filter((v: any) => v != null)
        if (values.length >= 2) next[s.id] = values
      } catch {
        /* 单张卡片取不到曲线不影响列表 */
      }
    })
  )
  sparks.value = next
}

async function openDetail(s: any) {
  detail.value = s
  editForm.name = s.name
  editForm.installPosition = s.installPosition || ''
  editForm.alarmEnabled = s.alarmEnabled ?? 1
  drawer.value = true
  seriesSvg.value = ''
  seriesInfo.value = '加载中…'
  try {
    const d: any = await sensorApi.detail(s.id)
    latest.value = d?.latest || []
    const f = primary.value
    const to = new Date()
    const from = new Date(to.getTime() - 24 * 3600 * 1000)
    const fmt = (x: Date) => x.toISOString().slice(0, 19).replace('T', ' ')
    const r: any = await dataApi.compare({
      sensorIds: [s.id],
      fieldIds: f?.id ? [f.id] : [],
      from: fmt(from),
      to: fmt(to),
      interval: '30m'
    })
    const series = (r?.series || []).map((x: any) => ({ name: `${x.fieldName}(${x.unit})`, values: x.values }))
    const labels = (r?.timeline || []).map((t: string) => t.slice(11, 16))
    seriesSvg.value = multiLine(series, labels)
    seriesInfo.value = `按 ${stepLabel(r?.interval)}汇总，缺值处断开，数据取自 ${r?.source || '—'}`
  } catch (e: any) {
    seriesInfo.value = '曲线加载失败'
  }
}

async function save() {
  saving.value = true
  try {
    await sensorApi.update(detail.value.id, {
      name: editForm.name,
      installPosition: editForm.installPosition,
      alarmEnabled: editForm.alarmEnabled
    })
    drawer.value = false
    toast('已保存')
    load()
  } catch (e: any) {
    toast(errMsg(e, '保存失败'))
  } finally {
    saving.value = false
  }
}

async function openGroups() {
  groupDlg.value = true
  groups.value = (await sensorApi.groups(app.projectId ?? undefined).catch(() => [])) as any[]
}

async function addGroup() {
  if (!newGroup.value.trim()) return
  await sensorApi.createGroup({ name: newGroup.value.trim(), projectId: app.projectId || 1, sortNo: groups.value.length + 1 })
  newGroup.value = ''
  groups.value = (await sensorApi.groups(app.projectId ?? undefined)) as any[]
}

async function removeGroup(g: any) {
  try {
    await sensorApi.deleteGroup(g.id)
    groups.value = (await sensorApi.groups(app.projectId ?? undefined)) as any[]
  } catch (e: any) {
    toast(errMsg(e, '删除失败'))
  }
}

function search() {
  Object.assign(applied, filter)
  page.value = 1
  load()
}

function reset() {
  Object.assign(filter, { keyword: '', typeId: '', status: '' })
  search()
}

function goto(p: number) {
  if (p < 1 || p > pages.value) return
  page.value = p
  load()
}

function clearDevice() {
  deviceFilter.value = null
  deviceFilterNum.value = ''
  router.replace({ path: '/sensors' })
  page.value = 1
  load()
}

watch(
  () => route.query.deviceId,
  (v) => {
    deviceFilter.value = v ? Number(v) : null
    deviceFilterNum.value = String(route.query.deviceNum || '')
    page.value = 1
    load()
  }
)

watch(
  () => app.projectId,
  () => {
    page.value = 1
    load()
  }
)

onMounted(async () => {
  deviceFilter.value = route.query.deviceId ? Number(route.query.deviceId) : null
  deviceFilterNum.value = String(route.query.deviceNum || '')
  types.value = ((await metaApi.sensorTypes().catch(() => [])) as any[]) || []
  await load()
})
</script>
