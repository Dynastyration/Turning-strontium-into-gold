<template>
  <section class="view view--flush">
    <div class="dash">
      <div class="dash-head">
        <div class="dash-intro">
          <p>
            覆盖<template v-if="app.projects.length">{{ app.projects.map((p: any) => p.name).join('、') }}共 {{ app.projects.length }} 处</template><template v-else>桥梁</template>现场的采集终端与传感器，频率、应变、距离、温湿度等数据随设备上报实时入库，按阈值判定告警。
          </p>
        </div>
        <div class="dash-clock">
          <div class="time">{{ clockTime }}</div>
          <div class="date">{{ clockDate }}</div>
        </div>
      </div>

      <div class="kpi-grid">
        <div v-for="(it, i) in kpis" :key="it.label" class="kpi">
          <div class="kpi-top">
            <span class="kpi-label">{{ it.label }}</span>
            <span class="kpi-ico" :class="it.tone" v-html="it.icon" />
          </div>
          <div class="kpi-value"><span>{{ shown[i] }}</span><small>{{ it.unit }}</small></div>
          <div class="kpi-foot">{{ it.sub }}</div>
          <div class="kpi-foot" style="margin-top: 4px">{{ it.foot }}</div>
        </div>
      </div>

      <div class="dash-grid">
        <div class="dash-col">
          <div class="panel">
            <div class="panel-head">
              <div>
                <div class="panel-title">报警趋势（近 7 日）</div>
                <div class="panel-sub">合计 {{ trendTotal }} 条</div>
              </div>
              <span class="badge danger"><span class="dot" />今日 +{{ overview.alarmToday ?? 0 }}</span>
            </div>
            <div ref="trendEl" class="chart" v-html="trendSvg" />
          </div>
          <div class="panel">
            <div class="panel-head">
              <div>
                <div class="panel-title">传感器类型分布</div>
                <div class="panel-sub">共 {{ overview.sensorTotal ?? 0 }} 只</div>
              </div>
            </div>
            <div ref="typeEl" class="chart" v-html="typeSvg" />
            <div class="legend" style="margin-top: 10px">
              <span v-for="t in typeDist" :key="t.name"><i :style="{ background: t.color }" />{{ t.name }} {{ t.value }}</span>
            </div>
          </div>
        </div>

        <div class="panel dash-map-panel" style="display: flex; flex-direction: column">
          <div class="panel-head">
            <div>
              <div class="panel-title">监测点地理分布</div>
              <div class="panel-sub">{{ app.projectName }}</div>
            </div>
            <div class="legend">
              <span><i style="background: var(--ok)" />正常</span>
              <span><i style="background: var(--danger)" />报警</span>
              <span><i style="background: var(--muted)" />离线</span>
            </div>
          </div>
          <div class="map-body">
            <ChinaMap :markers="markers" />
          </div>
        </div>

        <div class="dash-col">
          <div class="panel">
            <div class="panel-head">
              <div>
                <div class="panel-title">设备在线率</div>
                <div class="panel-sub">共 {{ overview.deviceTotal ?? 0 }} 台，{{ overview.deviceOnline ?? 0 }} 台在线</div>
              </div>
            </div>
            <div ref="ringEl" class="chart" v-html="ringSvg" />
          </div>
          <div class="panel">
            <div class="panel-head">
              <div>
                <div class="panel-title">报警级别分布</div>
                <div class="panel-sub">未关闭 {{ overview.alarmOpen ?? 0 }} 条</div>
              </div>
            </div>
            <div ref="levelEl" class="chart" v-html="levelSvg" />
          </div>
        </div>
      </div>

      <div class="dash-bottom">
        <div class="panel" style="display: flex; flex-direction: column">
          <div class="panel-head">
            <div>
              <div class="panel-title">实时数据流</div>
              <div class="panel-sub">每 10 秒推送一次</div>
            </div>
            <span class="badge" :class="sseOk ? 'ok' : 'off'">
              <span class="dot" :class="sseOk ? 'ok' : 'off'" />{{ sseOk ? '已连接' : '重连中' }}
            </span>
          </div>
          <div class="ticker" style="flex: 1; min-height: 0">
            <div v-for="(r, i) in ticker" :key="i" class="ticker-row">
              <span class="t">{{ r.time }}</span>
              <span class="n">{{ r.name }}</span>
              <span class="v">{{ r.value }} {{ r.unit }}</span>
            </div>
            <div v-if="!ticker.length" class="muted" style="font-size: 12px; padding: 12px 0">等待上报数据…</div>
          </div>
        </div>

        <div class="panel" style="display: flex; flex-direction: column">
          <div class="panel-head">
            <div>
              <div class="panel-title">最新告警</div>
              <div class="panel-sub">最近 3 条</div>
            </div>
            <button class="btn btn-ghost btn-sm" @click="router.push('/alarms')">全部</button>
          </div>
          <div style="flex: 1">
            <div v-for="a in feed" :key="a.id" class="feed-item">
              <span class="level-bar" :class="a.level === 3 ? 'b1' : a.level === 2 ? 'b2' : 'b3'" />
              <div class="feed-body">
                <div class="feed-title">{{ a.content }}</div>
                <div class="feed-meta">{{ ALARM_LEVEL[a.level]?.[0] }}，{{ a.deviceName || '—' }}，{{ fmtTime(a.lastTs) }}</div>
              </div>
            </div>
            <div v-if="!feed.length" class="muted" style="font-size: 12px; padding: 12px 0">暂无未处理告警</div>
          </div>
        </div>

        <div class="panel">
          <div class="panel-head">
            <div>
              <div class="panel-title">视频监控</div>
              <div class="panel-sub">共 {{ videos.length }} 路，{{ onlineVideo }} 路在线</div>
            </div>
          </div>
          <div class="video-mini">
            <div v-for="v in videos.slice(0, 4)" :key="v.id" class="vm vm-link" @click="router.push('/video')">
              <div class="vm-bg" />
              <span class="live">
                <span v-if="v.online" class="badge danger" style="background: color-mix(in oklch, var(--danger) 85%, black); color: var(--accent-ink); font-size: 10px; height: 18px; padding: 0 7px">直播中</span>
              </span>
              <span class="cap"><span class="dot" :class="v.online ? 'ok' : 'off'" />{{ v.name }}</span>
            </div>
            <div v-if="!videos.length" class="muted" style="font-size: 12px">未配置视频通道</div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { dashboardApi, alarmApi, deviceApi, videoApi } from '@/api'
