package trading.domain.ledger

import java.time.Instant

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

import trading.domain.core.*
import trading.domain.money.*
//import trading.domain.wallet.*

enum LedgerAccountKind {
  case Asset
  case Liability
  case Revenue
  case Expense
  case Equity
}

enum LedgerAccountPurpose {
  case CustomerAvailable
  case CustomerReserved
  case CustomerPendingInbound
  case CustomerPendingOutbound
  case TreasuryHot
  case TreasuryCold
  case NetworkFeeExpense
  case PlatformFeeRevenue
  case MintClearing
  case BurnClearing
  case RedemptionClearing
  case ReconciliationSuspense
}

enum LedgerAccountStatus {
  case Active
  case Frozen
  case Closed
}

final case class LedgerAccount(
    id: LedgerAccountId,
    tenantId: TenantId,
    assetId: AssetId,
    ownerId: Option[OwnerId],
    walletId: Option[WalletId],
    kind: LedgerAccountKind,
    purpose: LedgerAccountPurpose,
    status: LedgerAccountStatus,
    version: AggregateVersion
)

enum PostingSide derives CanEqual {
  case Debit
  case Credit
}

final case class Posting(
    accountId: LedgerAccountId,
    side: PostingSide,
    amount: PositiveAmount,
    memo: Option[String]
)

type PostingList = PostingList.T
object PostingList extends RefinedSubtype[List[Posting], MinLength[2]]

enum LedgerTxType {
  case DepositObserved
  case DepositAvailable
  case WithdrawalReserved
  case WithdrawalReleased
  case WithdrawalSettled
  case NetworkFeeCharged
  case PlatformFeeCharged
  case InternalTransfer
  case StablecoinMinted
  case StablecoinBurned
  case StablecoinRedeemed
  case ReconciliationAdjustment
}

final case class LedgerTransaction private (
    id: LedgerTxId,
    tenantId: TenantId,
    txType: LedgerTxType,
    postings: PostingList,
    idempotencyKey: Option[IdempotencyKey],
    externalReference: Option[ExternalReference],
    effectiveAt: Instant,
    createdAt: Instant
)

object LedgerTransaction {

  def create(
      id: LedgerTxId,
      tenantId: TenantId,
      txType: LedgerTxType,
      postings: List[Posting],
      idempotencyKey: Option[IdempotencyKey],
      externalReference: Option[ExternalReference],
      effectiveAt: Instant,
      createdAt: Instant
  ): Either[DomainError, LedgerTransaction] = {
    for {
      refinedPostings <- PostingList.either(postings).left.map(DomainError.ValidationFailed.apply)
      _ <- Either.cond(
        isBalanced(refinedPostings),
        (),
        DomainError.UnbalancedLedgerTransaction(explainImbalance(refinedPostings))
      )
    } yield {
      LedgerTransaction(
        id = id,
        tenantId = tenantId,
        txType = txType,
        postings = refinedPostings,
        idempotencyKey = idempotencyKey,
        externalReference = externalReference,
        effectiveAt = effectiveAt,
        createdAt = createdAt
      )
    }
  }

  private def isBalanced(postings: List[Posting]): Boolean = {
    postings
      .groupBy(_.amount.assetId)
      .forall { case (_, assetPostings) =>
        val debits = assetPostings
          .collect { case Posting(_, PostingSide.Debit, amount, _) =>
            amount.units
          }
          .foldLeft(BigInt(0))(_ + _)

        val credits = assetPostings
          .collect { case Posting(_, PostingSide.Credit, amount, _) =>
            amount.units
          }
          .foldLeft(BigInt(0))(_ + _)

        debits == credits
      }
  }

  private def explainImbalance(postings: List[Posting]): String = {
    postings
      .groupBy(_.amount.assetId)
      .map { case (assetId, assetPostings) =>
        val debits = assetPostings
          .collect { case Posting(_, PostingSide.Debit, amount, _) =>
            amount.units
          }
          .foldLeft(BigInt(0))(_ + _)

        val credits = assetPostings
          .collect { case Posting(_, PostingSide.Credit, amount, _) =>
            amount.units
          }
          .foldLeft(BigInt(0))(_ + _)

        s"asset=$assetId debits=$debits credits=$credits"
      }
      .mkString("; ")
  }
}

enum ReservationStatus derives CanEqual {
  case Active
  case Consumed
  case Released
  case Expired
}

final case class FundsReservation(
    id: ReservationId,
    tenantId: TenantId,
    walletId: WalletId,
    assetId: AssetId,
    amount: PositiveAmount,
    availableAccountId: LedgerAccountId,
    reservedAccountId: LedgerAccountId,
    status: ReservationStatus,
    expiresAt: Option[Instant],
    createdAt: Instant,
    updatedAt: Instant,
    version: AggregateVersion
) {

  def activeAt(now: Instant): Boolean = {
    status == ReservationStatus.Active &&
    expiresAt.forall(_.isAfter(now))
  }

  def consume(now: Instant): Either[DomainError, FundsReservation] = {
    if (activeAt(now)) {
      Right(
        copy(
          status = ReservationStatus.Consumed,
          updatedAt = now,
          version = AggregateVersion.applyUnsafe(version + 1)
        )
      )
    } else {
      Left(DomainError.ReservationNotAvailable(id))
    }
  }

  def release(now: Instant): Either[DomainError, FundsReservation] = {
    if (activeAt(now)) {
      Right(
        copy(
          status = ReservationStatus.Released,
          updatedAt = now,
          version = AggregateVersion.applyUnsafe(version + 1)
        )
      )
    } else {
      Left(DomainError.ReservationNotAvailable(id))
    }
  }
}
