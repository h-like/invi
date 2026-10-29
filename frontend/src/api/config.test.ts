import test from 'node:test'
import assert from 'node:assert/strict'
import { resolveApiBaseUrl, resolveApiSameOrigin, resolveRegistrationEnabled, resolveSocialLoginEnabled } from './config.ts'

test('API origin defaults to localhost when unset or blank', () => {
  for (const value of [undefined, '', '   ']) assert.equal(resolveApiBaseUrl(value), 'http://localhost:8080')
})
test('API origin accepts an explicit LAN or HTTPS server', () => {
  assert.equal(resolveApiBaseUrl('http://192.168.0.10:8080'), 'http://192.168.0.10:8080')
  assert.equal(resolveApiBaseUrl('https://api.example.com'), 'https://api.example.com')
})
test('API origin normalizes whitespace, hostname, root slash and default ports', () => {
  assert.equal(resolveApiBaseUrl('  HTTP://LOCALHOST:8080/  '), 'http://localhost:8080')
  assert.equal(resolveApiBaseUrl('https://api.example.com:443/'), 'https://api.example.com')
})
test('API origin rejects relative, unsafe, malformed and invalid-port values', () => {
  for (const value of ['/api', '//example.com', 'javascript:alert(1)', 'file:///tmp/api', 'ftp://example.com', 'http://localhost:99999', 'not a url']) {
    assert.throws(() => resolveApiBaseUrl(value), /VITE_API_BASE_URL/)
  }
})
test('API origin rejects credentials, query strings and fragments', () => {
  for (const value of ['http://user:password@example.com', 'https://api.example.com?token=example', 'https://api.example.com#fragment']) {
    assert.throws(() => resolveApiBaseUrl(value), /VITE_API_BASE_URL/)
  }
})
test('API origin rejects path prefixes to avoid silently sending auth to the wrong route', () => {
  for (const value of ['http://localhost:8080/api', 'https://api.example.com/backend/']) assert.throws(() => resolveApiBaseUrl(value), /VITE_API_BASE_URL/)
})


test('normal API origins preserve HTTP, IPv6, uppercase, whitespace and IDN support', () => {
  for (const [value, expected] of [
    ['http://api.example.com', 'http://api.example.com'],
    ['http://192.168.0.10:8080/', 'http://192.168.0.10:8080'],
    ['http://[::1]:8080/', 'http://[::1]:8080'],
    [' \tHTTP://LOCALHOST:8080/\r\n ', 'http://localhost:8080'],
    ['HTTPS://API.EXAMPLE.COM:443/', 'https://api.example.com'],
    ['https://bücher.example', 'https://xn--bcher-kva.example'],
  ]) {
    assert.equal(resolveApiBaseUrl(value), expected)
  }
})

test('normal API origins reject syntax that WHATWG URL would normalize away', () => {
  for (const value of [
    'https://api.example.com/a/..', 'https://api.example.com/.', 'https://api.example.com/%2e',
    'https://api.example.com/a/%2e%2e', 'https://api.example.com//',
    'https://@api.example.com', 'https://:@api.example.com',
    'https://api.example.com?', 'https://api.example.com#',
    'https:api.example.com', 'https:///api.example.com',
    'https://api.example.com\\', 'https://api.example.com\\a\\..',
    'https://api.exa\tmple.com', 'https://api.exa\nmple.com', 'https://api.exa\rmple.com',
    'https://api.example.com:\t443', 'https://api.example.com/\n/',
    'https://api.exa\u0000mple.com', 'https://api.exa\u001fmple.com',
    'https://api.exa\u007fmple.com', 'https://api.exa\u0085mple.com',
    'http://localhost:8080/a/..', 'http://@192.168.0.10:8080',
  ]) {
    assert.throws(() => resolveApiBaseUrl(value), /VITE_API_BASE_URL/)
  }
})