import { useAppStore } from '@/stores/app'
import ChinaMap from '@/components/ChinaMap.vue'
import type { MapMarker } from '@/utils/map'
import { ALARM_LEVEL, STATUS, fmtInt, fmtTime, fmtClock } from '@/utils/format'
import { areaChart, barH, donutChart, ringGauge, type Slice } from '@/utils/charts'

const app = useAppStore()
const router = useRouter()

const overview = reactive<Record<string, any>>({})
const trend = ref<{ l: string; v: number }[]>([])
const typeDist = ref<Slice[]>([])
const markers = ref<MapMarker[]>([])
const levels = ref<Slice[]>([])
const ticker = ref<{ time: string; name: string; value: string; unit: string }[]>([])
const feed = ref<any[]>([])
const videos = ref<any[]>([])
const sseOk = ref(false)

const ICONS = {
  server: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><rect x="3" y="4" width="18" height="7" rx="1.5"/><rect x="3" y="13" width="18" height="7" rx="1.5"/><path d="M7 7.5h.01M7 16.5h.01"/></svg>',
  wave: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M2 12h3l2-6 4 12 3-9 2 3h6"/></svg>',
  bell: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M6 9a6 6 0 1 1 12 0c0 5 2 6 2 6H4s2-1 2-6"/><path d="M10 19a2 2 0 0 0 4 0"/></svg>',
  cam: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><rect x="2.5" y="6" width="13" height="12" rx="2"/><path d="M15.5 10.5l6-3.5v10l-6-3.5"/></svg>',
  db: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><ellipse cx="12" cy="5.5" rx="7.5" ry="3"/><path d="M4.5 5.5v6c0 1.66 3.36 3 7.5 3s7.5-1.34 7.5-3v-6"/><path d="M4.5 11.5v6c0 1.66 3.36 3 7.5 3s7.5-1.34 7.5-3v-6"/></svg>',
  bat: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><rect x="2" y="8" width="17" height="8" rx="1.5"/><path d="M21 11v2"/><path d="M6 11.5v1"/></svg>'
}

