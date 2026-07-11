import { defineStore } from 'pinia'

export interface CandidateRow {
  id: number
  task_id?: number
  title: string | null
  summary?: string | null
  state: string
  sourceUrl: string
  source_site?: string | null
  draft_push_status?: string | null
  backend_candidate_id?: number | null
  last_push_message?: string | null
  fail_reason?: string | null
  updated_at?: string
  created_at?: string
}

export interface PushResultRow {
  item_id: number
  status: string
  message: string
  backend_candidate_id?: number | null
  draft_id?: number | null
  title: string
  pushed_at: string
}

export const useCrawlerStore = defineStore('crawler', {
  state: () => ({
    selectedCandidateIds: [] as number[],
    selectedTaskId: null as number | null,
    pushResults: [] as PushResultRow[]
  }),
  actions: {
    setSelectedCandidateIds(ids: number[]) {
      this.selectedCandidateIds = ids
    },
    setSelectedTaskId(id: number | null) {
      this.selectedTaskId = id
    },
    setPushResults(rows: PushResultRow[]) {
      this.pushResults = rows
    },
    clearPushResults() {
      this.pushResults = []
    }
  }
})