test('normal API origin errors stay generic for rejected raw syntax', () => {
  const genericError = 'VITE_API_BASE_URL에는 경로나 인증정보 없이 http 또는 https API 주소를 입력해 주세요.'
  for (const value of [
    'https://api.example.com/do-not-echo/..',
    'https://do-not-echo@api.example.com',
    'https://api.example.com\\do-not-echo\\..',
    'https://api.example.com?secret=do-not-echo',
  ]) {
    assert.throws(() => resolveApiBaseUrl(value), (error: unknown) => {
      assert.ok(error instanceof Error)
      assert.equal(error.message, genericError)
      assert.doesNotMatch(error.message, /do-not-echo|secret=/)
      return true
    })
  }
})

test('normal mode ignores current origin and preserves localhost and explicit LAN configuration', () => {
  assert.equal(resolveApiBaseUrl(undefined, { currentOrigin: 'https://invitation.example.com' }), 'http://localhost:8080')
  assert.equal(resolveApiBaseUrl('', { sameOrigin: false, currentOrigin: 'invalid' }), 'http://localhost:8080')
  assert.equal(resolveApiBaseUrl('http://192.168.0.10:8080', { sameOrigin: false }), 'http://192.168.0.10:8080')
})

test('same-origin mode uses HTTPS for undefined or blank explicit API configuration', () => {
  for (const value of [undefined, '', '   ']) {
    assert.equal(resolveApiBaseUrl(value, { sameOrigin: true, currentOrigin: 'https://invite.example.com' }), 'https://invite.example.com')
  }
  assert.equal(resolveApiBaseUrl('', { sameOrigin: true, currentOrigin: ' HTTPS://INVITE.EXAMPLE.COM:443/ ' }), 'https://invite.example.com')
  assert.equal(resolveApiBaseUrl('', { sameOrigin: true, currentOrigin: 'https://invite.example.com:8443' }), 'https://invite.example.com:8443')
})

test('same-origin mode permits HTTP only for exact loopback host spellings', () => {
  for (const origin of ['http://localhost', 'http://localhost:8080', 'http://127.0.0.1:8080', 'http://[::1]:8080']) {
    assert.equal(resolveApiBaseUrl(undefined, { sameOrigin: true, currentOrigin: origin }), origin)
  }
  assert.equal(resolveApiBaseUrl('', { sameOrigin: true, currentOrigin: 'http://LOCALHOST:80/' }), 'http://localhost')
})

test('same-origin mode rejects every nonblank explicit API URL, including a matching origin', () => {
  for (const value of ['https://invite.example.com', ' https://invite.example.com/ ', 'http://localhost:8080', '/api', 'invalid']) {
    assert.throws(() => resolveApiBaseUrl(value, { sameOrigin: true, currentOrigin: 'https://invite.example.com' }), /VITE_API_BASE_URL/)
  }
})

test('same-origin mode rejects missing origins instead of falling back to localhost', () => {
  for (const currentOrigin of [undefined, '', '   ']) {
    assert.throws(() => resolveApiBaseUrl(undefined, { sameOrigin: true, currentOrigin }), /동일 출처 API/)
  }
})

test('same-origin mode rejects public, LAN and nonexact loopback HTTP origins', () => {
  for (const currentOrigin of [
    'http://example.com', 'http://192.168.0.10:8080', 'http://10.0.0.2', 'http://172.16.0.2',
    'http://[fd00::1]', 'http://0.0.0.0:8080', 'http://127.0.0.2', 'http://127.1',
    'http://2130706433', 'http://0x7f000001', 'http://0177.0.0.1', 'http://localhost.',
    'http://localhost.example.com', 'http://[0:0:0:0:0:0:0:1]', 'http://[::ffff:127.0.0.1]',
  ]) {
    assert.throws(() => resolveApiBaseUrl('', { sameOrigin: true, currentOrigin }), /동일 출처 API/)
  }
})

test('same-origin mode rejects unsafe, relative and malformed origins', () => {
  for (const currentOrigin of [
    '/api', '//example.com', 'null', 'not a url', 'file:///tmp/api', 'ftp://example.com',
    'javascript:alert(1)', 'https://', 'https://example.com:99999', 'https:example.com',
    'https:///example.com', 'https://exam ple.com', 'https://example.com\\path',
  ]) {
    assert.throws(() => resolveApiBaseUrl('', { sameOrigin: true, currentOrigin }), /동일 출처 API/)
  }
})

