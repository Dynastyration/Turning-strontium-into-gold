<template>
  <section class="view">
    <div class="page-head">
      <div>
        <div class="page-title">告警管理</div>
        <div class="page-desc">传感器的值碰到阈值就生成告警，确认或关闭都在这页完成。</div>
      </div>
      <div style="display: flex; gap: 10px">
        <button class="btn btn-secondary" @click="openConfigs">报警配置</button>
        <button class="btn btn-primary" @click="openRules">生效规则</button>
      </div>
    </div>

    <div class="stat-strip">
      <div class="stat-cell">
        <div class="k">未关闭告警</div>
        <div class="v">{{ overview.alarmOpen ?? 0 }}</div>
        <div class="d">条</div>
      </div>
      <div class="stat-cell">
        <div class="k">严重</div>
        <div class="v" style="color: var(--danger)">{{ levelCount(3) }}</div>
        <div class="d">条</div>
      </div>
      <div class="stat-cell">
        <div class="k">警告</div>
        <div class="v" style="color: var(--warn)">{{ levelCount(2) }}</div>
        <div class="d">条</div>
      </div>
      <div class="stat-cell">
        <div class="k">提示</div>
        <div class="v">{{ levelCount(1) }}</div>
        <div class="d">条</div>
      </div>
      <div class="stat-cell">
        <div class="k">今日新增</div>
        <div class="v">{{ overview.alarmToday ?? 0 }}</div>
        <div class="d">条</div>
      </div>
    </div>

    <div class="filterbar">
      <div class="field">
        <label>级别</label>
        <select v-model="filter.level" class="select">
          <option value="">全部</option>
          <option value="3">严重</option>
          <option value="2">警告</option>
          <option value="1">提示</option>
        </select>
      </div>
      <div class="field">
        <label>状态</label>
        <select v-model="filter.status" class="select">
          <option value="">全部</option>
          <option value="0">触发中</option>
          <option value="1">已确认</option>
          <option value="2">已恢复</option>
          <option value="3">已关闭</option>
        </select>
      </div>
      <div class="field grow">
        <label>关键词（只筛本页结果）</label>
        <input v-model="filter.kw" class="input" placeholder="如 倾角、索力" />
      </div>
      <button class="btn btn-primary" @click="search">查询</button>
      <button class="btn btn-ghost" @click="reset">重置</button>
    </div>

    <div class="panel">
      <div class="tbl-wrap">
        <table class="tbl">
          <thead>
            <tr><th>级别</th><th>告警内容</th><th>位置</th><th>规则</th><th>状态</th><th>触发值</th><th>首次触发</th><th>操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="a in rows" :key="a.id">
              <td><StatusBadge :text="ALARM_LEVEL[a.level]?.[0] || '—'" :tone="ALARM_LEVEL[a.level]?.[1] || 'off'" /></td>
              <td style="white-space: normal; min-width: 280px">{{ a.content }}</td>
              <td class="muted">{{ a.deviceName || a.projectName || '—' }}</td>
              <td class="muted">{{ RULE_TEXT[a.ruleType] || '—' }}</td>
              <td><StatusBadge :text="ALARM_STATUS[a.status]?.[0] || '—'" :tone="ALARM_STATUS[a.status]?.[1] || 'off'" /></td>
              <td class="mono">{{ a.triggerVal == null ? '—' : Number(a.triggerVal).toFixed(2) + (a.unit || '') }}</td>
              <td class="mono muted">{{ fmtTime(a.firstTs) }}</td>
              <td style="white-space: nowrap">
                <button v-if="a.status === 0" class="btn btn-ghost btn-sm" @click="ack(a)">确认</button>
                <button v-if="a.status < 2" class="btn btn-ghost btn-sm" @click="close(a)">关闭</button>
              </td>
            </tr>
            <tr v-if="!rows.length"><td colspan="8" class="muted" style="text-align: center; padding: 32px">没有匹配的告警</td></tr>
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

    <AppDialog v-model="cfgDlg" title="报警配置" wide>
      <table class="tbl">
        <thead><tr><th>名称</th><th>规则</th><th>阈值</th><th>级别</th><th>抑制</th><th>启用</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="c in configs" :key="c.id">
            <td>{{ c.name }}</td>
            <td>
              <div>{{ RULE_TEXT[c.ruleType] || c.ruleType }}</div>
              <div v-if="c.ruleType > 3" class="muted" style="font-size: 11px">未接入判定</div>
            </td>
            <td class="mono">{{ c.threshold }}{{ c.threshold2 != null ? ' ~ ' + c.threshold2 : '' }}</td>
            <td><StatusBadge :text="ALARM_LEVEL[c.level]?.[0] || '—'" :tone="ALARM_LEVEL[c.level]?.[1] || 'off'" /></td>
            <td class="mono muted">{{ c.silenceSec }}s</td>
            <td>
              <span class="switch" :class="{ on: c.enabled === 1 }" @click="toggleConfig(c)" />
            </td>
            <td><button class="btn btn-ghost btn-sm" @click="removeConfig(c)">删除</button></td>
          </tr>
          <tr v-if="!configs.length"><td colspan="7" class="muted">还没有报警配置</td></tr>
        </tbody>
      </table>
      <div class="form-grid" style="margin-top: 16px">
        <div class="field full">
          <label>新规则名称</label>
          <input v-model="cfgForm.name" class="input" placeholder="如 距离上限 150mm" />
        </div>
        <div class="field">
          <label>规则类型</label>
          <select v-model.number="cfgForm.ruleType" class="select">
            <option :value="1">上限</option>
            <option :value="2">下限</option>
            <option :value="3">区间外</option>
          </select>
        </div>
        <div class="field">
          <label>级别</label>
          <select v-model.number="cfgForm.level" class="select">
            <option :value="3">严重</option>
            <option :value="2">警告</option>
            <option :value="1">提示</option>
          </select>
        </div>
        <div class="field">
          <label>阈值</label>
          <input v-model.number="cfgForm.threshold" class="input num" />
        </div>
        <div class="field">
          <label>区间上沿</label>
          <input v-model.number="cfgForm.threshold2" class="input num" />
        </div>
        <div class="field">
          <label>抑制时间（秒）</label>
          <input v-model.number="cfgForm.silenceSec" class="input num" />
        </div>
        <div class="field">
          <label>比对字段</label>
          <select v-model.number="cfgForm.fieldId" class="select">
            <option :value="null" disabled>请选择</option>
            <option v-for="o in fieldOptions" :key="o.id" :value="o.id">{{ o.text }}</option>
          </select>
        </div>
      </div>
      <template #foot>
        <button class="btn btn-ghost" @click="cfgDlg = false">关闭</button>
        <button class="btn btn-primary" @click="addConfig">新增规则</button>
      </template>
    </AppDialog>

    <AppDialog v-model="ruleDlg" title="生效中的规则">
      <div style="font-size: 13px; color: var(--muted); margin-bottom: 14px">
        数据上报时逐条比对阈值。同一条规则在抑制时间内反复触发，只记一条告警、次数累加。下面只列已经能判定的三类规则，变化率、离线、低电量、SIM 到期还没做。
      </div>
      <table class="tbl">
        <thead><tr><th>规则</th><th>类型</th><th>阈值</th><th>级别</th></tr></thead>
        <tbody>
          <tr v-for="c in configs.filter((x) => x.enabled === 1 && x.ruleType <= 3)" :key="c.id">
            <td>{{ c.name }}</td>
            <td class="muted">{{ RULE_TEXT[c.ruleType] }}</td>
            <td class="mono">{{ c.threshold }}{{ c.threshold2 != null ? ' ~ ' + c.threshold2 : '' }}</td>
            <td>{{ ALARM_LEVEL[c.level]?.[0] }}</td>
          </tr>
          <tr v-if="!configs.filter((x) => x.enabled === 1 && x.ruleType <= 3).length"><td colspan="4" class="muted">没有启用中的规则</td></tr>
        </tbody>
      </table>
      <template #foot>
        <button class="btn btn-ghost" @click="ruleDlg = false">关闭</button>
      </template>
    </AppDialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { alarmApi, dashboardApi, metaApi } from '@/api'
