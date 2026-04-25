import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TitleComponent, TooltipComponent } from 'echarts/components'
import { graphic, init, use, type ECharts } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'

export type DashboardECharts = ECharts

export interface DashboardEchartsRuntime {
  graphic: typeof graphic
  init: typeof init
}

let registered = false

export function getDashboardEcharts(): DashboardEchartsRuntime {
  if (!registered) {
    use([
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

  return { graphic, init }
}
