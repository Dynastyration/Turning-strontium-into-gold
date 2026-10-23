import axios from 'axios'
import { toast } from '@/utils/format'

const service = axios.create({
  baseURL: '/api/v1',
  timeout: 20000
})

service.interceptors.request.use((cfg) => {
  const token = localStorage.getItem('bn_token')
  if (token) {
    cfg.headers.Authorization = `Bearer ${token}`
  }
  return cfg
})

service.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body.code !== 'undefined') {
      if (body.code === 0) {
        return body.data
      }
      if (body.code === 40101) {
        localStorage.removeItem('bn_token')
        if (!location.hash.includes('/login')) {
          location.hash = '#/login'
        }
      }
      toast(body.msg || '请求失败')
      return Promise.reject(body)
    }
    return res.data
  },
  (err) => {
    toast(err?.message || '网络异常')
    return Promise.reject(err)
  }
)

export default service
