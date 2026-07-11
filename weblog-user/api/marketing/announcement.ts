import { http } from '~/utils/network/http'

export interface AnnouncementVO {
  id: number
  title: string
  content: string
  type: string
  priority: number
  isClosable: boolean
  createTime: string
  updateTime: string
}

export const announcementApi = {
  getByType: (type: string) =>
    http.get<AnnouncementVO[], { data: AnnouncementVO[] }>('/portal/announcement', { params: { type } }),
  getAll: () =>
    http.get<AnnouncementVO[], { data: AnnouncementVO[] }>('/portal/announcement'),
  getById: (id: number) =>
    http.get<AnnouncementVO, { data: AnnouncementVO }>(`/portal/announcement/${id}`),
}
