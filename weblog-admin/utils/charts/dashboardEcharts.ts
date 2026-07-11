import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TitleComponent, TooltipComponent } from 'echarts/components'
import * as echarts from 'echarts/core'
import type { ECharts } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'

export type DashboardECharts = ECharts

export interface DashboardEchartsRuntime {
  graphic: typeof echarts.graphic
  init: typeof echarts.init
}

let registered = false

export function getDashboardEcharts(): DashboardEchartsRuntime {
  if (!registered) {
    echarts.use([
      BarChart,
      LineChart,
      PieChart,
      TitleComponent,
      TooltipComponent,
      GridComponent,
      LegendComponent,
      CanvasRenderer,
    ])
    registered = true
  }

  return { graphic: echarts.graphic, init: echarts.init }
}
