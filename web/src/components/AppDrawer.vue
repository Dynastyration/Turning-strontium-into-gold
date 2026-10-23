<template>
  <teleport to="body">
    <div class="overlay" :class="{ show: modelValue }" @click="close" />
    <aside class="drawer" :class="{ show: modelValue }">
      <div class="drawer-head">
        <h3>{{ title }}</h3>
        <button class="btn-icon" @click="close">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M18 6L6 18M6 6l12 12" /></svg>
        </button>
      </div>
      <div class="drawer-body"><slot /></div>
      <div class="drawer-foot"><slot name="foot" /></div>
    </aside>
  </teleport>
</template>

<script setup lang="ts">
defineProps<{ modelValue: boolean; title: string }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()
const close = () => emit('update:modelValue', false)
</script>
