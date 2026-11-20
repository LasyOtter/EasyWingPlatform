import { describe, expect, it } from 'vitest'
import {
  isEmpty,
  trim,
  validateEmail,
  validateIdCard,
  validateIP,
  validatePassword,
  validatePhone,
  validatePort,
  validateUrl,
  validateUsername
} from './validate'

describe('validatePhone', () => {
  it('接受合法手机号', () => {
    expect(validatePhone('13800138000')).toBe(true)
    expect(validatePhone('19912345678')).toBe(true)
  })

  it('拒绝位数不对或号段非法的号码', () => {
    expect(validatePhone('1380013800')).toBe(false)
    expect(validatePhone('12800138000')).toBe(false)
    expect(validatePhone('23800138000')).toBe(false)
    expect(validatePhone('1380013800a')).toBe(false)
    expect(validatePhone('')).toBe(false)
  })
})

describe('validateEmail', () => {
  it('接受合法邮箱', () => {
    expect(validateEmail('user@example.com')).toBe(true)
    expect(validateEmail('a.b-c@sub.domain.cn')).toBe(true)
  })

  it('拒绝缺少域名或后缀过短的邮箱', () => {
    expect(validateEmail('user@example')).toBe(false)
    expect(validateEmail('user@.com')).toBe(false)
    expect(validateEmail('user@example.c')).toBe(false)
    expect(validateEmail('user.example.com')).toBe(false)
  })
})

describe('validateIdCard', () => {
  it('接受 15/18 位身份证', () => {
    expect(validateIdCard('11010519491231002X')).toBe(true)
    expect(validateIdCard('110105491231002')).toBe(true)
    expect(validateIdCard('11010519491231002x')).toBe(true)
  })

  it('拒绝位数或格式错误', () => {
    expect(validateIdCard('11010519491231002')).toBe(false)
    expect(validateIdCard('11010519491231002Y')).toBe(false)
  })
})

describe('validateIP', () => {
  it('接受合法 IPv4', () => {
    expect(validateIP('192.168.1.1')).toBe(true)
    expect(validateIP('0.0.0.0')).toBe(true)
    expect(validateIP('255.255.255.255')).toBe(true)
  })

  it('拒绝越界或非 IPv4', () => {
    expect(validateIP('256.1.1.1')).toBe(false)
    expect(validateIP('192.168.1')).toBe(false)
    expect(validateIP('::1')).toBe(false)
  })
})

describe('validatePort', () => {
  it('接受 0-65535 的整数', () => {
    expect(validatePort(8080)).toBe(true)
    expect(validatePort('8080')).toBe(true)
    expect(validatePort(0)).toBe(true)
    expect(validatePort(65535)).toBe(true)
  })

  it('拒绝越界、小数与非数字', () => {
    expect(validatePort(65536)).toBe(false)
    expect(validatePort(-1)).toBe(false)
    expect(validatePort(80.5)).toBe(false)
    expect(validatePort('abc')).toBe(false)
  })
})

describe('validateUrl', () => {
  it('接受 http/https/ftp', () => {
    expect(validateUrl('https://example.com')).toBe(true)
    expect(validateUrl('http://example.com/path?q=1')).toBe(true)
    expect(validateUrl('ftp://files.example.com')).toBe(true)
  })

  it('拒绝缺少 scheme 或主机名不合法', () => {
    expect(validateUrl('example.com')).toBe(false)
    expect(validateUrl('http://')).toBe(false)
  })
})

describe('validatePassword', () => {
  it('接受包含三类字符的密码', () => {
    expect(validatePassword('Abcdef12')).toBe(true)
    expect(validatePassword('Abcdef!@')).toBe(true)
    expect(validatePassword('abcdef12!')).toBe(true)
  })

  it('拒绝纯字母、纯数字或长度不符', () => {
    expect(validatePassword('Abcdefgh')).toBe(false)
    expect(validatePassword('12345678')).toBe(false)
    expect(validatePassword('Abc123')).toBe(false)
  })
})

describe('validateUsername', () => {
  it('接受字母数字下划线且长度 4-20', () => {
    expect(validateUsername('admin')).toBe(true)
    expect(validateUsername('user_2026')).toBe(true)
  })

  it('拒绝含非法字符或长度不符', () => {
    expect(validateUsername('abc')).toBe(false)
    expect(validateUsername('user-name')).toBe(false)
    expect(validateUsername('用户名')).toBe(false)
  })
})

describe('trim', () => {
  it('去除首尾空白', () => {
    expect(trim('  hello  ')).toBe('hello')
    expect(trim('\t\nhello')).toBe('hello')
    expect(trim('hello')).toBe('hello')
  })
})

describe('isEmpty', () => {
  it('识别 null/undefined', () => {
    expect(isEmpty(null)).toBe(true)
    expect(isEmpty(undefined)).toBe(true)
  })

  it('识别空字符串与纯空白字符串', () => {
    expect(isEmpty('')).toBe(true)
    expect(isEmpty('   ')).toBe(true)
    expect(isEmpty('x')).toBe(false)
  })

  it('识别空数组与空对象', () => {
    expect(isEmpty([])).toBe(true)
    expect(isEmpty({})).toBe(true)
    expect(isEmpty([1])).toBe(false)
    expect(isEmpty({ a: 1 })).toBe(false)
  })

  it('数字 0 与布尔 false 不算空', () => {
    expect(isEmpty(0)).toBe(false)
    expect(isEmpty(false)).toBe(false)
  })
})
