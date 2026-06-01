package trading.domain

import java.util.UUID
import io.github.iltotore.iron.*

/** Refined identifiers using Iron's RefinedType pattern.
  *
  * Each UUID-based ID type extends RefinedType with a simple constraint (UUIDs themselves cannot be invalid once
  * constructed). Inherits apply/value helpers for safe construction and extraction. This provides:
  *   - Compile-time type distinction (UserId != AccountId)
  *   - Consistent pattern with other refined types (money, addresses)
  *   - Automatic codec support via Iron integration
  */
object ids {

  // ── User IDs ──
  type UserId = UserId.T
  object UserId extends RefinedType[UUID, True]

  type AccountId = AccountId.T
  object AccountId extends RefinedType[UUID, True]

  type WalletId = WalletId.T
  object WalletId extends RefinedType[UUID, True]

  // ── Market IDs ──
  type InstrumentId = InstrumentId.T
  object InstrumentId extends RefinedType[UUID, True]

  type StrategyId = StrategyId.T
  object StrategyId extends RefinedType[UUID, True]

  type ComponentId = ComponentId.T
  object ComponentId extends RefinedType[UUID, True]

  // ── Trading IDs ──
  type OrderId = OrderId.T
  object OrderId extends RefinedType[UUID, True]

  type TradeId = TradeId.T
  object TradeId extends RefinedType[UUID, True]

  type LedgerEntryId = LedgerEntryId.T
  object LedgerEntryId extends RefinedType[UUID, True]

  // ── Content/Community IDs ──
  type ProjectId = ProjectId.T
  object ProjectId extends RefinedType[UUID, True]

  type DonationId = DonationId.T
  object DonationId extends RefinedType[UUID, True]

  type PostId = PostId.T
  object PostId extends RefinedType[UUID, True]

}
