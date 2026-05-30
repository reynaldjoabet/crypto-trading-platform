package trading.domain

import trading.domain.ids.*
import trading.domain.money.*
import java.time.Instant

object accounts {

  enum AccountKind derives CanEqual {
    case Cash, Trading, Reserve, FeesPayable, ExchangeOmnibus, CustodyOmnibus
  }

  /** A user-facing account is *one* per (user, currency). System accounts (omnibus, P&L) are not tied to a user.
    */
  final case class Account(
      id: AccountId,
      userId: Option[UserId],
      kind: AccountKind,
      currency: CurrencyCode,
      createdAt: Instant
  ) derives CanEqual

  enum EntryDirection derives CanEqual {
    case Debit, Credit
  }

  /** A double-entry ledger entry. Always written paired (debit + credit) inside one transaction so the books balance.
    */
  final case class LedgerEntry(
      id: LedgerEntryId,
      accountId: AccountId,
      direction: EntryDirection,
      amount: Amount,
      currency: CurrencyCode,
      reference: String, // e.g. "order:<uuid>", "deposit:<uuid>"
      postedAt: Instant
  ) derives CanEqual

  final case class Balance(
      accountId: AccountId,
      currency: CurrencyCode,
      available: BigDecimal, // signed; ledger sum can be negative for liability accounts
      pending: BigDecimal
  ) derives CanEqual

}