import { useAppStore } from '@/stores/app'
import AppDialog from '@/components/AppDialog.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { ALARM_LEVEL, ALARM_STATUS, RULE_TEXT, fmtTime, toast, errMsg } from '@/utils/format'

const app = useAppStore()

const rows = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const overview = reactive<Record<string, any>>({})
const levelStat = ref<any[]>([])
const filter = reactive({ level: '', status: '', kw: '' })
const applied = reactive({ level: '', status: '' })

const cfgDlg = ref(false)
const ruleDlg = ref(false)
const configs = ref<any[]>([])
const cfgForm = reactive({ name: '', ruleType: 1, level: 2, threshold: 0, threshold2: null as number | null, silenceSec: 600, fieldId: null as number | null })

// 规则比对的是 sensor_field 的具体字段，这里按传感器类型把字段摊平给下拉用
const fieldOptions = ref<{ id: number; text: string }[]>([])

async function loadFieldOptions() {
  if (fieldOptions.value.length) return
  const types: any[] = ((await metaApi.sensorTypes().catch(() => [])) as any[]) || []
  fieldOptions.value = types.flatMap((t) =>
    (t.fields || []).map((f: any) => ({
      id: f.id,
      text: `${t.name}的${f.name}${f.unit ? '（' + f.unit + '）' : ''}`
    }))
  )
  if (cfgForm.fieldId == null && fieldOptions.value.length) cfgForm.fieldId = fieldOptions.value[0].id
}

