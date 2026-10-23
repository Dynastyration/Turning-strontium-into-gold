<template>
  <span v-if="value == null" class="mono muted">—</span>
  <span v-else class="battery">
    <span class="bt"><i :style="{ width: value + '%', background: tone }" /></span>{{ value }}%
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { batteryTone } from '@/utils/format'

const props = defineProps<{ value: number | null | undefined }>()
const tone = computed(() => batteryTone(props.value))
</script>
