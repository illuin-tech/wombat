import type { UserRole } from '@/domain/access'
import { asUserDate } from '@/services/format/formatters'

export function roleSourceText(role: UserRole): string {
  if (!role.grant) return 'From the identity provider or auth.roles configuration'
  const grant = `Granted in Wombat by ${role.grant.grantedBy} on ${asUserDate(role.grant.grantedAt)}`
  return role.fromProvider ? `${grant}, also from the identity provider or configuration` : grant
}
