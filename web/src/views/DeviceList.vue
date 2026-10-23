<template>
  <section class="view">
    <div class="page-head">
      <div>
        <div class="page-title">设备管理</div>
        <div class="page-desc">现场采集终端和网关的台账，能改参数，也能给设备下发指令。</div>
      </div>
      <div style="display: flex; gap: 10px">
        <button class="btn btn-primary" @click="openEdit(null)">新增设备</button>
      </div>
    </div>

    <div class="stat-strip">
      <div class="stat-cell">
        <div class="k">设备总数</div>
        <div class="v">{{ stats.total }}</div>
        <div class="d">台</div>
      </div>
      <div class="stat-cell">
        <div class="k">在线</div>
        <div class="v">{{ stats.online }}</div>
        <div class="d">台</div>
      </div>
      <div class="stat-cell">
        <div class="k">在线率</div>
        <div class="v">{{ stats.rate }}%</div>
        <div class="d">在线台数占全部</div>
      </div>
      <div class="stat-cell">
        <div class="k">低电量</div>
        <div class="v">{{ stats.lowBattery }}</div>
        <div class="d">电量低于 20%</div>
      </div>
    </div>

    <div class="filterbar">
      <div class="field grow">
        <label>关键词（编号或名称）</label>
        <input v-model="filter.keyword" class="input" placeholder="如 YX000625" @keyup.enter="search" />
      </div>
      <div class="field">
        <label>设备类型</label>
        <select v-model="filter.typeId" class="select">
          <option value="">全部</option>
          <option v-for="d in deviceTypes" :key="d.dictKey" :value="Number(d.dictKey)">{{ d.dictValue }}</option>
        </select>
      </div>
      <div class="field">
        <label>运行状态</label>
        <select v-model="filter.status" class="select">
          <option value="">全部</option>
          <option value="1">在线</option>
          <option value="0">离线</option>
          <option value="2">故障</option>
          <option value="3">调试中</option>
        </select>
      </div>
      <div class="field">
        <label>电源类型</label>
        <select v-model="filter.powerType" class="select">
          <option value="">全部</option>
          <option value="1">市电</option>
          <option value="2">太阳能</option>
          <option value="3">锂电</option>
        </select>
      </div>
      <button class="btn btn-primary" @click="search">查询</button>
      <button class="btn btn-ghost" @click="reset">重置</button>
    </div>

    <div class="panel">
      <div class="tbl-wrap">
        <table class="tbl">
          <thead>
            <tr>
              <th>设备编号</th><th>名称</th><th>类型</th><th>运行状态</th><th>电量</th>
              <th>传感器数</th><th>采集周期</th><th>最后上报</th><th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="d in list" :key="d.id">
              <td class="mono cell-main">{{ d.deviceNum }}</td>
              <td><div class="cell-main">{{ d.name }}</div></td>
              <td class="muted">{{ typeName(d.typeId) }}</td>
              <td><StatusBadge :text="STATUS[d.runStatus]?.[0] || '未知'" :tone="STATUS[d.runStatus]?.[1] || 'off'" dot /></td>
              <td><Battery :value="d.battery" /></td>
              <td class="num">{{ d.sensorCount ?? 0 }}</td>
              <td class="mono muted">{{ d.collectPeriod }}s</td>
              <td class="mono muted">{{ fmtTime(d.lastPushAt) }}</td>
              <td style="white-space: nowrap">
                <button class="btn btn-ghost btn-sm" @click="openEdit(d)">编辑</button>
                <button class="btn btn-ghost btn-sm" @click="openCmd(d)">指令</button>
                <button class="btn btn-ghost btn-sm" @click="goSensors(d)">传感器</button>
              </td>
            </tr>
            <tr v-if="!list.length">
              <td colspan="9" class="muted" style="text-align: center; padding: 32px">没有匹配的设备</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="pager">
        <span class="pager-info">共 {{ total }} 条，分 {{ pages }} 页，当前第 {{ page }} 页</span>
        <span class="pager-btns">
          <button :disabled="page <= 1" @click="goto(page - 1)">‹</button>
          <button v-for="p in pageList" :key="p" :class="{ on: p === page }" @click="goto(p)">{{ p }}</button>
          <button :disabled="page >= pages" @click="goto(page + 1)">›</button>
        </span>
      </div>
    </div>

    <AppDrawer v-model="drawer" :title="form.id ? `编辑设备（${form.deviceNum}）` : '新增设备'">
      <div class="form-grid">
        <div class="field full">
          <label>设备名称</label>
          <input v-model="form.name" class="input" placeholder="如 主塔偏位采集仪" />
        </div>
        <div class="field">
          <label>设备编号</label>
          <input v-model="form.deviceNum" class="input mono" placeholder="YX000625" />
        </div>
        <div class="field">
          <label>设备类型</label>
          <select v-model="form.typeId" class="select">
            <option v-for="d in deviceTypes" :key="d.dictKey" :value="Number(d.dictKey)">{{ d.dictValue }}</option>
          </select>
        </div>
        <div class="field">
          <label>所属项目</label>
          <select v-model="form.projectId" class="select">
            <option v-for="p in app.projects" :key="p.id" :value="p.id">{{ p.name }}</option>
          </select>
        </div>
        <div class="field">
          <label>电源类型</label>
          <select v-model="form.powerType" class="select">
            <option :value="1">市电</option>
            <option :value="2">太阳能</option>
            <option :value="3">锂电</option>
          </select>
        </div>
        <div class="field">
          <label>采集周期（秒）</label>
          <input v-model.number="form.collectPeriod" class="input num" />
        </div>
        <div class="field">
          <label>指令间隔（秒）</label>
          <input v-model.number="form.cmdInterval" class="input num" />
        </div>
        <div class="field">
          <label>调试模式</label>
          <div style="display: flex; align-items: center; gap: 10px; height: 34px">
            <span class="switch" :class="{ on: form.debugMode === 1 }" @click="form.debugMode = form.debugMode ? 0 : 1" />
            <span class="muted" style="font-size: 12px">{{ form.debugMode ? '开' : '关' }}</span>
          </div>
        </div>
        <div class="field">
          <label>排序号</label>
          <input v-model.number="form.sortNo" class="input num" />
        </div>
        <div class="field">
          <label>经度（WGS84）</label>
          <input v-model.number="form.lng" class="input num" />
        </div>
        <div class="field">
          <label>纬度（WGS84）</label>
          <input v-model.number="form.lat" class="input num" />
        </div>
        <div class="field">
          <label>SIM 到期日</label>
          <input v-model="form.simExpire" class="input" type="date" />
        </div>
        <div class="field">
          <label>安装日期</label>
          <input v-model="form.installAt" class="input" type="date" />
        </div>
        <div class="field full">
          <label>位置描述（用于地图定位）</label>
          <input v-model="form.searchLocation" class="input" placeholder="如 常州市新北区春江镇" />
        </div>
        <div class="field full">
          <label>备注</label>
          <textarea v-model="form.remark" class="textarea" placeholder="备注" />
        </div>
      </div>
      <template #foot>
        <button class="btn btn-ghost" @click="drawer = false">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存' }}</button>
      </template>
    </AppDrawer>

    <AppDialog v-model="cmdDlg" :title="`远程配置（${current?.deviceNum || ''}）`">
      <div class="field" style="margin-bottom: 14px">
        <label>指令类型</label>
        <select v-model="cmd.type" class="select">
          <option value="SET_PERIOD">设置采集周期</option>
          <option value="SET_INTERVAL">设置指令间隔</option>
          <option value="SET_DEBUG">开关调试模式</option>
          <option value="REBOOT">重启设备</option>
          <option value="SYNC_TIME">同步时间</option>
        </select>
      </div>
      <div class="field">
        <label>参数（JSON）</label>
        <textarea v-model="cmd.payload" class="textarea mono" />
      </div>
      <div style="margin-top: 12px; font-size: 12px; color: var(--muted)">
        指令会先记一条到库里，设备状态同时标成「配置中」。设备回执和状态更新还没接。
      </div>
      <template #foot>
        <button class="btn btn-ghost" @click="cmdDlg = false">取消</button>
        <button class="btn btn-primary" @click="sendCmd">下发指令</button>
      </template>
    </AppDialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { deviceApi, dashboardApi, metaApi } from '@/api'
