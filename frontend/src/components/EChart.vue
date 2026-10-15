<script setup lang="ts">
import { BarChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TitleComponent, TooltipComponent } from 'echarts/components'
import * as echarts from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import type { ECharts, EChartsCoreOption } from 'echarts/core'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

// 只注册用得到的图表与组件，避免把整个 echarts 打进包里
echarts.use([
  BarChart,
  PieChart,
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
  CanvasRenderer,
])

const props = withDefaults(
  defineProps<{
    option: EChartsCoreOption
    height?: string
  }>(),
  { height: '300px' },
)

const container = ref<HTMLDivElement | null>(null)
let chart: ECharts | null = null
let observer: ResizeObserver | null = null

function render(): void {
  if (!container.value) {
    return
  }
  if (!chart) {
    chart = echarts.init(container.value)
  }
  chart.setOption(props.option, true)
}

onMounted(() => {
  render()
  observer = new ResizeObserver(() => {
    chart?.resize()
  })
  if (container.value) {
    observer.observe(container.value)
  }
})

watch(
  () => props.option,
  () => {
    render()
  },
  { deep: true },
)

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div ref="container" class="chart" :style="{ height: props.height }" />
</template>

<style scoped>
.chart {
  width: 100%;
}
</style>
