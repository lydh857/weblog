export const DEFAULT_SITE_NAME = 'Weblog'
export const DEFAULT_SITE_DESCRIPTION = '记录经验、分享洞察、连接有价值的内容。'
export const DEFAULT_SITE_FOOTER_NOTICE = '本站内容仅供学习与交流，商业使用请联系原作者授权。'
export const DEFAULT_SITE_FOOTER_COPYRIGHT = '© 2026 zhhhkl. All rights reserved.'
export const DEFAULT_SITE_DISCLAIMER_CONTENT = '1. 本站所有资源文章出自互联网收集整理，本站不参与制作，如果侵犯了您的合法权益，请联系本站我们会及时删除。\n2. 本站发布资源来源于互联网，可能存在水印或者引流等信息，请用户擦亮眼睛自行鉴别，做一个有主见和判断力的用户。\n3. 本站资源仅供研究、学习交流之用，若使用商业用途，请购买正版授权，否则产生的一切后果将由下载用户自行承担。'

export interface SiteConfigState {
  siteName: string
  siteDescription: string
  siteFooterNotice: string
  siteFooterCopyright: string
  siteDisclaimerContent: string
  loaded: boolean
}

export function useSiteConfigState() {
  return useState<SiteConfigState>('site-config', () => ({
    siteName: DEFAULT_SITE_NAME,
    siteDescription: DEFAULT_SITE_DESCRIPTION,
    siteFooterNotice: DEFAULT_SITE_FOOTER_NOTICE,
    siteFooterCopyright: DEFAULT_SITE_FOOTER_COPYRIGHT,
    siteDisclaimerContent: DEFAULT_SITE_DISCLAIMER_CONTENT,
    loaded: false,
  }))
}
