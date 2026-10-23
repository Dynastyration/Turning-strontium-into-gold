<template>
  <span class="badge" :class="tone"><span v-if="dot" class="dot" :class="dotTone" />{{ text }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ text: string; tone: string; dot?: boolean }>()
const dotTone = computed(() => (['ok', 'danger', 'warn', 'info'].includes(props.tone) ? props.tone : 'off'))
</script>
