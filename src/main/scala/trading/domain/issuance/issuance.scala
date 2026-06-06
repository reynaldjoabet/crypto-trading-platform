package trading.domain.issuance

import java.time.Instant
import java.time.LocalDate

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

import trading.domain.core.*
import trading.domain.money.*
import trading.domain.chain.*
import trading.domain.ledger.*
//import trading.domain.wallet.*

type FiatMinorUnits = FiatMinorUnits.T
object FiatMinorUnits extends RefinedSubtype[BigInt, Positive0]

enum ReserveReportStatus {
  case Draft
  case Published
  case Verified
  case Superseded
  case Rejected
}

final case class ReserveReport(
    id: ReserveReportId,
    tenantId: TenantId,
    stablecoinAssetId: AssetId,
    reserveCurrency: FiatCurrency,
    reserveMinorUnits: FiatMinorUnits,
    circulatingSupply: AtomicUnits,
    asOfDate: LocalDate,
    status: ReserveReportStatus,
    externalReference: Option[ExternalReference],
    publishedAt: Option[Instant],
    version: AggregateVersion
)

enum StablecoinOperationKind {
  case Mint
  case Burn
  case Redemption
}

enum StablecoinOperationStatus {
  case Requested
  case Approved
  case Reserved
  case ChainTxCreated
  case Broadcast
  case Confirmed
  case Settled
  case Rejected
  case Failed
  case Cancelled
}

final case class MintRequest(
    id: StablecoinOperationId,
    tenantId: TenantId,
    stablecoinAssetId: AssetId,
    blockchainId: BlockchainId,
    recipient: BlockchainAddress,
    amount: PositiveAmount,
    reserveReference: ExternalReference,
    chainTxId: Option[ChainTxId],
    ledgerTxId: Option[LedgerTxId],
    status: StablecoinOperationStatus,
    requestedAt: Instant,
    updatedAt: Instant,
    version: AggregateVersion
)

final case class BurnRequest(
    id: StablecoinOperationId,
    tenantId: TenantId,
    stablecoinAssetId: AssetId,
    blockchainId: BlockchainId,
    sourceWalletId: WalletId,
    amount: PositiveAmount,
    burnChainTxId: Option[ChainTxId],
    ledgerTxId: Option[LedgerTxId],
    status: StablecoinOperationStatus,
    requestedAt: Instant,
    updatedAt: Instant,
    version: AggregateVersion
)

final case class RedemptionRequest(
    id: StablecoinOperationId,
    tenantId: TenantId,
    stablecoinAssetId: AssetId,
    customerWalletId: WalletId,
    amount: PositiveAmount,
    fiatCurrency: FiatCurrency,
    fiatPayoutReference: Option[ExternalReference],
    burnRequestId: Option[StablecoinOperationId],
    ledgerTxId: Option[LedgerTxId],
    status: StablecoinOperationStatus,
    requestedAt: Instant,
    updatedAt: Instant,
    version: AggregateVersion
)

object StablecoinLedgerTemplates {

  def recordMintToTreasury(
      id: LedgerTxId,
      tenantId: TenantId,
      mintClearingAccount: LedgerAccountId,
      treasuryAssetAccount: LedgerAccountId,
      amount: PositiveAmount,
      externalReference: ExternalReference,
      now: Instant
  ): Either[DomainError, LedgerTransaction] = {
    LedgerTransaction.create(
      id = id,
      tenantId = tenantId,
      txType = LedgerTxType.StablecoinMinted,
      postings = List(
        Posting(
          accountId = treasuryAssetAccount,
          side = PostingSide.Debit,
          amount = amount,
          memo = Some("Increase treasury stablecoin asset after mint")
        ),
        Posting(
          accountId = mintClearingAccount,
          side = PostingSide.Credit,
          amount = amount,
          memo = Some("Mint clearing credit")
        )
      ),
      idempotencyKey = None,
      externalReference = Some(externalReference),
      effectiveAt = now,
      createdAt = now
    )
  }

  def recordBurnFromTreasury(
      id: LedgerTxId,
      tenantId: TenantId,
      burnClearingAccount: LedgerAccountId,
      treasuryAssetAccount: LedgerAccountId,
      amount: PositiveAmount,
      externalReference: ExternalReference,
      now: Instant
  ): Either[DomainError, LedgerTransaction] = {
    LedgerTransaction.create(
      id = id,
      tenantId = tenantId,
      txType = LedgerTxType.StablecoinBurned,
      postings = List(
        Posting(
          accountId = burnClearingAccount,
          side = PostingSide.Debit,
          amount = amount,
          memo = Some("Burn clearing debit")
        ),
        Posting(
          accountId = treasuryAssetAccount,
          side = PostingSide.Credit,
          amount = amount,
          memo = Some("Reduce treasury stablecoin asset after burn")
        )
      ),
      idempotencyKey = None,
      externalReference = Some(externalReference),
      effectiveAt = now,
      createdAt = now
    )
  }
}
