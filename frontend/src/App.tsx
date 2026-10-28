import { useEffect } from 'react'
import AppRoutes from './routes/router'
import { useAuthStore } from './stores/authStore'

export default function App() {
  useEffect(() => {
    // bootstrap은 내부 가드로 StrictMode 이중 호출에도 refresh 1회만 전송
    void useAuthStore.getState().bootstrap()
  }, [])
  return <AppRoutes />
}