import { useAppStore } from '@/stores/app'
import AppDrawer from '@/components/AppDrawer.vue'
import AppDialog from '@/components/AppDialog.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import Battery from '@/components/Battery.vue'
import { STATUS, fmtTime, toast, errMsg } from '@/utils/format'

const app = useAppStore()
const router = useRouter()

const list = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const deviceTypes = ref<any[]>([])
const filter = reactive({ keyword: '', typeId: '', status: '', powerType: '' })
const applied = reactive({ keyword: '', typeId: '', status: '', powerType: '' })
const stats = reactive({ total: 0, online: 0, rate: '0.0', lowBattery: 0 })

const drawer = ref(false)
const saving = ref(false)
const emptyForm = () => ({
  id: null as number | null,
  name: '',
  deviceNum: '',
  typeId: 1,
  projectId: app.projectId || 1,
  powerType: 1,
  collectPeriod: 300,
  cmdInterval: 1,
  debugMode: 0,
  sortNo: 1,
  lng: 119.6408538,
  lat: 31.7625293,
  simExpire: '',
  installAt: '',
  searchLocation: '',
  remark: ''
})
const form = reactive<any>(emptyForm())

const cmdDlg = ref(false)
const current = ref<any>(null)
const cmd = reactive({ type: 'SET_PERIOD', payload: '{"period": 300}' })