const kpis = computed(() => {
  const o = overview
  const onlineRate = o.deviceTotal ? ((o.deviceOnline / o.deviceTotal) * 100).toFixed(1) : '0.0'
  return [
    { label: '设备总数', value: o.deviceTotal ?? 0, unit: '台', sub: `在线 ${o.deviceOnline ?? 0} 台，在线率 ${onlineRate}%`, icon: ICONS.server, tone: 'info', foot: `离线 ${(o.deviceTotal ?? 0) - (o.deviceOnline ?? 0)} 台` },
    { label: '传感器总数', value: o.sensorTotal ?? 0, unit: '只', sub: `在线 ${o.sensorOnline ?? 0}`, icon: ICONS.wave, tone: 'ok', foot: `接入 ${typeDist.value.length} 种类型` },
    { label: '报警', value: o.alarmOpen ?? 0, unit: '条', sub: `今日新增 ${o.alarmToday ?? 0} 条，累计 ${fmtInt(o.alarmTotal ?? 0)} 条`, icon: ICONS.bell, tone: 'danger', foot: `未关闭率 ${o.alarmTotal ? ((o.alarmOpen / o.alarmTotal) * 100).toFixed(1) : '0.0'}%` },
    { label: '视频通道', value: o.monitorTotal ?? 0, unit: '路', sub: `在线 ${o.monitorOnline ?? 0}`, icon: ICONS.cam, tone: 'info', foot: `离线 ${(o.monitorTotal ?? 0) - (o.monitorOnline ?? 0)} 路` },
    { label: '今日数据量', value: o.readingToday ?? 0, unit: '行', sub: '今日 00:00 起入库', icon: ICONS.db, tone: 'info', foot: `更新 ${fmtClock(o.updatedAt)}` },
    { label: '低电量设备', value: o.batteryLow ?? 0, unit: '台', sub: '电量低于 20%', icon: ICONS.bat, tone: 'warn', foot: `占设备总数 ${o.deviceTotal ? ((o.batteryLow / o.deviceTotal) * 100).toFixed(1) : '0.0'}%` }
  ]
})

// 数字滚动：只在目标值真的变了才动，从上一个数滚到新数，避免 SSE 每 10 秒把数字刷回 0
const shown = ref<string[]>(['0', '0', '0', '0', '0', '0'])
let anims: number[] = []
function runCountUp() {
  const targets = kpis.value.map((it) => Number(it.value) || 0)
  const from = shown.value.map((s) => Number(String(s).replace(/,/g, '')) || 0)
  if (targets.every((t, i) => t === from[i])) return
  anims.forEach((id) => cancelAnimationFrame(id))
  anims = []
  const dur = 900
  const start = performance.now()
  for (let i = 0; i < targets.length; i++) {
    const a = from[i]
    const b = targets[i]
    const step = (now: number) => {
      const p = Math.min(1, (now - start) / dur)
      const e = 1 - Math.pow(1 - p, 3)
      shown.value[i] = fmtInt(Math.round(a + (b - a) * e))
      if (p < 1) anims[i] = requestAnimationFrame(step)
    }
    anims[i] = requestAnimationFrame(step)
  }
}

// 四张图都按容器实际尺寸绘制：一屏模式下这块区域高度只有几十像素，
// 固定 viewBox 会被整体等比缩小，刻度文字小到看不清
const trendEl = ref<HTMLElement | null>(null)
const typeEl = ref<HTMLElement | null>(null)
const ringEl = ref<HTMLElement | null>(null)
const levelEl = ref<HTMLElement | null>(null)

/** 跟着容器尺寸走，最小给到还能画出内容的大小。 */
function useBox(el: typeof trendEl) {
  const box = reactive({ w: 300, h: 200 })
  let ro: ResizeObserver | null = null
  onMounted(() => {
    if (!el.value) return
    ro = new ResizeObserver(([entry]) => {
      const r = entry.contentRect
      box.w = Math.max(120, Math.round(r.width))
      box.h = Math.max(40, Math.round(r.height))
    })
    ro.observe(el.value)
  })
  onUnmounted(() => ro?.disconnect())
  return box
}

const trendBox = useBox(trendEl)
const typeBox = useBox(typeEl)
const ringBox = useBox(ringEl)
const levelBox = useBox(levelEl)

const trendSvg = computed(() => areaChart(trend.value, { w: trendBox.w, h: trendBox.h, gid: 'agTrend', int: true }))
const trendTotal = computed(() => trend.value.reduce((s, d) => s + (Number(d.v) || 0), 0))
const typeTotal = computed(() => typeDist.value.reduce((s, d) => s + d.value, 0))
const typeSvg = computed(() => donutChart(typeDist.value, typeTotal.value, fmtInt(typeTotal.value), '', { w: typeBox.w, h: typeBox.h }))

const ringPct = computed(() => (overview.deviceTotal ? (overview.deviceOnline / overview.deviceTotal) * 100 : 0))
const ringSvg = computed(() =>
  ringGauge(Number(ringPct.value.toFixed(1)), ringPct.value.toFixed(1) + '%', '设备在线率', { w: ringBox.w, h: ringBox.h })
)
const levelSvg = computed(() => barH(levels.value, { w: levelBox.w, h: levelBox.h }))
const onlineVideo = computed(() => videos.value.filter((v) => v.online === 1).length)

