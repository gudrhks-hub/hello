import { getMe } from '../api/users'
import { ErrorMessage, Loading } from '../components/Feedback'
import { useApiData } from '../components/useApiData'

export default function MyPage() {
  const r = useApiData(getMe)
  return (
    <section>
      <h1>마이페이지</h1>
      {r.state === 'loading' && <Loading />}
      {r.state === 'error' && <ErrorMessage message={r.message} />}
      {r.state === 'success' && (
        <dl className="info">
          <dt>아이디</dt>
          <dd>{r.data.userId}</dd>
          <dt>이름</dt>
          <dd>{r.data.userName}</dd>
          <dt>성별</dt>
          <dd>{r.data.gender}</dd>
          <dt>생년월일</dt>
          <dd>{r.data.birth}</dd>
          <dt>이메일</dt>
          <dd>{r.data.email}</dd>
          <dt>연락처</dt>
          <dd>{r.data.phone}</dd>
          <dt>우편번호</dt>
          <dd>{r.data.zipCode}</dd>
          <dt>주소</dt>
          <dd>{r.data.addr}</dd>
          <dt>상세주소</dt>
          <dd>{r.data.addrDtl ?? '-'}</dd>
          <dt>권한</dt>
          <dd>{r.data.roleName ?? r.data.role}</dd>
          <dt>가입일</dt>
          <dd>{r.data.createAt ? new Date(r.data.createAt).toLocaleDateString('ko-KR') : '-'}</dd>
        </dl>
      )}
    </section>
  )
}
