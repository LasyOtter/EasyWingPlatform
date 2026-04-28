/**
 * 验证手机号
 */
export function validatePhone(phone: string): boolean {
  const reg = /^1[3-9]\d{9}$/
  return reg.test(phone)
}

/**
 * 验证邮箱
 */
export function validateEmail(email: string): boolean {
  const reg = /^[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,6}$/
  return reg.test(email)
}

/**
 * 验证身份证号
 */
export function validateIdCard(idCard: string): boolean {
  const reg = /(^\d{15}$)|(^\d{18}$)|(^\d{17}(\d|X|x)$)/
  return reg.test(idCard)
}

/**
 * 验证 URL
 */
export function validateUrl(url: string): boolean {
  const reg = /^(https?|ftp):\/\/([a-zA-Z0-9.-]+\.)+[a-zA-Z]{2,}(\/\S*)?$/
  return reg.test(url)
}

/**
 * 验证 IP 地址
 */
export function validateIP(ip: string): boolean {
  const reg = /^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/
  return reg.test(ip)
}

/**
 * 验证端口号
 */
export function validatePort(port: number | string): boolean {
  const num = Number(port)
  return Number.isInteger(num) && num >= 0 && num <= 65535
}

/**
 * 验证密码强度
 * 至少包含大写字母、小写字母、数字、特殊字符中的三种，长度8-30位
 */
export function validatePassword(password: string): boolean {
  const reg = /^(?![a-zA-Z]+$)(?![A-Z0-9]+$)(?![A-Z!@#$%^&*]+$)(?![a-z0-9!@#$%^&*]+$)(?![a-zA-Z0-9]+$)[a-zA-Z0-9!@#$%^&*]{8,30}$/
  return reg.test(password)
}

/**
 * 验证用户名
 * 只能包含字母、数字、下划线，长度4-20位
 */
export function validateUsername(username: string): boolean {
  const reg = /^[a-zA-Z0-9_]{4,20}$/
  return reg.test(username)
}

/**
 * 去除字符串空格
 */
export function trim(str: string): string {
  return str.replace(/^\s+|\s+$/g, '')
}

/**
 * 验证是否为空
 */
export function isEmpty(value: any): boolean {
  if (value === null || value === undefined) return true
  if (typeof value === 'string') return trim(value) === ''
  if (Array.isArray(value)) return value.length === 0
  if (typeof value === 'object') return Object.keys(value).length === 0
  return false
}