const pages = computed(() => Math.max(1, Math.ceil(total.value / size)))
const pageList = computed(() => {
  const arr: number[] = []
  const from = Math.max(1, page.value - 2)
  for (let p = from; p <= Math.min(pages.value, from + 4); p++) arr.push(p)
  return arr
})

const typeName = (id: number) => deviceTypes.value.find((d) => Number(d.dictKey) === id)?.dictValue || '—'

async function load() {
  const data: any = await deviceApi.page({
    keyword: applied.keyword || undefined,
    typeId: applied.typeId || undefined,
    status: applied.status === '' ? undefined : applied.status,
    powerType: applied.powerType || undefined,
    projectId: app.projectId ?? undefined,
    page: page.value,
    size
  })
  list.value = data?.list || []
  total.value = data?.total || 0
}

async function loadStats() {
  const o: any = await dashboardApi.overview(app.projectId ?? undefined)
  stats.total = o.deviceTotal ?? 0
  stats.online = o.deviceOnline ?? 0
  stats.rate = o.deviceTotal ? ((o.deviceOnline / o.deviceTotal) * 100).toFixed(1) : '0.0'
  stats.lowBattery = o.batteryLow ?? 0
}

function search() {
  Object.assign(applied, filter)
  page.value = 1
  load()
}

function reset() {
  Object.assign(filter, { keyword: '', typeId: '', status: '', powerType: '' })
  search()
}

function goto(p: number) {
  if (p < 1 || p > pages.value) return
  page.value = p
  load()
}

function openEdit(d: any | null) {
  Object.assign(form, emptyForm(), d ? { ...d } : {})
  if (d) {
    form.simExpire = d.simExpire || ''
    form.installAt = d.installAt || ''
  }
  drawer.value = true
}

async function save() {
  if (!form.name || !form.deviceNum) {
    toast('设备名称与编号必填')
    return
  }
  saving.value = true
  const payload: any = { ...form }
  delete payload.id
  payload.simExpire = form.simExpire || null
  payload.installAt = form.installAt || null
  try {
    if (form.id) await deviceApi.update(form.id, payload)
    else await deviceApi.create(payload)
    drawer.value = false
    toast('已保存')
    await Promise.all([load(), loadStats()])
  } catch (e: any) {
    toast(errMsg(e, '保存失败'))
  } finally {
    saving.value = false
  }
}

function openCmd(d: any) {
  current.value = d
  cmdDlg.value = true
}

async function sendCmd() {
  try {
    const r: any = await deviceApi.command(current.value.id, cmd.type, cmd.payload)
    cmdDlg.value = false
    toast(`指令已提交，编号 ${r?.commandId ?? '—'}`)
  } catch (e: any) {
    toast(errMsg(e, '下发失败'))
  }
}

function goSensors(d: any) {
  router.push({ path: '/sensors', query: { deviceId: String(d.id), deviceNum: d.deviceNum } })
}

watch(
  () => app.projectId,
  () => {
    page.value = 1
    load()
    loadStats()
  }
)

onMounted(async () => {
  const dicts: any = await metaApi.dicts().catch(() => ({}))
  deviceTypes.value = dicts?.device_type || []
  await Promise.all([load(), loadStats()])
})
</script>