const clockTime = ref('--:--:--')
const clockDate = ref('----')
function tick() {
  const n = new Date()
  clockTime.value = n.toLocaleTimeString('zh-CN', { hour12: false })
  clockDate.value = n.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', weekday: 'short' })
}

const PALETTE = ['var(--c1)', 'var(--c2)', 'var(--c3)', 'var(--c4)', 'var(--c5)']

async function loadAll() {
  const pid = app.projectId ?? undefined
  const [ov, tr, td, geo, lv, latest, al, vd] = await Promise.allSettled([
    dashboardApi.overview(pid),
    dashboardApi.trend(7),
    dashboardApi.typeDist(pid),
    deviceApi.geo(pid),
    alarmApi.stat('level'),
    dashboardApi.latest(4),
    alarmApi.page({ page: 1, size: 3, status: 0, projectId: pid }),
    videoApi.list({ projectId: pid })
  ])

  if (ov.status === 'fulfilled') {
    Object.assign(overview, ov.value || {})
    runCountUp()
  }
  if (tr.status === 'fulfilled') {
    trend.value = ((tr.value as any[]) || []).map((r) => ({
      l: String(r.day).slice(5),
      v: Number(r.alarmCount) || 0
    }))
  }
  if (td.status === 'fulfilled') {
    typeDist.value = ((td.value as any[]) || []).map((r, i) => ({
      name: r.name,
      value: Number(r.cnt) || 0,
      color: PALETTE[i % PALETTE.length]
    }))
  }
  if (geo.status === 'fulfilled') {
    markers.value = ((geo.value as any[]) || [])
      .filter((d) => d.lng != null && d.lat != null)
      .map((d) => ({
        lng: Number(d.lng),
        lat: Number(d.lat),
        name: d.name,
        meta: `${d.deviceNum}，${STATUS[d.runStatus]?.[0] || '—'}，${d.sensorCount ?? 0} 只传感器`,
        status: d.runStatus === 1 ? 'ok' : d.runStatus === 2 ? 'danger' : 'off'
      })) as MapMarker[]
  }
  if (lv.status === 'fulfilled') {
    const map: Record<number, Slice> = {
      3: { name: '严重', value: 0, color: 'var(--danger)' },
      2: { name: '警告', value: 0, color: 'var(--warn)' },
      1: { name: '提示', value: 0, color: 'var(--info)' }
    }
    for (const r of (lv.value as any[]) || []) {
      const m = map[Number(r.level)]
      if (m) m.value = Number(r.cnt) || 0
    }
    levels.value = [map[3], map[2], map[1]]
  }
  if (latest.status === 'fulfilled') {
    ticker.value = ((latest.value as any[]) || []).map((r) => ({
      time: fmtClock(r.ts),
      name: `${r.sensorName} ${r.fieldName}`,
      value: r.value == null ? '—' : Number(r.value).toFixed(2),
      unit: r.unit || ''
    }))
  }
  if (al.status === 'fulfilled') {
    feed.value = ((al.value as any)?.list || []) as any[]
  }
  if (vd.status === 'fulfilled') {
    videos.value = (vd.value as any[]) || []
  }
}

let es: EventSource | null = null
let retry = 0
let retryTimer = 0

function connect() {
  const token = localStorage.getItem('bn_token') || ''
  const pid = app.projectId ? `&projectId=${app.projectId}` : ''
  es?.close()
  es = new EventSource(`/api/v1/stream?token=${encodeURIComponent(token)}${pid}`)
  es.addEventListener('overview', (e: MessageEvent) => {
    sseOk.value = true
    retry = 0
    Object.assign(overview, JSON.parse(e.data))
    runCountUp()
  })
  es.onopen = () => {
    sseOk.value = true
    retry = 0
  }
  es.onerror = () => {
    sseOk.value = false
    es?.close()
    retry += 1
    const wait = Math.min(30000, 2000 * retry)
    retryTimer = window.setTimeout(connect, wait)
  }
}

let clockTimer = 0
let pollTimer = 0

onMounted(() => {
  tick()
  clockTimer = window.setInterval(tick, 1000)
  loadAll()
  connect()
  pollTimer = window.setInterval(loadAll, 30000)
})

onUnmounted(() => {
  window.clearInterval(clockTimer)
  window.clearInterval(pollTimer)
  window.clearTimeout(retryTimer)
  anims.forEach((id) => cancelAnimationFrame(id))
  es?.close()
})

watch(() => app.projectId, () => {
  loadAll()
  connect()
})
</script>
