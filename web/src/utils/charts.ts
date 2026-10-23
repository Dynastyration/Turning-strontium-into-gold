/** 内联 SVG 图表，全部按设计稿的绘制方式还原，颜色一律走 CSS 变量以便跟随主题。 */

export interface AreaPoint {
  l: string
  v: number
}

export function areaChart(data: AreaPoint[], opts: { w?: number; h?: number; gid?: string; int?: boolean } = {}): string {
  const W = opts.w || 300
  const H = opts.h || 210
  const pl = 30
  const pr = 8
  // 上下留白按高度收缩，矮面板里把空间让给折线本身
  const pt = Math.min(12, H * 0.14)
  const pb = Math.min(24, H * 0.2)
  if (!data.length) return ''
  const peak = Math.max(...data.map((d) => d.v)) || 0
  // 整数序列（如每天告警条数）顶部取整到 4 等分，刻度才是 0/1/2/3 这种能读的数
  const step = opts.int ? (peak <= 4 ? 1 : peak <= 8 ? 2 : peak <= 20 ? 5 : Math.ceil(peak / 4)) : 0
  const max = opts.int ? Math.max(4, step * 4) : peak * 1.18 || 1
  const iw = W - pl - pr
  const ih = H - pt - pb
  const x = (i: number) => pl + iw * (i / Math.max(1, data.length - 1))
  const y = (v: number) => pt + ih * (1 - v / max)

  let g = ''
  for (let k = 0; k <= 4; k++) {
    const yy = pt + (ih * k) / 4
    g += `<line x1="${pl}" y1="${yy}" x2="${W - pr}" y2="${yy}" stroke="var(--border)" stroke-width="1"/>`
  }
  // 左侧刻度：四段网格各标一个值，量级小的时候保留一位小数
  const fmt = (v: number) => (opts.int ? Math.round(v).toString() : max <= 5 ? (Math.round(v * 10) / 10).toString() : Math.round(v).toString())
  let ylab = ''
  for (let k = 0; k <= 4; k++) {
    const yy = pt + (ih * k) / 4
    ylab += `<text x="${pl - 6}" y="${(yy + 3.2).toFixed(1)}" text-anchor="end" fill="var(--muted)" font-size="9" font-family="var(--font-mono)">${fmt(max * (1 - k / 4))}</text>`
  }
  const lineD = data.map((d, i) => `${i ? 'L' : 'M'} ${x(i).toFixed(1)} ${y(d.v).toFixed(1)}`).join(' ')
  const areaD = `${lineD} L ${x(data.length - 1).toFixed(1)} ${(pt + ih).toFixed(1)} L ${pl} ${(pt + ih).toFixed(1)} Z`
  const dots = data
    .map((d, i) => `<circle cx="${x(i).toFixed(1)}" cy="${y(d.v).toFixed(1)}" r="3" fill="var(--surface)" stroke="var(--c1)" stroke-width="2"/>`)
    .join('')
  const n = data.length
  const stride = n <= 6 ? 1 : Math.ceil(n / 6)
  const labs = data
    .map((d, i) => ({ d, i }))
    .filter(({ i }) => (n - 1 - i) % stride === 0)
    .map(({ d, i }) => {
      const anchor = i === n - 1 ? 'end' : i === 0 ? 'start' : 'middle'
      const tx = i === n - 1 ? W - pr : i === 0 ? pl : x(i)
      return `<text x="${tx.toFixed(1)}" y="${H - 8}" text-anchor="${anchor}" fill="var(--muted)" font-size="9.5" font-family="var(--font-mono)">${d.l}</text>`
    })
    .join('')
  const gid = opts.gid || 'ag'
  return `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="xMidYMid meet">
    <defs><linearGradient id="${gid}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="var(--c1)" stop-opacity=".28"/><stop offset="1" stop-color="var(--c1)" stop-opacity="0"/></linearGradient></defs>
    ${g}${ylab}<path d="${areaD}" fill="url(#${gid})"/><path d="${lineD}" fill="none" stroke="var(--c1)" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/>
    ${dots}${labs}</svg>`
}

export interface Slice {
  name: string
  value: number
  color: string
}

