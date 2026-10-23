<template>
  <div class="spark" v-html="svg" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { spark } from '@/utils/charts'

const props = defineProps<{ values: number[]; gid?: string }>()
const svg = computed(() => spark(props.values, props.gid || 'spark'))
</script>
