/**
 * 后端接口地址的**运行时**配置。
 *
 * 为什么不能像网页版那样只用编译期常量：
 * 网页版里 `VITE_APP_BASE_API = '/dev-api'` 是相对路径，靠 Vite 代理转到后端 ——
 * 这依赖「同源」。但打包成 APK 后，前端资源是从 `capacitor://localhost` 这种
 * 本地地址加载的，**没有同源可言**，相对路径会打到手机自己身上，请求全部失败。
 *
 * 所以 APK 里的地址必须是绝对 URL，而且必须**运行时可改** ——
 * 换网络、ngrok 地址变了，都不该逼着重新打包一个 APK。
 *
 * 浏览器里仍然走相对路径，本机开发不受影响。
 */

const KEY = 'tz-server-url'

/** 装成 APP 时的默认后端地址（打包时写进来的，之后可在「服务器设置」里改） */
export const DEFAULT_SERVER = 'https://revenge-duh-attest.ngrok-free.dev/dev-api'

/** 是否运行在 Capacitor 打包出来的原生壳里 */
export function isNativeApp() {
  try {
    return !!(window.Capacitor && window.Capacitor.isNativePlatform && window.Capacitor.isNativePlatform())
  } catch (e) {
    return false
  }
}

/**
 * 取当前生效的接口前缀。
 *
 * **每次调用都现读 localStorage**，不做缓存 —— 改完设置不用重启 APP 就生效。
 */
export function getServerBase() {
  let saved = ''
  try {
    saved = localStorage.getItem(KEY) || ''
  } catch (e) {
    saved = ''
  }
  if (saved.trim()) return saved.trim().replace(/\/+$/, '')
  // 浏览器：保持相对路径（走代理）；APP：用绝对地址
  return isNativeApp() ? DEFAULT_SERVER : import.meta.env.VITE_APP_BASE_API
}

/** 保存自定义地址；传空则恢复默认 */
export function setServerBase(url) {
  const v = String(url || '').trim().replace(/\/+$/, '')
  try {
    if (v) localStorage.setItem(KEY, v)
    else localStorage.removeItem(KEY)
  } catch (e) {
    // 隐私模式下 localStorage 可能不可用，忽略即可
  }
  return getServerBase()
}

/** 是否已自定义过（用于设置页提示「当前为默认地址」还是「当前为自定义地址」） */
export function isCustomServer() {
  try {
    return !!localStorage.getItem(KEY)
  } catch (e) {
    return false
  }
}

/**
 * 经由 ngrok 免费版访问时必须带的请求头。
 *
 * ngrok 免费版会对**看起来像浏览器的请求**插一个「你即将访问…」的警告页，
 * 返回的是一整页 HTML（状态码还是 200），而且会种 cookie 让你下次免打扰。
 *
 * 网页版没事：你能在那个页面上点一下「继续访问」，之后 cookie 生效。
 * 但 **APP 里的 WebView 没有「点一下」的机会** ——
 * 表现为接口全报「后端接口连接异常」（前端收到的是 HTML 不是 JSON），
 * 图片则全部裂开。这个现象很难从表象联想到隧道，所以记在这里。
 *
 * 带上这个头就绕过警告页。对非 ngrok 的服务端来说它只是个被忽略的多余头，无害。
 */
export const TUNNEL_HEADER = 'ngrok-skip-browser-warning'

/** 所有请求都应该合并进去的额外头 */
export function tunnelHeaders() {
  return { [TUNNEL_HEADER]: 'true' }
}
