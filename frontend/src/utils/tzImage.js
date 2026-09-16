import { getServerBase } from '@/utils/tzServer'

/**
 * 图片地址归一化。
 *
 * 后端给图片地址的地方有三处，格式并不统一：
 *   - `/common/upload` 上传接口      返回**绝对地址** http://127.0.0.1:18080/profile/...
 *   - 脚本播种的演示数据              存的也是绝对地址
 *   - 前端上传后自己拼的              相对路径 /profile/...
 *
 * **绝对地址里的 host 只在本机成立。** 手机通过公网域名访问时，
 * 那个 `127.0.0.1` 指向的是手机自己 —— 图片必然裂开，而页面其余部分都正常，
 * 看起来像是「照片丢了」，根本想不到是地址问题。
 *
 * 所以这里一律丢掉 origin、只取路径，再拼**当前生效的**接口前缀。
 * 前缀来自 tzServer（运行时可改），所以输入法是本机、局域网、公网还是 APK
 * 里，都能取到图。
 *
 * 这个函数存在的另一个理由：之前这段判断在 5 个页面里各写了一遍，
 * 于是「保留绝对地址」这个错误也跟着复制了 5 份。抽出来只留一处。
 */
export function resolveImageUrl(url) {
  if (!url) return ''
  const path = String(url).replace(/^https?:\/\/[^/]+/i, '')
  return getServerBase() + path
}
