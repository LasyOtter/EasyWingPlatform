import type { MockMethod } from 'vite-plugin-mock'

const loginMock: MockMethod = {
  url: '/api/auth/login',
  method: 'post',
  response: () => ({
    code: 200,
    msg: '操作成功',
    data: {
      token: 'mock-token-' + Date.now()
    }
  })
}

export default [loginMock]
