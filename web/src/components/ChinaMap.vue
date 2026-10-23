<template>
  <div class="map-wrap">
    <div ref="leadRef" class="map-svg" v-html="svg" />
    <svg class="map-svg" :viewBox="MAP_VIEWBOX" preserveAspectRatio="xMidYMid meet">
      <defs>
        <radialGradient id="bnMapGlow">
          <stop offset="0%" stop-color="var(--accent)" stop-opacity=".42" />
          <stop offset="100%" stop-color="var(--accent)" stop-opacity="0" />
        </radialGradient>
      </defs>
      <circle v-if="callout" :cx="callout.x" :cy="callout.y" r="46" fill="url(#bnMapGlow)" />
      <g class="map-callout" v-if="callout && label">
        <line :x1="callout.x" :y1="callout.y - 13" :x2="callout.x" :y2="callout.y - 27" />
        <text :x="callout.x" :y="callout.y - 33">{{ label }}</text>
      </g>
      <g class="map-markers">
        <g
          v-for="p in placed"
          :key="p.key"
          class="map-marker"
          :class="{ on: hover === p.key }"
          :transform="`translate(${(p.x + p.dx).toFixed(1)},${(p.y + p.dy).toFixed(1)})`"
          @mouseenter="hover = p.key"
          @mouseleave="hover = ''"
        >
          <circle class="hit" r="11" />
          <circle class="halo" r="9.5" />
          <circle class="pulse" r="8" :fill="color(p.status)" opacity=".45" />
          <circle class="core" r="4" :fill="color(p.status)" />
        </g>
      </g>
      <!-- 悬停提示：设备名 + 编号、状态、传感器数 -->
      <g v-if="tip" class="map-tip">
        <rect :x="tip.left" :y="tip.top" :width="tip.w" height="40" rx="7" />
        <circle :cx="tip.left + 12" :cy="tip.top + 14" r="3.2" :fill="color(tip.status)" />
        <text class="tip-name" :x="tip.left + 21" :y="tip.top + 17">{{ tip.name }}</text>
        <text class="tip-meta" :x="tip.left + 21" :y="tip.top + 32">{{ tip.meta }}</text>
      </g>
    </svg>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { MAP_BOUNDS, MAP_VIEWBOX, mapProj, type MapMarker } from '@/utils/map'

const props = withDefaults(defineProps<{ markers: MapMarker[]; label?: string }>(), { label: '' })

const svg = ref('')
const leadRef = ref<HTMLDivElement>()
const hover = ref('')
let cache = ''

const color = (s: string) => (s === 'danger' ? 'var(--danger)' : s === 'off' ? 'var(--muted)' : 'var(--ok)')

// 设备坐标可能只差几米，投影后落在同一像素：按 6 像素网格分组，重合的沿小圆散开。
const placed = computed(() => {
  const list = props.markers.map((m, i) => {
    const [x, y] = mapProj(Number(m.lng), Number(m.lat))
    return { key: `${m.name}-${i}`, name: m.name, meta: m.meta || '', status: m.status, x, y, dx: 0, dy: 0 }
  })
  const groups = new Map<string, typeof list>()
  for (const p of list) {
    const k = `${Math.round(p.x / 6)}:${Math.round(p.y / 6)}`
    const g = groups.get(k) || []
    g.push(p)
    groups.set(k, g)
  }
  for (const g of groups.values()) {
    if (g.length < 2) continue
    g.forEach((p, i) => {
      const a = (Math.PI * 2 * i) / g.length - Math.PI / 2
      p.dx = Math.cos(a) * 6
      p.dy = Math.sin(a) * 6
    })
  }
  return list
})

// 项目名挂在点位簇的正中上方，簇太靠上时把标注压回画面内
const callout = computed(() => {
  const list = placed.value
  if (!list.length) return null
  const x = list.reduce((s, p) => s + p.x + p.dx, 0) / list.length
  const y = list.reduce((s, p) => s + p.y + p.dy, 0) / list.length
  return { x, y: Math.max(y, 44) }
})

const active = computed(() => placed.value.find((p) => p.key === hover.value) || null)

// 提示框在 SVG 坐标系里画，不用把页面坐标换回经纬度；靠边时往画面内收，靠北时翻到点位下方。
const tip = computed(() => {
  const p = active.value
  if (!p) return null
  const name = p.name || ''
  const meta = p.meta || ''
  const w = Math.max(112, Math.min(250, 30 + Math.max(name.length * 12, meta.length * 10.5)))
  const x = p.x + p.dx
  const left = Math.min(MAP_BOUNDS.maxX - w - 4, Math.max(MAP_BOUNDS.minX + 4, x - w / 2))
  const top = p.y + p.dy < 90 ? p.y + p.dy + 13 : p.y + p.dy - 52
  return { left, top, w, name, meta, status: p.status }
})

// 点位压住省名时把该省名隐去；定位标注所占的矩形也一并让开，避免两层文字叠字
function maskLabels() {
  const root = leadRef.value
  if (!root) return
  const c = callout.value
  const text = props.label || ''
  const box = c && text
    ? { x1: c.x - (text.length * 13) / 2 - 8, x2: c.x + (text.length * 13) / 2 + 8, y1: c.y - 48, y2: c.y - 22 }
    : null
  for (const t of root.querySelectorAll<SVGTextElement>('.map-label')) {
    const tx = Number(t.getAttribute('x')) || 0
    const ty = (Number(t.getAttribute('y')) || 0) - 4
    const covered =
      placed.value.some((p) => Math.hypot(p.x + p.dx - tx, p.y + p.dy - ty) < 16) ||
      (!!box && tx > box.x1 && tx < box.x2 && ty > box.y1 && ty < box.y2)
    t.style.opacity = covered ? '0' : ''
  }
}

watch([placed, svg, () => props.label], () => nextTick(maskLabels))

onMounted(async () => {
  if (!cache) {
    const res = await fetch('/china-map.svg')
    cache = await res.text()
  }
  svg.value = cache
  await nextTick()
  maskLabels()
})
</script>
