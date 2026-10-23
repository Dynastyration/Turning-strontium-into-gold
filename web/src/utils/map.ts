/** 经纬度 → 设计稿地图坐标系（mercator，与省界 SVG 的 viewBox 对齐）。 */
const PROJ = {
  leftLon: 73.554302,
  topLat: 53.562517,
  rightLon: 134.775703,
  bottomLat: 18.153878,
  minX: -8.28,
  maxX: 773.56,
  minY: -18.92,
  maxY: 542.12
}

const yMerc = (lat: number) => Math.log(Math.tan(Math.PI / 4 + (lat * Math.PI) / 180 / 2))
const YT = yMerc(PROJ.topLat)
const YB = yMerc(PROJ.bottomLat)

export function mapProj(lon: number, lat: number): [number, number] {
  const x = PROJ.minX + ((lon - PROJ.leftLon) / (PROJ.rightLon - PROJ.leftLon)) * (PROJ.maxX - PROJ.minX)
  const y = PROJ.minY + ((yMerc(lat) - YT) / (YB - YT)) * (PROJ.maxY - PROJ.minY)
  return [x, y]
}

export const MAP_VIEWBOX = `${PROJ.minX} ${PROJ.minY} ${PROJ.maxX - PROJ.minX} ${PROJ.maxY - PROJ.minY}`

/** 提示框要贴着点位放，超出这块范围就往回收，避免被卡片裁掉。 */
export const MAP_BOUNDS = { minX: PROJ.minX, maxX: PROJ.maxX, minY: PROJ.minY, maxY: PROJ.maxY }

export interface MapMarker {
  lng: number
  lat: number
  name: string
  /** 悬停提示的第二行：编号、运行状态、传感器数量 */
  meta?: string
  status: 'ok' | 'danger' | 'off'
}
