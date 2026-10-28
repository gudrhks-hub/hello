import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { z } from 'zod'
import { ApiError, toApiError } from '../api/errors'
import { ErrorMessage } from '../components/Feedback'
import { safeRedirect } from '../routes/redirect'
import { useAuthStore } from '../stores/authStore'

const schema = z.object({
  userId: z
    .string()
    .trim()
    .min(1, '아이디를 입력해 주세요.')
    .max(50, '아이디는 50자 이하로 입력해 주세요.'),
  password: z
    .string()
    .min(1, '비밀번호를 입력해 주세요.')
    .max(100, '비밀번호는 100자 이하로 입력해 주세요.'),
})
type FormValues = z.infer<typeof schema>

export default function LoginPage() {
  const [params] = useSearchParams()
  const redirect = safeRedirect(params.get('redirect'))
  const navigate = useNavigate()
  const status = useAuthStore((s) => s.status)
  const login = useAuthStore((s) => s.login)
  const [formError, setFormError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { userId: '', password: '' } })

  if (status === 'authenticated') return <Navigate to={redirect} replace />

  const onSubmit = async (values: FormValues) => {
    setFormError(null)
    try {
      await login({ userId: values.userId.trim(), password: values.password })
      navigate(redirect, { replace: true })
    } catch (e) {
      const err: ApiError = toApiError(e)
      if (err.code === 'VALIDATION_ERROR' && err.fieldErrors.length > 0) {
        let unmatched = false
        for (const fe of err.fieldErrors) {
          if (fe.field === 'userId' || fe.field === 'password') {
            setError(fe.field, { type: 'server', message: fe.message })
          } else unmatched = true
        }
        if (unmatched) setFormError(err.message)
      } else {
        setFormError(err.message)
      }
    }
  }

  return (
    <section>
      <h1>로그인</h1>
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="form">
        {formError && <ErrorMessage message={formError} />}
        <div className="field">
          <label htmlFor="userId">아이디</label>
          <input
            id="userId"
            type="text"
            autoComplete="username"
            aria-invalid={!!errors.userId}
            aria-describedby={errors.userId ? 'userId-error' : undefined}
            {...register('userId')}
          />
          {errors.userId && (
            <span id="userId-error" className="error" role="alert">
              {errors.userId.message}
            </span>
          )}
        </div>
        <div className="field">
          <label htmlFor="password">비밀번호</label>
          <input
            id="password"
            type="password"
            autoComplete="current-password"
            aria-invalid={!!errors.password}
            aria-describedby={errors.password ? 'password-error' : undefined}
            {...register('password')}
          />
          {errors.password && (
            <span id="password-error" className="error" role="alert">
              {errors.password.message}
            </span>
          )}
        </div>
        <button type="submit" disabled={isSubmitting}>
          {isSubmitting ? '로그인 중...' : '로그인'}
        </button>
      </form>
    </section>
  )
}
