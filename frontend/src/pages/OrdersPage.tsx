import { getOrders } from '../api/orders'
import { Empty, ErrorMessage, Loading } from '../components/Feedback'
import { useApiData } from '../components/useApiData'

export default function OrdersPage() {
  const r = useApiData(getOrders)
  return (
    <section>
      <h1>구매내역</h1>
      {r.state === 'loading' && <Loading />}
      {r.state === 'error' && <ErrorMessage message={r.message} />}
      {r.state === 'success' &&
        (r.data.items.length === 0 ? (
          <Empty>구매 내역이 없습니다.</Empty>
        ) : (
          <p>총 {r.data.totalCount}건</p>
        ))}
    </section>
  )
}
