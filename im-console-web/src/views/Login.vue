<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <h1>IM管理后台</h1>
        <p>即时通讯系统管理平台</p>
      </div>
      
      <el-form
        ref="formRef"
        :model="loginForm"
        :rules="rules"
        class="login-form"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入用户名"
            prefix-icon="User"
            size="large"
          />
        </el-form-item>
        
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            prefix-icon="Lock"
            size="large"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="login-btn"
            :loading="loading"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form-item>

        <el-form-item>
          <el-button
            type="success"
            size="large"
            class="login-btn"
            @click="handleTestLogin"
          >
            测试登录（无密码）
          </el-button>
        </el-form-item>
      </el-form>
      
      <div class="login-tips">
        <p>提示：默认账号 admin / 123456</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { login } from '@/api'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)

const loginForm = reactive({
  username: 'admin',
  password: '123456'
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        // 调用登录接口（通过vite代理转发到服务器47.93.209.60:8092）
        const result = await login(loginForm)

        // 后端返回 code=1 表示成功
        if (result.code === 1 && result.data) {
          // 保存token和用户信息
          localStorage.setItem('token', result.data.token)
          localStorage.setItem('adminId', result.data.adminId)
          localStorage.setItem('adminName', result.data.username)
          localStorage.setItem('realName', result.data.realName || '')
          localStorage.setItem('roleCode', result.data.roleCode || '')

          ElMessage.success('登录成功')
          router.push('/dashboard')
        } else {
          ElMessage.error(result.msg || '登录失败')
        }
      } catch (error) {
        console.error('登录失败:', error)
        ElMessage.error('登录失败，请检查网络连接')
      } finally {
        loading.value = false
      }
    }
  })
}

// 测试登录（无密码验证）- 用于调试
const handleTestLogin = async () => {
  loading.value = true
  try {
    // 测试登录接口
    const result = await login({ username: 'admin', password: 'admin123' })

    // 后端返回 code=1 表示成功
    if (result.code === 1 && result.data) {
      localStorage.setItem('token', result.data.token)
      localStorage.setItem('adminId', result.data.adminId)
      localStorage.setItem('adminName', result.data.username)
      localStorage.setItem('realName', result.data.realName || '')
      localStorage.setItem('roleCode', result.data.roleCode || '')

      ElMessage.success('测试登录成功')
      router.push('/dashboard')
    } else {
      ElMessage.error(result.msg || '测试登录失败')
    }
  } catch (error) {
    console.error('测试登录失败:', error)
    ElMessage.error('测试登录失败，请检查服务器连接')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  width: 100vw;
  height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-box {
  width: 400px;
  padding: 40px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
}

.login-header {
  text-align: center;
  margin-bottom: 30px;
}

.login-header h1 {
  font-size: 28px;
  color: #303133;
  margin: 0;
}

.login-header p {
  font-size: 14px;
  color: #909399;
  margin-top: 8px;
}

.login-form {
  margin-top: 20px;
}

.login-btn {
  width: 100%;
}

.login-tips {
  text-align: center;
  margin-top: 20px;
}

.login-tips p {
  font-size: 12px;
  color: #909399;
}
</style>
