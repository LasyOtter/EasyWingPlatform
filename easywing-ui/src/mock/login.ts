import { defineMock } from 'vite-plugin-mock'

export default defineMock({
  url: '/api/auth/login',
  method: 'post',
  response: {
    code: 200,
    msg: '操作成功',
    data: {
      token: 'mock-token-' + Date.now()
    }
  }
})
