import { useRef, useState, type FormEvent } from 'react'
import type { AddBookingGuestRequest } from '../../types/reservation'

interface Props {
  registeredCount: number
  guestCount: number
  hasPrimaryGuest: boolean
  disabled: boolean
  onRegister: (request: AddBookingGuestRequest) => Promise<boolean>
}

const emptyProfile = {
  firstName: '',
  lastName: '',
  phone: '',
  email: '',
  nationality: '',
  documentType: '',
  documentNumber: '',
}

export function BookingGuestRegistration({
  registeredCount,
  guestCount,
  hasPrimaryGuest,
  disabled,
  onRegister,
}: Props) {
  const [profile, setProfile] = useState(emptyProfile)
  const [submitting, setSubmitting] = useState(false)
  const submitLock = useRef(false)
  const isPrimary = !hasPrimaryGuest
  const isFull = registeredCount >= guestCount

  function update(field: keyof typeof emptyProfile, value: string) {
    setProfile((current) => ({ ...current, [field]: value }))
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitLock.current || disabled || isFull) return

    submitLock.current = true
    setSubmitting(true)
    try {
      const saved = await onRegister({
        role: isPrimary ? 'PRIMARY' : 'ACCOMPANYING',
        profile: {
          firstName: profile.firstName.trim(),
          lastName: profile.lastName.trim(),
          phone: profile.phone.trim() || undefined,
          email: profile.email.trim() || undefined,
          nationality: profile.nationality.trim() || undefined,
          documentType: profile.documentType.trim() || undefined,
          documentNumber: profile.documentNumber.trim() || undefined,
        },
      })
      if (saved) setProfile(emptyProfile)
    } finally {
      submitLock.current = false
      setSubmitting(false)
    }
  }

  return (
    <article className="panel guest-registration">
      <div className="panel-heading">
        <div>
          <h3>登记住客</h3>
          <p>使用酒店的正式住客资料办理入住。</p>
        </div>
        <strong>{registeredCount} / {guestCount}</strong>
      </div>
      {isFull ? (
        <p className="workflow-note">已达到此预订的住客人数上限。</p>
      ) : (
        <form className="form-grid" onSubmit={(event) => void submit(event)}>
          <p className="wide guest-role-note">
            本次登记：{isPrimary ? '主要住客（每笔预订一位）' : '同行住客'}
          </p>
          <label>
            名 *
            <input required maxLength={80} value={profile.firstName} onChange={(event) => update('firstName', event.target.value)} />
          </label>
          <label>
            姓 *
            <input required maxLength={80} value={profile.lastName} onChange={(event) => update('lastName', event.target.value)} />
          </label>
          <label>
            电话
            <input type="tel" maxLength={30} value={profile.phone} onChange={(event) => update('phone', event.target.value)} />
          </label>
          <label>
            邮箱
            <input type="email" maxLength={120} value={profile.email} onChange={(event) => update('email', event.target.value)} />
          </label>
          <label>
            国籍 {isPrimary ? '*' : ''}
            <input required={isPrimary} maxLength={80} value={profile.nationality} onChange={(event) => update('nationality', event.target.value)} />
          </label>
          <label>
            证件类型 {isPrimary ? '*' : ''}
            <input required={isPrimary} maxLength={40} value={profile.documentType} onChange={(event) => update('documentType', event.target.value)} />
          </label>
          <label className="wide">
            证件号码 {isPrimary ? '*' : ''}
            <input required={isPrimary} maxLength={100} value={profile.documentNumber} onChange={(event) => update('documentNumber', event.target.value)} />
          </label>
          <div className="wide actions">
            <button className="primary-button" type="submit" disabled={disabled || submitting}>
              {submitting ? '登记中…' : '保存住客登记'}
            </button>
          </div>
        </form>
      )}
    </article>
  )
}
