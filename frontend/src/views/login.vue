<template>
  <div class="login">
    <!-- 左：品牌区。放的是系统真实能力，不是营销话术 —— 登录页也是台账的封面 -->
    <section class="login-brand">
      <header class="brand-top">
        <img src="@/assets/logo/logo.png" class="brand-logo" alt="" />
        <span class="brand-name">{{ title }}</span>
      </header>

      <div class="brand-mid">
        <h1 class="brand-headline">柑橘植保<br />巡田与诊断台账</h1>
        <ul class="brand-points">
          <li v-for="point in highlights" :key="point.name">
            <span class="point-name">{{ point.name }}</span>
            <span class="point-desc">{{ point.desc }}</span>
          </li>
        </ul>
      </div>

      <footer class="brand-foot">
        <span>{{ brandFoot }}</span>
      </footer>
    </section>

    <!-- 右：登录表单 -->
    <section class="login-panel">
      <el-form ref="loginRef" :model="loginForm" :rules="loginRules" class="login-form">
        <h2 class="form-title">登录</h2>
        <p class="form-sub">请使用分配给你的账号登录</p>

        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            type="text"
            size="large"
            auto-complete="off"
            placeholder="账号"
          >
            <template #prefix><svg-icon icon-class="user" class="el-input__icon input-icon" /></template>
          </el-input>
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            size="large"
            auto-complete="off"
            placeholder="密码"
            @keyup.enter="handleLogin"
          >
            <template #prefix><svg-icon icon-class="password" class="el-input__icon input-icon" /></template>
          </el-input>
        </el-form-item>

        <el-form-item prop="code" v-if="captchaEnabled">
          <el-input
            v-model="loginForm.code"
            size="large"
            auto-complete="off"
            placeholder="验证码"
            style="width: 63%"
            @keyup.enter="handleLogin"
          >
            <template #prefix><svg-icon icon-class="validCode" class="el-input__icon input-icon" /></template>
          </el-input>
          <div class="login-code">
            <img :src="codeUrl" @click="getCode" class="login-code-img"/>
          </div>
        </el-form-item>

        <div class="form-row">
          <el-checkbox v-model="loginForm.rememberMe">记住密码</el-checkbox>
          <router-link v-if="register" class="link-type" :to="'/register'">立即注册</router-link>
        </div>

        <el-button
          :loading="loading"
          size="large"
          type="primary"
          class="submit-btn"
          @click.prevent="handleLogin"
        >
          <span v-if="!loading">登 录</span>
          <span v-else>登 录 中...</span>
        </el-button>
      </el-form>

      <div class="el-login-footer">
        <span>{{ footerContent }}</span>
      </div>
    </section>
  </div>
</template>

<script setup>
import { getCodeImg } from "@/api/login"
import Cookies from "js-cookie"
import { encrypt, decrypt } from "@/utils/jsencrypt"
import useUserStore from '@/store/modules/user'
import defaultSettings from '@/settings'

const title = import.meta.env.VITE_APP_TITLE
const footerContent = defaultSettings.footerContent

// 左侧品牌区写的是系统的真实能力，不是宣传语 —— 这几条都能在系统里找到对应功能。
// 登录页不该许诺产品没有的东西，尤其这个项目的底线是"结论可以没有，但不能编"。
const highlights = [
  { name: '巡田记录', desc: '影像与症状一并归档，田间情况可回溯' },
  { name: 'AI 辅助初诊', desc: '结论同时标注置信度与来源，把握不足就明说不采信' },
  { name: '剂量双向校验', desc: '用药建议逐条比对知识库，超量自动拦下' },
  { name: '植保知识库', desc: '症状、用药与注意事项一站检索' }
]

const brandFoot = '本系统结论仅供巡田参考，不能替代农技人员的现场诊断与农药标签说明。'
const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const { proxy } = getCurrentInstance()

// 口令一律不预填。原来是 password: "admin123" —— 那等于把口令印在登录页上，
// 页面一旦挂到公网，任何人打开就能看见；而且改了口令之后这里会变成
// 「输对了也登不上」的假故障，排查时最容易被忽略。
// 账号名保留预填，它不是密钥，演示时少打几个字。
const loginForm = ref({
  username: "admin",
  password: "",
  rememberMe: false,
  code: "",
  uuid: ""
})

const loginRules = {
  username: [{ required: true, trigger: "blur", message: "请输入您的账号" }],
  password: [{ required: true, trigger: "blur", message: "请输入您的密码" }],
  code: [{ required: true, trigger: "change", message: "请输入验证码" }]
}

const codeUrl = ref("")
const loading = ref(false)
// 验证码开关
const captchaEnabled = ref(true)
// 注册开关
const register = ref(false)
const redirect = ref(undefined)

watch(route, (newRoute) => {
    redirect.value = newRoute.query && newRoute.query.redirect
}, { immediate: true })

