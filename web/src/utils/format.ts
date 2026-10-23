export const STATUS: Record<number, [string, string]> = {
  0: ['离线', 'off'],
  1: ['在线', 'ok'],
  2: ['故障', 'danger'],
  3: ['调试中', 'info']
}

export const SNS_STATUS: Record<number, [string, string]> = {
  0: ['离线', 'off'],
  1: ['正常', 'ok'],
  2: ['报警中', 'danger'],
  3: ['停用', 'off']
}

export const ALARM_LEVEL: Record<number, [string, string]> = {
  1: ['提示', 'info'],
  2: ['警告', 'warn'],
  3: ['严重', 'danger']
}

export const ALARM_STATUS: Record<number, [string, string]> = {
  0: ['触发中', 'danger'],
  1: ['已确认', 'info'],
  2: ['已恢复', 'ok'],
  3: ['已关闭', 'off']
}

export const REPORT_STATUS: Record<number, [string, string]> = {
  0: ['排队', 'off'],
  1: ['生成中', 'info'],
  2: ['成功', 'ok'],
  3: ['失败', 'danger']
}

export const RULE_TEXT: Record<number, string> = {
  1: '上限',
  2: '下限',
  3: '区间外',
  4: '变化率',
  5: '离线',
  6: '低电量',
  7: 'SIM到期'
}

export const PROTOCOL_TEXT: Record<number, string> = {
  1: 'GB28181',
  2: 'HTTP-FLV',
  3: 'HLS',
  4: 'RTSP'
}

export const fmtInt = (n: number | null | undefined): string =>
  n == null ? '—' : Number(n).toLocaleString('zh-CN')

export const fmtNum = (n: number | null | undefined, d = 2): string =>
  n == null ? '—' : Number(n).toFixed(d)

/** 后端把粒度统一换算成分钟（如 60m），界面上说成「1 小时」更好读。 */
export function stepLabel(v: string | null | undefined): string {
  const m = Number(String(v || '').replace(/m$/, ''))
  if (!m) return '—'
  return m % 60 === 0 ? `${m / 60} 小时` : `${m} 分钟`
}

/** 后端返回 ISO 时间串，统一成分钟精度，避免每处各写一遍截取。 */
export function fmtTime(v: string | null | undefined, keepSec = false): string {
  if (!v) return '—'
  const s = String(v).replace('T', ' ')
  return keepSec ? s.slice(0, 19) : s.slice(0, 16)
}

export function fmtClock(v: string | null | undefined): string {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(11, 19)
}

export function batteryTone(b: number | null | undefined): string {
  if (b == null) return 'var(--muted)'
  return b < 20 ? 'var(--warn)' : b < 40 ? 'var(--c4)' : 'var(--ok)'
}

let toastEl: HTMLElement | null = null
let toastTimer = 0

/** 轻提示：与设计稿同样的底部胶囊，避免引入组件库。 */
export function toast(msg: string) {
  if (!toastEl) {
    toastEl = document.createElement('div')
    toastEl.id = 'toast'
    toastEl.style.cssText =
      'position:fixed;left:50%;bottom:32px;transform:translateX(-50%);background:var(--fg);color:var(--bg);' +
      'padding:10px 18px;border-radius:10px;font-size:13px;z-index:100;opacity:0;transition:opacity .25s ease;' +
      'box-shadow:0 8px 30px rgba(0,0,0,.25);pointer-events:none;'
    document.body.appendChild(toastEl)
  }
  toastEl.textContent = msg
  toastEl.style.opacity = '1'
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => {
    if (toastEl) toastEl.style.opacity = '0'
  }, 1800)
}

export function errMsg(e: any, fallback = '请求失败'): string {
  return e?.msg || e?.message || fallback
}
