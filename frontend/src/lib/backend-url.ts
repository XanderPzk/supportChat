const DEFAULT_BACKEND_HTTP_URL = 'http://127.0.0.1:8642'

function normalizeBaseUrl(raw: string) {
  return raw.endsWith('/') ? raw.slice(0, -1) : raw
}

export function getBackendHttpBase() {
  const configured = import.meta.env.VITE_BACKEND_HTTP_URL?.trim()
  if (!configured) {
    return DEFAULT_BACKEND_HTTP_URL
  }
  if (configured === 'same-origin') {
    return window.location.origin
  }
  return normalizeBaseUrl(configured)
}

export function buildApiUrl(path: string) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${getBackendHttpBase()}${normalizedPath}`
}

export function buildWebSocketUrl(path: string, query: URLSearchParams) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  const baseUrl = new URL(getBackendHttpBase())
  const wsProtocol = baseUrl.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${wsProtocol}//${baseUrl.host}${normalizedPath}?${query.toString()}`
}
