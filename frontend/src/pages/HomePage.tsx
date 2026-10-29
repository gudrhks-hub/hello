import { useAuthStore } from '../stores/authStore'

export default function HomePage() {
  const user = useAuthStore((s) => s.user)
  return (
    <section>
      <h1>홈</h1>
      {user ? <p>{user.userName}님 환영합니다</p> : <p>로그인해 주세요.</p>}
    </section>
  )
}