const pages = computed(() => Math.max(1, Math.ceil(total.value / size)))
const pageList = computed(() => {
  const arr: number[] = []
  const from = Math.max(1, page.value - 2)
  for (let p = from; p <= Math.min(pages.value, from + 4); p++) arr.push(p)
  return arr
})

const shown = computed(() => {
  const kw = filter.kw.trim().toLowerCase()
  if (!kw) return rows.value
  return rows.value.filter((a) => `${a.content}${a.sensorName}${a.deviceName}`.toLowerCase().includes(kw))
})

const levelCount = (lv: number) => {
  const hit = levelStat.value.find((x) => Number(x.level) === lv)
  return hit ? Number(hit.cnt) : 0
}

async function load() {
  const data: any = await alarmApi.page({
    level: applied.level || undefined,
    status: applied.status === '' ? undefined : applied.status,
    projectId: app.projectId ?? undefined,
    page: page.value,
    size
  })
  rows.value = data?.list || []
  total.value = data?.total || 0
}

async function loadStats() {
  const [ov, st] = await Promise.allSettled([
    dashboardApi.overview(app.projectId ?? undefined),
    alarmApi.stat('level')
  ])
  if (ov.status === 'fulfilled') Object.assign(overview, ov.value || {})
  if (st.status === 'fulfilled') levelStat.value = (st.value as any[]) || []
}

function search() {
  Object.assign(applied, { level: filter.level, status: filter.status })
  page.value = 1
  load()
}

function reset() {
  filter.level = ''
  filter.status = ''
  filter.kw = ''
  search()
}

function goto(p: number) {
  if (p < 1 || p > pages.value) return
  page.value = p
  load()
}

async function ack(a: any) {
  try {
    await alarmApi.ack(a.id)
    toast('已确认')
    await Promise.all([load(), loadStats()])
    app.loadUnread()
  } catch (e: any) {
    toast(errMsg(e, '操作失败'))
  }
}

async function close(a: any) {
  try {
    await alarmApi.close(a.id)
    toast('已关闭')
    await Promise.all([load(), loadStats()])
    app.loadUnread()
  } catch (e: any) {
    toast(errMsg(e, '操作失败'))
  }
}

async function loadConfigs() {
  configs.value = ((await alarmApi.configs().catch(() => [])) as any[]) || []
}

async function openConfigs() {
  cfgDlg.value = true
  await Promise.all([loadConfigs(), loadFieldOptions()])
}

async function openRules() {
  ruleDlg.value = true
  await loadConfigs()
}

async function toggleConfig(c: any) {
  try {
    await alarmApi.updateConfig(c.id, { ...c, enabled: c.enabled === 1 ? 0 : 1 })
    await loadConfigs()
  } catch (e: any) {
    toast(errMsg(e, '更新失败'))
  }
}

async function removeConfig(c: any) {
  try {
    await alarmApi.deleteConfig(c.id)
    await loadConfigs()
    toast('已删除')
  } catch (e: any) {
    toast(errMsg(e, '删除失败'))
  }
}

async function addConfig() {
  if (!cfgForm.name) {
    toast('请填写规则名称')
    return
  }
  if (cfgForm.fieldId == null) {
    toast('请选择比对字段')
    return
  }
  if (cfgForm.ruleType === 3 && cfgForm.threshold2 == null) {
    toast('区间外规则需要填写阈值 2')
    return
  }
  try {
    await alarmApi.createConfig({ ...cfgForm, targetType: 2, enabled: 1, createdAt: null })
    toast('规则已新增')
    await loadConfigs()
  } catch (e: any) {
    toast(errMsg(e, '新增失败'))
  }
}

watch(
  () => app.projectId,
  () => {
    page.value = 1
    load()
    loadStats()
  }
)

onMounted(() => {
  load()
  loadStats()
})
</script>
