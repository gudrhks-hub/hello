import { useEffect, useState } from 'react'
import { errorMessage } from '../api/errors'

export type ApiData<T> =
  | { state: 'loading' }
  | { state: 'error'; message: string }
  | { state: 'success'; data: T }

export function useApiData<T>(fetcher: () => Promise<T>): ApiData<T> {
  const [result, setResult] = useState<ApiData<T>>({ state: 'loading' })
  useEffect(() => {
    let active = true
    fetcher().then(
      (data) => active && setResult({ state: 'success', data }),
      (e) => active && setResult({ state: 'error', message: errorMessage(e) }),
    )
    return () => {
      active = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  return result
}
