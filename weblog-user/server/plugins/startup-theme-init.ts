const DARK_MASK = 'radial-gradient(120% 120% at 0% 0%, rgba(59, 130, 246, 0.13), transparent 45%), radial-gradient(120% 120% at 100% 100%, rgba(56, 189, 248, 0.1), transparent 52%), linear-gradient(180deg, #171b20, #101215)'
const LIGHT_MASK = 'radial-gradient(120% 120% at 0% 0%, rgba(37, 99, 235, 0.09), transparent 46%), radial-gradient(120% 120% at 100% 100%, rgba(14, 165, 233, 0.11), transparent 52%), linear-gradient(180deg, #ffffff, #f8fafc)'

function buildStartupThemeInitScript(): string {
  return `<script>(() => {
  try {
    const keys = ['vueuse-color-scheme', 'nuxt-color-mode', 'theme', 'color-scheme'];
    const startupDoneEvent = 'weblog:startup-done';
    const startupLockClass = 'startup-theme-lock';
    const darkMask = '${DARK_MASK}';
    const lightMask = '${LIGHT_MASK}';

    const normalizeMode = (value) => {
      if (value === null || value === undefined) return '';
      const text = String(value).trim().toLowerCase().replace(/^"|"$/g, '');
      if (text === 'dark' || text === 'true' || text === '1') return 'dark';
      if (text === 'light' || text === 'false' || text === '0') return 'light';
      if (text === 'auto' || text === 'system') return 'auto';
      return '';
    };

    const parseMode = (raw) => {
      const direct = normalizeMode(raw);
      if (direct) return direct;
      try {
        const parsed = JSON.parse(String(raw));
        if (typeof parsed === 'string' || typeof parsed === 'number' || typeof parsed === 'boolean') {
          return normalizeMode(parsed);
        }
        if (parsed && typeof parsed === 'object') {
          return normalizeMode(parsed.preference ?? parsed.value ?? parsed.mode ?? parsed.theme ?? parsed.colorMode ?? '');
        }
      } catch (_) {
        return '';
      }
      return '';
    };

    const parseThemeCookie = () => {
      const cookieHeader = document.cookie || '';
      if (!cookieHeader) return '';
      const chunks = cookieHeader.split(';');
      for (const chunk of chunks) {
        const [rawName, ...rawValueParts] = chunk.trim().split('=');
        if (rawName !== 'weblog-theme') continue;
        const rawValue = rawValueParts.join('=');
        const value = decodeURIComponent(rawValue).trim().toLowerCase();
        if (value === 'dark' || value === 'light') return value;
      }
      return '';
    };

    let mode = parseThemeCookie();
    if (!mode) {
      for (const key of keys) {
        const raw = localStorage.getItem(key);
        if (raw === null) continue;
        mode = parseMode(raw);
        if (mode) break;
      }
    }

    const mediaDark = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)').matches : false;
    const isDark = mode === 'dark' || ((mode === 'auto' || mode === '') && mediaDark);
    const root = document.documentElement;
    root.classList.add(startupLockClass);
    root.classList.toggle('dark', isDark);
    root.style.backgroundColor = isDark ? '#101215' : '#f8fafc';
    root.setAttribute('data-startup-theme', isDark ? 'dark' : 'light');
    root.style.setProperty('--startup-mask-bg', isDark ? darkMask : lightMask);
    root.style.setProperty('--startup-loader-text-color', isDark ? '#d7e4fb' : '#4e6ea8');
    root.style.setProperty('--startup-loader-shadow', isDark ? 'drop-shadow(0 10px 24px rgba(2, 6, 23, 0.6))' : 'drop-shadow(0 8px 20px rgba(32, 61, 120, 0.2))');

    const clearStartupLock = () => {
      root.classList.remove(startupLockClass);
      window.removeEventListener(startupDoneEvent, clearStartupLock);
    };

    window.addEventListener(startupDoneEvent, clearStartupLock, { once: true });
    window.setTimeout(clearStartupLock, 2600);
  } catch (_) {
    // noop
  }
})();</script>`
}

export default defineNitroPlugin((nitroApp) => {
  nitroApp.hooks.hook('render:html', (html) => {
    html.head.unshift(buildStartupThemeInitScript())
  })
})
