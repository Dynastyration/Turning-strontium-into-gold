<template>
  <section class="view">
    <div class="page-head">
      <div>
        <div class="page-title">视频监控</div>
        <div class="page-desc">现场摄像头的通道列表，在线的那几路点播放就能看到实时画面。</div>
      </div>
    </div>

    <div class="video-grid">
      <div v-for="v in list" :key="v.id" class="video-tile">
        <div class="stage">
          <video v-if="playing[v.id]" :ref="(el) => setVideoRef(v.id, el)" muted autoplay playsinline />
          <template v-else>
            <div v-if="v.online === 1" class="vm-bg" />
            <div v-else class="no-signal">无信号（{{ v.gbDeviceId || v.id }}）</div>
            <span v-if="v.online === 1" style="position: absolute; right: 10px; top: 10px; z-index: 2">
              <span class="badge danger" style="background: color-mix(in oklch, var(--danger) 85%, black); color: var(--accent-ink); font-size: 10px; height: 18px; padding: 0 7px">直播中</span>
            </span>
            <button
              v-if="v.online === 1"
              class="btn btn-secondary btn-sm"
              style="position: absolute; left: 50%; top: 50%; transform: translate(-50%, -50%); z-index: 3"
              @click="play(v)"
            >
              播放
            </button>
          </template>
        </div>
        <div class="meta">
          <div>
            <div class="nm">{{ v.name }}</div>
            <div class="sub">{{ PROTOCOL_TEXT[v.protocol] || 'GB28181' }}，通道 {{ v.channelNo || '—' }}</div>
            <div class="sub">{{ v.lastHbAt ? '心跳 ' + fmtClock(v.lastHbAt) : '暂无心跳' }}</div>
          </div>
          <StatusBadge v-if="v.online === 1" text="在线" tone="ok" dot />
          <StatusBadge v-else text="离线" tone="off" dot />
        </div>
      </div>
      <div v-if="!list.length" class="muted" style="padding: 32px; font-size: 13px">还没有配置视频通道</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import flvjs from 'flv.js'
import { videoApi } from '@/api'
import { useAppStore } from '@/stores/app'
import StatusBadge from '@/components/StatusBadge.vue'
import { PROTOCOL_TEXT, fmtClock, toast, errMsg } from '@/utils/format'

const app = useAppStore()
const list = ref<any[]>([])
const playing = reactive<Record<number, boolean>>({})
const refs: Record<number, HTMLVideoElement | null> = {}
const players: Record<number, any> = {}

function setVideoRef(id: number, el: any) {
  refs[id] = el as HTMLVideoElement | null
}

async function load() {
  list.value = ((await videoApi.list({ projectId: app.projectId ?? undefined }).catch(() => [])) as any[]) || []
}

async function play(v: any) {
  try {
    const r: any = await videoApi.play(v.id)
    if (!r?.playUrl) {
      toast('该通道没有可用播放地址')
      return
    }
    playing[v.id] = true
    await new Promise((res) => setTimeout(res, 30))
    const el = refs[v.id]
    if (!el) return
    if (flvjs.isSupported() && String(r.playUrl).includes('.flv')) {
      const p = flvjs.createPlayer({ type: 'flv', url: r.playUrl, isLive: true })
      p.attachMediaElement(el)
      p.load()
      p.play().catch(() => toast('自动播放被浏览器拦截，请再次点击'))
      players[v.id] = p
    } else {
      el.src = r.playUrl
      el.play().catch(() => toast('当前浏览器不支持该流地址'))
    }
  } catch (e: any) {
    playing[v.id] = false
    toast(errMsg(e, '播放失败'))
  }
}

function destroyAll() {
  Object.values(players).forEach((p) => {
    try {
      p.pause()
      p.unload()
      p.detachMediaElement()
      p.destroy()
    } catch {
      /* 播放器已释放 */
    }
  })
  Object.keys(players).forEach((k) => delete players[Number(k)])
}

watch(() => app.projectId, load)

onMounted(load)
onUnmounted(destroyAll)
</script>
