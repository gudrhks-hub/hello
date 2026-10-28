import { getCart } from '../api/cart'
import { Empty, ErrorMessage, Loading } from '../components/Feedback'
import { useApiData } from '../components/useApiData'

export default function CartPage() {
  const r = useApiData(getCart)
  return (
    <section>
      <h1>장바구니</h1>
      {r.state === 'loading' && <Loading />}
      {r.state === 'error' && <ErrorMessage message={r.message} />}
      {r.state === 'success' &&
        (r.data.items.length === 0 ? (
          <Empty>장바구니가 비어 있습니다.</Empty>
        ) : (
          <p>총 {r.data.totalCount}건</p>
        ))}
    </section>
  )
}
