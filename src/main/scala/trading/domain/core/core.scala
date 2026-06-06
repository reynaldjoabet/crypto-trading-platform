package trading.domain.core

import java.time.Instant

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

type UuidText =
  DescribedAs[
    Match["^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"],
    "Expected UUID text"
  ]

type SlugText =
  DescribedAs[
    Match["^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$"],
    "Expected lower-case slug"
  ]

type NonBlank64 =
  DescribedAs[
    Trimmed & MinLength[1] & MaxLength[64],
    "Expected non-blank text up to 64 characters"
  ]

type NonBlank128 =
  DescribedAs[
    Trimmed & MinLength[1] & MaxLength[128],
    "Expected non-blank text up to 128 characters"
  ]

type AssetSymbolText =
  DescribedAs[
    Match["^[A-Z][A-Z0-9]{1,15}$"],
    "Expected upper-case asset symbol"
  ]

type FiatCurrencyText =
  DescribedAs[
    Match["^[A-Z]{3}$"],
    "Expected 3-letter fiat currency code"
  ]

type IdempotencyKeyText =
  DescribedAs[
    Trimmed & MinLength[8] & MaxLength[128],
    "Expected idempotency key between 8 and 128 characters"
  ]

type ExternalReferenceText =
  DescribedAs[
    Trimmed & MinLength[1] & MaxLength[128],
    "Expected external reference up to 128 characters"
  ]

type TenantId = TenantId.T
object TenantId extends RefinedType[String, UuidText]

type OwnerId = OwnerId.T
object OwnerId extends RefinedType[String, UuidText]

type WalletId = WalletId.T
object WalletId extends RefinedType[String, UuidText]

type WalletAddressId = WalletAddressId.T
object WalletAddressId extends RefinedType[String, UuidText]

type AssetId = AssetId.T
object AssetId extends RefinedType[String, UuidText] {
  given CanEqual[AssetId, AssetId] = CanEqual.derived
}

type BlockchainId = BlockchainId.T
object BlockchainId extends RefinedType[String, SlugText] {
  given CanEqual[BlockchainId, BlockchainId] = CanEqual.derived
}

type LedgerAccountId = LedgerAccountId.T
object LedgerAccountId extends RefinedType[String, UuidText]

type LedgerTxId = LedgerTxId.T
object LedgerTxId extends RefinedType[String, UuidText]

type ReservationId = ReservationId.T
object ReservationId extends RefinedType[String, UuidText]

type WithdrawalId = WithdrawalId.T
object WithdrawalId extends RefinedType[String, UuidText]

type DepositId = DepositId.T
object DepositId extends RefinedType[String, UuidText]

type ChainTxId = ChainTxId.T
object ChainTxId extends RefinedType[String, UuidText]

type StablecoinOperationId = StablecoinOperationId.T
object StablecoinOperationId extends RefinedType[String, UuidText]

type ReserveReportId = ReserveReportId.T
object ReserveReportId extends RefinedType[String, UuidText]

type IdempotencyKey = IdempotencyKey.T
object IdempotencyKey extends RefinedType[String, IdempotencyKeyText]

type ExternalReference = ExternalReference.T
object ExternalReference extends RefinedType[String, ExternalReferenceText]

type DisplayName = DisplayName.T
object DisplayName extends RefinedSubtype[String, NonBlank128]

type Label = Label.T
object Label extends RefinedSubtype[String, NonBlank64]

type AssetSymbol = AssetSymbol.T
object AssetSymbol extends RefinedSubtype[String, AssetSymbolText]

type FiatCurrency = FiatCurrency.T
object FiatCurrency extends RefinedSubtype[String, FiatCurrencyText]

type AggregateVersion = AggregateVersion.T
object AggregateVersion extends RefinedSubtype[Long, Positive0] {
  val initial: AggregateVersion = AggregateVersion.applyUnsafe(0L)
}

type TokenDecimals = TokenDecimals.T
object TokenDecimals extends RefinedSubtype[Int, Interval.Closed[0, 36]]

type ConfirmationCount = ConfirmationCount.T
object ConfirmationCount extends RefinedSubtype[Int, Interval.Closed[0, 2048]]

type BlockHeight = BlockHeight.T
object BlockHeight extends RefinedSubtype[Long, Positive0]

type TransactionIndex = TransactionIndex.T
object TransactionIndex extends RefinedSubtype[Int, Positive0]

type LogIndex = LogIndex.T
object LogIndex extends RefinedSubtype[Int, Positive0]

type ChainNonce = ChainNonce.T
object ChainNonce extends RefinedSubtype[BigInt, Positive0]

type BasisPoints = BasisPoints.T
object BasisPoints extends RefinedSubtype[Int, Interval.Closed[0, 10000]]

type AtomicUnits = AtomicUnits.T
object AtomicUnits extends RefinedSubtype[BigInt, Positive0] {
  val zero: AtomicUnits = AtomicUnits.applyUnsafe(BigInt(0))
}

type PositiveUnits = PositiveUnits.T
object PositiveUnits extends RefinedSubtype[BigInt, Positive]

enum DomainError {
  case ValidationFailed(message: String)
  case AssetMismatch(expected: AssetId, actual: AssetId)
  case AmountMustBePositive(assetId: AssetId)
  case InsufficientFunds(assetId: AssetId, available: AtomicUnits, requested: PositiveUnits)

  case TenantNotFound(tenantId: TenantId)
  case OwnerNotFound(ownerId: OwnerId)
  case WalletNotFound(walletId: WalletId)
  case AssetNotFound(assetId: AssetId)
  case BlockchainNotFound(blockchainId: BlockchainId)

  case WalletInactive(walletId: WalletId)
  case AssetInactive(assetId: AssetId)
  case BlockchainInactive(blockchainId: BlockchainId)
  case AssetNotDeployed(assetId: AssetId, blockchainId: BlockchainId)

  case InvalidAddressForChain(blockchainId: BlockchainId, message: String)
  case InvalidAssetLocatorForChain(blockchainId: BlockchainId, message: String)

  case UnbalancedLedgerTransaction(message: String)
  case LedgerAccountMismatch(message: String)
  case DuplicateIdempotencyKey(key: IdempotencyKey)

  case ReservationNotFound(reservationId: ReservationId)
  case ReservationNotAvailable(reservationId: ReservationId)
  case InvalidStateTransition(entity: String, from: String, to: String)

  case ComplianceRejected(reason: String)
  case LimitExceeded(reason: String)
  case ChainClientFailed(message: String)
  case ConcurrencyConflict(entity: String, expected: AggregateVersion, actual: AggregateVersion)
}

final case class AuditInfo(
    createdAt: Instant,
    updatedAt: Instant,
    createdBy: Option[ExternalReference],
    updatedBy: Option[ExternalReference]
)