function handleLogin() {
  proxy.$refs.loginRef.validate(valid => {
    if (valid) {
      loading.value = true
      // 勾选了需要记住密码设置在 cookie 中设置记住用户名和密码
      if (loginForm.value.rememberMe) {
        Cookies.set("username", loginForm.value.username, { expires: 30 })
        Cookies.set("password", encrypt(loginForm.value.password), { expires: 30 })
        Cookies.set("rememberMe", loginForm.value.rememberMe, { expires: 30 })
      } else {
        // 否则移除
        Cookies.remove("username")
        Cookies.remove("password")
        Cookies.remove("rememberMe")
      }
      // 调用action的登录方法
      userStore.login(loginForm.value).then(() => {
        const query = route.query
        const otherQueryParams = Object.keys(query).reduce((acc, cur) => {
          if (cur !== "redirect") {
            acc[cur] = query[cur]
          }
          return acc
        }, {})
        router.push({ path: redirect.value || "/", query: otherQueryParams })
      }).catch(() => {
        loading.value = false
        // 重新获取验证码
        if (captchaEnabled.value) {
          getCode()
        }
      })
    }
  })
}

function getCode() {
  getCodeImg().then(res => {
    captchaEnabled.value = res.captchaEnabled === undefined ? true : res.captchaEnabled
    // 注册开关由后端 /captchaImage 下发（读 sys.account.registerUser）。
    // 上游若依把 register 声明成 ref(false) 之后就再没赋过值，
    // 所以「立即注册」在原生代码里是永远不显示的——不是样式问题，是压根没接上。
    register.value = res.registerEnabled === true
    if (captchaEnabled.value) {
      codeUrl.value = "data:image/gif;base64," + res.img
      loginForm.value.uuid = res.uuid
    }
  })
}

function getCookie() {
  const username = Cookies.get("username")
  const password = Cookies.get("password")
  const rememberMe = Cookies.get("rememberMe")
  loginForm.value = {
    username: username === undefined ? loginForm.value.username : username,
    password: password === undefined ? loginForm.value.password : decrypt(password),
    rememberMe: rememberMe === undefined ? false : Boolean(rememberMe)
  }
}

getCode()
getCookie()
</script>

<style lang='scss' scoped>
.login {
  display: flex;
  height: 100%;
  background-color: var(--tz-paper);
}

/* ---------- 左：品牌区 ---------- */
.login-brand {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 40px;
  padding: 46px 52px;
  background-color: #1c3a2d;
  color: #e9f0ea;
}

.brand-top {
  display: flex;
  align-items: center;
  gap: 11px;

  .brand-logo {
    width: 34px;
    height: 34px;
    object-fit: contain;
  }

  .brand-name {
    font-size: 16px;
    font-weight: 600;
    letter-spacing: 1.5px;
    color: #e9f0ea;
  }
}

.brand-mid {
  max-width: 46ch;
}

.brand-headline {
  margin: 0 0 26px;
  font-size: clamp(28px, 3.1vw, 42px);
  font-weight: 600;
  line-height: 1.26;
  letter-spacing: -0.4px;
  color: #ffffff;
}

.brand-points {
  list-style: none;
  margin: 0;
  padding: 0;

  li {
    padding: 11px 0;
    /* 细线是拿来分隔条目的结构线，不是装饰 */
    border-top: 1px solid rgba(233, 240, 234, 0.15);

    &:last-child {
      border-bottom: 1px solid rgba(233, 240, 234, 0.15);
    }
  }

  .point-name {
    display: block;
    font-size: 14px;
    font-weight: 600;
    letter-spacing: 0.3px;
    color: #e9f0ea;
    margin-bottom: 3px;
  }

  .point-desc {
    display: block;
    font-size: 13px;
    line-height: 1.6;
    color: rgba(233, 240, 234, 0.6);
  }
}

.brand-foot {
  font-size: 12px;
  line-height: 1.7;
  color: rgba(233, 240, 234, 0.58);
  max-width: 52ch;
}

/* ---------- 右：表单区 ---------- */
.login-panel {
  flex: 0 0 50%;
  max-width: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 32px;
  background-color: var(--tz-paper);
}

.login-form {
  width: 100%;
  max-width: 372px;
}

.form-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  letter-spacing: -0.2px;
  color: var(--tz-ink);
}

.form-sub {
  margin: 7px 0 26px;
  font-size: 13px;
  color: var(--tz-ink-3);
}

.form-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 22px;
}

.submit-btn {
  width: 100%;
  letter-spacing: 4px;
  font-weight: 500;
}

.login-form {
  .el-input {
    height: 44px;

    input {
      height: 44px;
    }
  }

  .input-icon {
    height: 20px;
    width: 14px;
    margin-left: 0;
  }
}

.login-code {
  width: 33%;
  height: 44px;
  float: right;

  img {
    cursor: pointer;
    vertical-align: middle;
  }
}

.login-code-img {
  height: 44px;
  padding-left: 12px;
}

.el-login-footer {
  margin-top: 34px;
  max-width: 372px;
  text-align: center;
  font-size: 12px;
  line-height: 1.6;
  color: var(--tz-ink-4);
}

/* ---------- 窄屏：收起品牌区，只留表单 ---------- */
@media screen and (max-width: 900px) {
  .login-brand {
    display: none;
  }

  .login-panel {
    flex: 1 1 auto;
  }
}

/* ---------- 暗色 ---------- */
html.dark {
  .login {
    background-color: var(--tz-paper);
  }

  .login-brand {
    background-color: #131d19;
  }

  .login-panel {
    background-color: var(--tz-paper);
  }
}
</style>