export function donutChart(data: Slice[], total: number, center: string, unit: string, box: { w?: number; h?: number } = {}): string {
  const W = box.w || 200
  const H = box.h || 200
  const R = Math.min(W, H) / 2
  const r = R * 0.72
  const cx = W / 2
  const cy = H / 2
  const sw = Math.max(8, r * 0.46)
  let acc = 0
  const segs = data
    .map((d) => {
      const pct = total ? (d.value / total) * 100 : 0
      const s = `<circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="${d.color}" stroke-width="${sw}" pathLength="100" stroke-dasharray="${pct.toFixed(2)} ${(100 - pct).toFixed(2)}" stroke-dashoffset="${(-acc).toFixed(2)}" transform="rotate(-90 ${cx} ${cy})"/>`
      acc += pct
      return s
    })
    .join('')
  return `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="xMidYMid meet">
    <circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="var(--surface-2)" stroke-width="${sw}"/>
    ${segs}
    <text x="${cx}" y="${cy + r * 0.14}" text-anchor="middle" fill="var(--fg)" font-size="${Math.max(13, r * 0.68)}" font-weight="700" font-family="var(--font-mono)">${center}</text>
    <text x="${cx}" y="${cy + r * 0.62}" text-anchor="middle" fill="var(--muted)" font-size="${Math.max(8, r * 0.26)}">${unit}</text>
  </svg>`
}

export function ringGauge(pct: number, center: string, unit: string, box: { w?: number; h?: number } = {}): string {
  const W = box.w || 200
  const H = box.h || 200
  const R = Math.min(W, H) / 2
  const r = R * 0.8
  const cx = W / 2
  const cy = H / 2
  const sw = Math.max(7, r * 0.2)
  return `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="xMidYMid meet">
    <circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="var(--surface-2)" stroke-width="${sw}"/>
    ${pct > 0 ? `<circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="var(--c1)" stroke-width="${sw}" pathLength="100" stroke-dasharray="${pct} ${100 - pct}" transform="rotate(-90 ${cx} ${cy})" stroke-linecap="round"/>` : ''}
    <text x="${cx}" y="${cy + r * 0.1}" text-anchor="middle" fill="var(--fg)" font-size="${Math.max(12, r * 0.42)}" font-weight="700" font-family="var(--font-mono)">${center}</text>
    <text x="${cx}" y="${cy + r * 0.44}" text-anchor="middle" fill="var(--muted)" font-size="${Math.max(8, r * 0.2)}">${unit}</text>
  </svg>`
}

export function barH(data: Slice[], opts: { w?: number; h?: number } = {}): string {
  const W = opts.w || 280
  const H = opts.h || data.length * 58 + 18
  const rowH = H / Math.max(1, data.length)
  const bar = Math.max(10, Math.min(26, rowH * 0.46))
  const gap = (rowH - bar) / 2
  const max = Math.max(1, ...data.map((d) => d.value))
  const fs = Math.max(9, Math.min(12, rowH * 0.5))
  const x0 = 46
  const padR = 34
  const rows = data
    .map((d, i) => {
      const w = d.value === 0 ? 0 : Math.max(8, (d.value / max) * (W - x0 - padR))
      const yy = i * rowH + gap
      const ty = (yy + bar / 2 + fs * 0.35).toFixed(1)
      return `<text x="0" y="${ty}" fill="var(--fg)" font-size="${fs}">${d.name}</text>
      <rect x="${x0}" y="${yy.toFixed(1)}" width="${W - x0}" height="${bar.toFixed(1)}" rx="4" fill="var(--surface-2)"/>
      <rect x="${x0}" y="${yy.toFixed(1)}" width="${w.toFixed(1)}" height="${bar.toFixed(1)}" rx="4" fill="${d.color}"/>
      <text x="${(x0 + w + 8).toFixed(1)}" y="${ty}" fill="${d.color}" font-size="${fs}" font-weight="700" font-family="var(--font-mono)">${d.value}</text>`
    })
    .join('')
  return `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="xMidYMid meet">${rows}</svg>`
}

