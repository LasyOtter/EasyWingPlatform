import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getToken, removeToken } from '@/utils/auth'

// 创建 axios 实例
const service = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json;charset=utf-8'
  }
})

// 请求拦截器
service.interceptors.request.use(
  (config) => {
    // 添加 Token
    const token = getToken()
    if (token) {
      config.headers['X-Access-Token'] = token
    }

    // 添加时间戳防止缓存
    if (config.method === 'get') {
      config.params = {
        ...config.params,
        _t: Date.now()
      }
    }

    return config
  },
  (error) => {
    console.error('Request error:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data

    // 文件流处理
    if (response.config.responseType === 'blob') {
      return response
    }

    // 业务错误处理
    if (res.code !== 200) {
      ElMessage.error(res.msg || '请求失败')

      // Token 过期
      if (res.code === 401) {
        ElMessageBox.confirm(
          '登录状态已过期，请重新登录',
          '系统提示',
          {
            confirmButtonText: '重新登录',
            cancelButtonText: '取消',
            type: 'warning'
          }
        ).then(() => {
          removeToken()
          location.href = '/login'
        })
      }

      return Promise.reject(new Error(res.msg || '请求失败'))
    }

    return res
  },
  (error) => {
    console.error('Response error:', error)

    let message = '网络错误，请稍后重试'

    if (error.response) {
      const { status } = error.response
      switch (status) {
        case 400:
          message = '请求参数错误'
          break
        case 401:
          message = '未授权，请重新登录'
          removeToken()
          location.href = '/login'
          break
        case 403:
          message = '拒绝访问'
          break
        case 404:
          message = '请求地址不存在'
          break
        case 500:
          message = '服务器内部错误'
          break
        case 502:
          message = '网关错误'
          break
        case 503:
          message = '服务不可用'
          break
        case 504:
          message = '网关超时'
          break
        default:
          message = error.message
      }
    } else if (error.code === 'ECONNABORTED') {
      message = '请求超时'
    }

    ElMessage.error(message)
    return Promise.reject(error)
  }
)

// 封装请求方法
export function request<T = any>(config: AxiosRequestConfig): Promise<T> {
  return new Promise((resolve, reject) => {
    service
      .request<any, T>(config)
      .then((res) => {
        resolve(res)
      })
      .catch((error) => {
        reject(error)
      })
  })
}

// GET 请求
export function get<T = any>(
  url: string,
  params?: any,
  config?: AxiosRequestConfig
): Promise<T> {
  return request({
    url,
    method: 'GET',
    params,
    ...config
  })
}

// POST 请求
export function post<T = any>(
  url: string,
  data?: any,
  config?: AxiosRequestConfig
): Promise<T> {
  return request({
    url,
    method: 'POST',
    data,
    ...config
  })
}

// PUT 请求
export function put<T = any>(
  url: string,
  data?: any,
  config?: AxiosRequestConfig
): Promise<T> {
  return request({
    url,
    method: 'PUT',
    data,
    ...config
  })
}

// DELETE 请求
export function del<T = any>(
  url: string,
  params?: any,
  config?: AxiosRequestConfig
): Promise<T> {
  return request({
    url,
    method: 'DELETE',
    params,
    ...config
  })
}

export default service