test('same-origin mode rejects credentials, paths, queries and fragments before URL normalization', () => {
  for (const currentOrigin of [
    'https://user:password@example.com', 'https://user@example.com', 'https://@example.com',
    'https://example.com/api', 'https://example.com/api/', 'https://example.com/.',
    'https://example.com/api/..', 'https://example.com/%2e', 'https://example.com//',
    'https://example.com?token=example', 'https://example.com#fragment',
    'https://example.com?', 'https://example.com#',
  ]) {
    assert.throws(() => resolveApiBaseUrl('', { sameOrigin: true, currentOrigin }), /동일 출처 API/)
  }
})

test('configuration errors never echo supplied origins or flag values', () => {
  const secretOrigin = 'https://user:do-not-echo@example.com/private?token=private'
  const errors = [
    () => resolveApiBaseUrl(secretOrigin),
    () => resolveApiBaseUrl(secretOrigin, { sameOrigin: true, currentOrigin: secretOrigin }),
    () => resolveApiBaseUrl('', { sameOrigin: true, currentOrigin: secretOrigin }),
    () => resolveApiSameOrigin('do-not-echo'),
    () => resolveSocialLoginEnabled('https-test', 'do-not-echo'),
  ]
  for (const operation of errors) {
    assert.throws(operation, (error: unknown) => {
      assert.ok(error instanceof Error)
      assert.doesNotMatch(error.message, /do-not-echo|user:|token=private/)
      return true
    })
  }
})

test('same-origin flag defaults to false and accepts only explicit true or false', () => {
  for (const value of [undefined, '', '   ', 'false', ' false ']) assert.equal(resolveApiSameOrigin(value), false)
  for (const value of ['true', ' true ']) assert.equal(resolveApiSameOrigin(value), true)
  for (const value of ['TRUE', 'False', '1', '0', 'yes', 'no', 'on', 'off', 'null']) {
    assert.throws(() => resolveApiSameOrigin(value), /VITE_API_SAME_ORIGIN/)
  }
})

test('social login is enabled by default in normal modes and disabled by an explicit false flag', () => {
  for (const mode of ['development', 'production', 'test', 'https-test']) {
    for (const value of [undefined, '', '   ', 'true', ' true ']) assert.equal(resolveSocialLoginEnabled(mode, value), true)
    for (const value of ['false', ' false ']) assert.equal(resolveSocialLoginEnabled(mode, value), false)
  }
})

test('mobile mode never enables social login, including when the public flag is true', () => {
  for (const value of [undefined, '', '   ', 'true', ' true ', 'false']) assert.equal(resolveSocialLoginEnabled('mobile', value), false)
})

test('social login rejects invalid boolean values in every mode, including mobile', () => {
  for (const mode of ['development', 'production', 'https-test', 'mobile']) {
    for (const value of ['TRUE', 'False', '1', '0', 'yes', 'no', 'on', 'off', 'null']) {
      assert.throws(() => resolveSocialLoginEnabled(mode, value), /VITE_SOCIAL_LOGIN_ENABLED/)
    }
  }
})


test('registration defaults to enabled and accepts only explicit true or false', () => {
  for (const value of [undefined, '', '   ', 'true', ' true ']) assert.equal(resolveRegistrationEnabled(value), true)
  for (const value of ['false', ' false ']) assert.equal(resolveRegistrationEnabled(value), false)
})

test('registration rejects invalid flags with a generic non-echoing error', () => {
  for (const value of ['TRUE', 'False', '1', '0', 'yes', 'no', 'on', 'off', 'null', 'do-not-echo']) {
    assert.throws(() => resolveRegistrationEnabled(value), (error: unknown) => {
      assert.ok(error instanceof Error)
      assert.equal(error.message, 'VITE_REGISTRATION_ENABLED에는 true 또는 false만 입력해 주세요.')
      assert.doesNotMatch(error.message, /do-not-echo/)
      return true
    })
  }
})
