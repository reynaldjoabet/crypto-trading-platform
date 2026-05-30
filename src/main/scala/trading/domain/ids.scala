package trading.domain

import java.util.UUID

/** Opaque-typed identifiers. UUIDs at the wire level; distinct types in code. */
object ids {

  opaque type UserId = UUID
  opaque type AccountId = UUID
  opaque type WalletId = UUID
  opaque type InstrumentId = UUID
  opaque type StrategyId = UUID
  opaque type ComponentId = UUID
  opaque type OrderId = UUID
  opaque type TradeId = UUID
  opaque type LedgerEntryId = UUID
  opaque type ProjectId = UUID
  opaque type DonationId = UUID
  opaque type PostId = UUID

  object UserId {
    inline def apply(u: UUID): UserId = u
    extension (id: UserId) inline def value: UUID = id
  }

  object AccountId {
    inline def apply(u: UUID): AccountId = u
    extension (id: AccountId) inline def value: UUID = id
  }

  object WalletId {
    inline def apply(u: UUID): WalletId = u
    extension (id: WalletId) inline def value: UUID = id
  }

  object InstrumentId {
    inline def apply(u: UUID): InstrumentId = u
    extension (id: InstrumentId) inline def value: UUID = id
  }

  object StrategyId {
    inline def apply(u: UUID): StrategyId = u
    extension (id: StrategyId) inline def value: UUID = id
  }

  object ComponentId {
    inline def apply(u: UUID): ComponentId = u
    extension (id: ComponentId) inline def value: UUID = id
  }

  object OrderId {
    inline def apply(u: UUID): OrderId = u
    extension (id: OrderId) inline def value: UUID = id
  }

  object TradeId {
    inline def apply(u: UUID): TradeId = u
    extension (id: TradeId) inline def value: UUID = id
  }

  object LedgerEntryId {
    inline def apply(u: UUID): LedgerEntryId = u
    extension (id: LedgerEntryId) inline def value: UUID = id
  }

  object ProjectId {
    inline def apply(u: UUID): ProjectId = u
    extension (id: ProjectId) inline def value: UUID = id
  }

  object DonationId {
    inline def apply(u: UUID): DonationId = u
    extension (id: DonationId) inline def value: UUID = id
  }

  object PostId {
    inline def apply(u: UUID): PostId = u
    extension (id: PostId) inline def value: UUID = id
  }

}
