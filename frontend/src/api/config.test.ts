import test from 'node:test'
import assert from 'node:assert/strict'
import { resolveApiBaseUrl } from './config.ts'

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
