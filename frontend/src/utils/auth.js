import Cookies from 'js-cookie'

const TokenKey = 'Admin-Token'

/*
  token 的存取在两种构建下走不同存储：

  · 正常部署（走 HTTP）：cookie。权限判断要跟着每次请求走，cookie 最合适。
  · 离线演示（file:// 直接打开单文件）：localStorage。
    浏览器对 file:// 页面不保存 cookie —— document.cookie 写进去读不出来
    （file 页面没有可归属的域），于是刚登录完 permission 守卫就读不到 token，
    直接被踢回登录页，整个演示包等于废了。localStorage 没有 this 限制，
    file:// 下读写都正常。
*/
const isDemo = import.meta.env.VITE_DEMO === 'true'

export function getToken() {
  return isDemo ? localStorage.getItem(TokenKey) : Cookies.get(TokenKey)
}

export function setToken(token) {
  return isDemo ? localStorage.setItem(TokenKey, token) : Cookies.set(TokenKey, token)
}

export function removeToken() {
  return isDemo ? localStorage.removeItem(TokenKey) : Cookies.remove(TokenKey)
}
