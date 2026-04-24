import { createContext, useContext, useState, useCallback } from 'react'

const AuthContext = createContext(null)

const API = 'http://localhost:8080/api/auth'

export function AuthProvider({ children }) {
  const [user,  setUser]  = useState(() => {
    try {
      const stored = localStorage.getItem('auth_user')
      return stored ? JSON.parse(stored) : null
    } catch { return null }
  })
  const [token, setToken] = useState(() => localStorage.getItem('auth_token') || null)

  const login = useCallback(async (username, password) => {
    const res  = await fetch(`${API}/login`, {
      method:  'POST',
      headers: { 'Content-Type': 'application/json' },
      body:    JSON.stringify({ username, password }),
    })
    const data = await res.json()
    if (!data.success) throw new Error(data.message)

    const { token, ...userInfo } = data.data
    localStorage.setItem('auth_token', token)
    localStorage.setItem('auth_user',  JSON.stringify(userInfo))
    setToken(token)
    setUser(userInfo)
    return userInfo
  }, [])

  const register = useCallback(async (username, email, password) => {
    const res  = await fetch(`${API}/register`, {
      method:  'POST',
      headers: { 'Content-Type': 'application/json' },
      body:    JSON.stringify({ username, email, password }),
    })
    const data = await res.json()
    if (!data.success) throw new Error(data.message)

    const { token, ...userInfo } = data.data
    localStorage.setItem('auth_token', token)
    localStorage.setItem('auth_user',  JSON.stringify(userInfo))
    setToken(token)
    setUser(userInfo)
    return userInfo
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem('auth_token')
    localStorage.removeItem('auth_user')
    setToken(null)
    setUser(null)
  }, [])

  return (
    <AuthContext.Provider value={{ user, token, login, register, logout, isLoggedIn: !!token }}>
      {children}
    </AuthContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export const useAuth = () => {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}