export function spark(arr: number[], gid = 'sp'): string {
  if (arr.length < 2) return ''
  const W = 120
  const H = 34
  const min = Math.min(...arr)
  const max = Math.max(...arr)
  const rng = max - min || 1
  const px = (i: number) => (i / (arr.length - 1)) * W
  const py = (v: number) => H - 3 - ((v - min) / rng) * (H - 8)
  const d = arr.map((v, i) => `${i ? 'L' : 'M'} ${px(i).toFixed(1)} ${py(v).toFixed(1)}`).join(' ')
  return `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="none" style="width:100%;height:100%;"><defs><linearGradient id="${gid}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="var(--c1)" stop-opacity=".3"/><stop offset="1" stop-color="var(--c1)" stop-opacity="0"/></linearGradient></defs>
    <path d="${d} L ${W} ${H} L 0 ${H} Z" fill="url(#${gid})"/><path d="${d}" fill="none" stroke="var(--c1)" stroke-width="1.6"/></svg>`
}

export interface Series {
  name: string
  values: (number | null)[]
}

/** 多序列折线：缺值断段不连线，与后端的时间桶对齐结果直接对接。 */
export function multiLine(series: Series[], labels: string[], box: { w?: number; h?: number } = {}): string {
  const W = box.w || 720
  const H = box.h || 320
  const pl = 46
  const pr = 14
  const pt = 30
  const pb = 30
  const all = series.flatMap((s) => s.values).filter((v): v is number => v != null)
  if (!all.length || labels.length < 2) {
    return `<text x="${W / 2}" y="${H / 2}" text-anchor="middle" fill="var(--muted)" font-size="12">所选区间没有数据</text>`
  }
  const min = Math.min(...all)
  const max = Math.max(...all)
  const span = max - min || 1
  const iw = W - pl - pr
  const ih = H - pt - pb
  const x = (i: number) => pl + iw * (i / (labels.length - 1))
  const y = (v: number) => pt + ih * (1 - (v - min) / span)

  let g = ''
  for (let k = 0; k <= 4; k++) {
    const yy = pt + (ih * k) / 4
    g += `<line x1="${pl}" y1="${yy}" x2="${W - pr}" y2="${yy}" stroke="var(--border)" stroke-width="1"/>`
  }
  // 纵轴刻度：网格线四段，从最大值往下递减；小数位按跨度收起，避免一串无意义的尾数
  const dp = span >= 10 ? 0 : span >= 0.5 ? 1 : 2
  let ylabM = ''
  for (let k = 0; k <= 4; k++) {
    const yy = pt + (ih * k) / 4
    const v = max - span * (k / 4)
    ylabM += `<text x="${pl - 8}" y="${(yy + 3.2).toFixed(1)}" text-anchor="end" fill="var(--muted)" font-size="9.5" font-family="var(--font-mono)">${v.toFixed(dp)}</text>`
  }
  const stepLab = Math.max(1, Math.ceil(labels.length / 8))
  const xlab = labels
    .map((lb, i) => (i % stepLab === 0 ? `<text x="${x(i).toFixed(1)}" y="${H - 8}" text-anchor="middle" fill="var(--muted)" font-size="9.5" font-family="var(--font-mono)">${lb}</text>` : ''))
    .join('')
  const colors = ['var(--c1)', 'var(--c2)', 'var(--c3)']
  const lines = series
    .map((s, si) => {
      const segs: string[] = []
      let cur = ''
      s.values.forEach((v, i) => {
        if (v == null) {
          if (cur) segs.push(cur)
          cur = ''
        } else {
          cur += (cur ? ' L ' : 'M ') + `${x(i).toFixed(1)} ${y(v).toFixed(1)}`
        }
      })
      if (cur) segs.push(cur)
      return segs.map((p) => `<path d="${p}" fill="none" stroke="${colors[si % 3]}" stroke-width="2"/>`).join('')
    })
    .join('')
  const legend = series
    .map((s, si) => `<text x="${pl + si * 150}" y="16" fill="${colors[si % 3]}" font-size="11" font-family="var(--font-mono)">— ${s.name}</text>`)
    .join('')
  return `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="xMidYMid meet">${g}${ylabM}${xlab}${lines}${legend}</svg>`
}
