import { authApi } from '~/api/auth/auth'
import { saveNavContext } from '~/utils/navigation/navContext'
import { normalizeSafeHref } from '~/utils/security/urlSafety'

interface RouteLike {
  path: string
  fullPath: string
}

interface UseLoginModalGithubOAuthOptions {
  onError: (message: string) => void
}

const GITHUB_OAUTH_HOSTS = new Set(['github.com', 'www.github.com'])

export function useLoginModalGithubOAuth(options: UseLoginModalGithubOAuthOptions) {
  const githubLoading = ref(false)

  async function handleGithubLogin(route: RouteLike) {
    githubLoading.value = true
    try {
      saveNavContext({
        path: route.path,
        fullPath: route.fullPath,
        scrollY: typeof window !== 'undefined' ? window.scrollY : 0,
      })

      const redirectUri = window.location.origin + '/oauth/github/callback'
      const res = await authApi.getGithubAuthUrl(redirectUri)
      const authUrl = normalizeSafeHref(res.data)
      if (!authUrl) {
        throw new Error('无效的 OAuth 地址')
      }

      const authHost = new URL(authUrl).host.toLowerCase()
      if (!GITHUB_OAUTH_HOSTS.has(authHost)) {
        throw new Error('无效的 OAuth 域名')
      }

      window.location.href = authUrl
    } catch {
      options.onError('GitHub 登录暂不可用')
      githubLoading.value = false
    }
  }

  return {
    githubLoading,
    handleGithubLogin,
  }
}
