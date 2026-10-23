<template>
  <div class="login-wrap">
    <div class="login-card">
      <div class="login-logo">
        <div class="mark">BN</div>
        <div>
          <div class="t">结构健康监测平台</div>
        </div>
      </div>
      <div class="field">
        <label>用户名</label>
        <input v-model="form.username" class="input" placeholder="用户名" @keyup.enter="submit" />
      </div>
      <div class="field">
        <label>密码</label>
        <input v-model="form.password" class="input" type="password" placeholder="密码" @keyup.enter="submit" />
      </div>
      <button class="btn btn-primary" style="width: 100%; height: 40px; margin-top: 6px" :disabled="loading" @click="submit">
        {{ loading ? '登录中…' : '登 录' }}
      </button>
      <div class="login-hint">演示账号 admin，密码 admin123</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { toast, errMsg } from '@/utils/format'

const router = useRouter()
const userStore = useUserStore()
const app = useAppStore()

const form = reactive({ username: 'admin', password: 'admin123' })
const loading = ref(false)

async function submit() {
  if (!form.username || !form.password) {
    toast('请输入用户名与密码')
    return
  }
  loading.value = true
  try {
    await userStore.login(form.username, form.password)
    await app.loadProjects()
    router.push('/dashboard')
  } catch (e: any) {
    toast(errMsg(e, '登录失败'))
  } finally {
    loading.value = false
  }
}
</script>
