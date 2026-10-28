import { useAuthStore } from '../stores/authStore'

export default function HomePage() {
  const user = useAuthStore((s) => s.user)
  return (
    <section>
      <h1>홈</h1>
      {user ? <p>{user.userName}님 환영합니다</p> : <p>로그인하면 마이페이지, 장바구니, 구매내역을 이용할 수 있습니다.</p>}
    </section>
  )
}
