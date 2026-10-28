import { useState } from 'react'
import { createOrder } from '../api/orders'
import { errorMessage } from '../api/errors'
import { ErrorMessage } from '../components/Feedback'

export default function CheckoutPage() {
  const [pending, setPending] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  const onBuy = async () => {
    setPending(true)
    setMessage(null)
    try {
      await createOrder()
      setMessage('구매가 완료되었습니다.')
    } catch (e) {
      setMessage(errorMessage(e)) // 501이면 "준비 중인 기능입니다."
    } finally {
      setPending(false)
    }
  }

  return (
    <section>
      <h1>구매하기</h1>
      <button type="button" onClick={onBuy} disabled={pending}>
        {pending ? '처리 중...' : '구매하기'}
      </button>
      {message && <ErrorMessage message={message} />}
    </section>
  )
}
