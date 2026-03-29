/**
 * 密码加密工具（保留用于其他场景）
 * 登录密码现在直接传输（HTTPS加密）
 */

function getRsaPublicKey(): string {
  try {
    const config = useRuntimeConfig()
    return config.public.rsaPublicKey as string || ''
  } catch {
    return ''
  }
}

/**
 * 加密密码（现在直接返回明文，由HTTPS加密传输）
 */
export async function encryptPassword(password: string): Promise<string> {
  // 直接返回明文密码，浏览器到服务器的传输已经有HTTPS/TLS加密
  return password
}

/**
 * 生成随机密码
 * @param length 密码长度，默认 12
 * @returns 随机密码
 */
export function generateRandomPassword(length: number = 12): string {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*'
  const random = new Uint32Array(length)
  crypto.getRandomValues(random)

  let password = ''
  for (let i = 0; i < length; i++) {
    const randomValue = random[i] ?? 0
    const charIndex = randomValue % chars.length
    password += chars.charAt(charIndex)
  }
  return password
}
