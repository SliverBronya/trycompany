import defaultSettings from '@/settings'
import { useDark, useToggle } from '@vueuse/core'
import { useDynamicTitle } from '@/utils/dynamicTitle'
import { handleThemeStyle } from '@/utils/theme'

const isDark = useDark()
const toggleDark = useToggle(isDark)

const { sideTheme, showSettings, navType, tagsView, tagsViewPersist, tagsIcon, tagsViewStyle, fixedHeader, sidebarLogo, dynamicTitle, footerVisible, footerContent } = defaultSettings

const storageSetting = JSON.parse(localStorage.getItem('layout-setting')) || ''

// 若依出厂默认的科技蓝。项目定位是柑橘植保，蓝色后台太"通用"，
// 换成带纸感的深墨绿（见 assets/styles/tianzhen.scss 的「农事台账」）。
const RUOYI_DEFAULT_THEME = '#409EFF'
const TZ_DEFAULT_THEME = '#2F6B4F'

/**
 * 取主题色。
 *
 * 关键点：老版本的 localStorage 里很可能已经存着若依默认蓝，而 localStorage
 * 的优先级高于这里写的默认值 —— 不改的话，改完默认色在浏览器里还是显示蓝色，
 * 看起来像"改了没生效"。所以把"存的就是出厂蓝"视同"用户从没自定义过"。
 * 用户真正手选过的其它颜色一律尊重，不动。
 */
function pickTheme(stored) {
  if (!stored) return TZ_DEFAULT_THEME
  if (String(stored).toUpperCase() === RUOYI_DEFAULT_THEME) return TZ_DEFAULT_THEME
  return stored
}

const useSettingsStore = defineStore(
  'settings',
  {
    state: () => ({
      title: '',
      theme: pickTheme(storageSetting.theme),
      sideTheme: storageSetting.sideTheme || sideTheme,
      showSettings: showSettings,
      navType: storageSetting.navType === undefined ? navType : storageSetting.navType,
      tagsView: storageSetting.tagsView === undefined ? tagsView : storageSetting.tagsView,
      tagsViewPersist: storageSetting.tagsViewPersist === undefined ? tagsViewPersist : storageSetting.tagsViewPersist,
      tagsIcon: storageSetting.tagsIcon === undefined ? tagsIcon : storageSetting.tagsIcon,
      tagsViewStyle: storageSetting.tagsViewStyle === undefined ? tagsViewStyle : storageSetting.tagsViewStyle,
      fixedHeader: storageSetting.fixedHeader === undefined ? fixedHeader : storageSetting.fixedHeader,
      sidebarLogo: storageSetting.sidebarLogo === undefined ? sidebarLogo : storageSetting.sidebarLogo,
      dynamicTitle: storageSetting.dynamicTitle === undefined ? dynamicTitle : storageSetting.dynamicTitle,
      footerVisible: storageSetting.footerVisible === undefined ? footerVisible : storageSetting.footerVisible,
      footerContent: footerContent,
      isDark: isDark.value
    }),
    actions: {
      // 修改布局设置
      changeSetting(data) {
        const { key, value } = data
        if (this.hasOwnProperty(key)) {
          this[key] = value
        }
      },
      // 设置网页标题
      setTitle(title) {
        this.title = title
        useDynamicTitle()
      },
      // 切换暗黑模式
      toggleTheme() {
        this.isDark = !this.isDark
        toggleDark()
        nextTick(() => {
          handleThemeStyle(this.theme)
        })
      }
    }
  })

export default useSettingsStore
