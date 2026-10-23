import http from './request'

export const authApi = {
  login: (username: string, password: string) =>
    http.post('/auth/login', { username, password }),
  me: () => http.get('/auth/me')
}

export const dashboardApi = {
  overview: (projectId?: number) => http.get('/dashboard/overview', { params: { projectId } }),
  trend: (days = 7) => http.get('/dashboard/trend', { params: { days } }),
  topAlarm: (limit = 8) => http.get('/dashboard/top-alarm', { params: { limit } }),
  typeDist: (projectId?: number) => http.get('/dashboard/type-dist', { params: { projectId } }),
  latest: (limit = 8) => http.get('/dashboard/latest-readings', { params: { limit } })
}

export const deviceApi = {
  page: (params: any) => http.get('/devices', { params }),
  detail: (id: number) => http.get(`/devices/${id}`),
  create: (data: any) => http.post('/devices', data),
  update: (id: number, data: any) => http.put(`/devices/${id}`, data),
  remove: (id: number) => http.delete(`/devices/${id}`),
  geo: (projectId?: number) => http.get('/devices/geo', { params: { projectId } }),
  sensors: (id: number) => http.get(`/devices/${id}/sensors`),
  command: (id: number, cmdType: string, payload: string) =>
    http.post(`/devices/${id}/command`, { cmdType, payload }),
  commandStatus: (id: number, cid: number) => http.get(`/devices/${id}/command/${cid}`)
}

export const sensorApi = {
  page: (params: any) => http.get('/sensors', { params }),
  detail: (id: number) => http.get(`/sensors/${id}`),
  update: (id: number, data: any) => http.put(`/sensors/${id}`, data),
  latest: (id: number) => http.get(`/sensors/${id}/latest`),
  series: (id: number, params: any) => http.get(`/sensors/${id}/series`, { params }),
  readings: (id: number, params: any) => http.get(`/sensors/${id}/readings`, { params }),
  groups: (projectId?: number) => http.get('/sensors/groups', { params: { projectId } }),
  createGroup: (data: any) => http.post('/sensors/groups', data),
  updateGroup: (id: number, data: any) => http.put(`/sensors/groups/${id}`, data),
  deleteGroup: (id: number) => http.delete(`/sensors/groups/${id}`),
  assignGroup: (ids: number[], groupId: number) => http.put('/sensors/group', { ids, groupId })
}

export const dataApi = {
  query: (body: any) => http.post('/data/query', body),
  compare: (body: any) => http.post('/data/compare', body),
  fields: (typeId: number) => http.get('/data/fields', { params: { typeId } }),
  createReport: (body: any) => http.post('/reports', body),
  reports: () => http.get('/reports'),
  report: (id: number) => http.get(`/reports/${id}`),
  downloadUrl: (id: number) => `/api/v1/reports/${id}/download`
}

export const videoApi = {
  list: (params: any) => http.get('/videos', { params }),
  play: (id: number) => http.get(`/videos/${id}/play`),
}

export const alarmApi = {
  page: (params: any) => http.get('/alarms', { params }),
  ack: (id: number) => http.post(`/alarms/${id}/ack`),
  close: (id: number) => http.post(`/alarms/${id}/close`),
  stat: (groupBy = 'level') => http.get('/alarms/stat', { params: { groupBy } }),
  unread: () => http.get('/alarms/unread-count'),
  configs: () => http.get('/alarm-configs'),
  createConfig: (data: any) => http.post('/alarm-configs', data),
  updateConfig: (id: number, data: any) => http.put(`/alarm-configs/${id}`, data),
  deleteConfig: (id: number) => http.delete(`/alarm-configs/${id}`)
}

export const metaApi = {
  dicts: () => http.get('/dicts/all'),
  projects: () => http.get('/projects'),
  sensorTypes: () => http.get('/sensor-types')
}
