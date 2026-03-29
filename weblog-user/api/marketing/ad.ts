import { http } from '~/utils/network/http'

export interface AdvertisementVO {
  id: number
  title: string
  type: string
  content: string
  linkUrl: string | null
  position: string
}

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

export const adApi = {
  getByPosition: (position: string) =>
    http.get<unknown, { data: AdvertisementVO[] }>('/portal/advertisement', { params: { position } }),
  recordClick: (id: number) =>
    http.post(`/portal/advertisement/${id}/click`),
}

export const announcementApi = {
  getByType: (type: string) =>
    http.get<AnnouncementVO[], { data: AnnouncementVO[] }>('/portal/announcement', { params: { type } }),
  getAll: () =>
    http.get<AnnouncementVO[], { data: AnnouncementVO[] }>('/portal/announcement'),
  getById: (id: number) =>
    http.get<AnnouncementVO, { data: AnnouncementVO }>(`/portal/announcement/${id}`),
}
