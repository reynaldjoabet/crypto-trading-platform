package trading.domain.transfer

import java.time.Instant
import trading.domain.core.*
import trading.domain.money.*
import trading.domain.chain.*
// import trading.domain.ledger.*
// import trading.domain.wallet.*

enum WithdrawalStatus {
  case Requested
  case Reserved
  case AwaitingApproval
  case Approved
  case Signing
  case Signed
  case Broadcast
  case Confirming
  case Completed
  case Failed
  case Cancelled
}

enum WithdrawalFailureReason {
  case RejectedByPolicy
  case RejectedByCompliance
  case InsufficientFunds
  case SigningFailed
  case BroadcastFailed
  case ChainDropped
  case ChainReorg
  case Unknown
}

final case class WithdrawalDestination(
    blockchainId: BlockchainId,
    address: VerifiedBlockchainAddress,
    label: Option[Label],
    allowlisted: Boolean
)

final case class Withdrawal(
    id: WithdrawalId,
    tenantId: TenantId,
    walletId: WalletId,
    assetId: AssetId,
    blockchainId: BlockchainId,
    sourceAddressId: WalletAddressId,
    destination: WithdrawalDestination,
    amount: PositiveAmount,
    feePolicy: FeePolicy,
    reservationId: Option[ReservationId],
    chainTxId: Option[ChainTxId],
    status: WithdrawalStatus,
    failureReason: Option[WithdrawalFailureReason],
    idempotencyKey: IdempotencyKey,
    requestedAt: Instant,
    updatedAt: Instant,
    version: AggregateVersion
) {

  def moveTo(
      next: WithdrawalStatus,
      now: Instant
  ): Either[DomainError, Withdrawal] = {
    if (Withdrawal.allowed(status).contains(next)) {
      Right(
        copy(
          status = next,
          updatedAt = now,
          version = AggregateVersion.applyUnsafe(version + 1)
        )
      )
    } else {
      Left(
        DomainError.InvalidStateTransition(
          entity = "Withdrawal",
          from = status.toString,
          to = next.toString
        )
      )
    }
  }

  def attachReservation(
      reservationId: ReservationId,
      now: Instant
  ): Either[DomainError, Withdrawal] = {
    moveTo(WithdrawalStatus.Reserved, now).map { withdrawal =>
      withdrawal.copy(reservationId = Some(reservationId))
    }
  }

  def attachChainTx(
      chainTxId: ChainTxId,
      now: Instant
  ): Either[DomainError, Withdrawal] = {
    moveTo(WithdrawalStatus.Signing, now).map { withdrawal =>
      withdrawal.copy(chainTxId = Some(chainTxId))
    }
  }

  def fail(
      reason: WithdrawalFailureReason,
      now: Instant
  ): Either[DomainError, Withdrawal] = {
    moveTo(WithdrawalStatus.Failed, now).map { withdrawal =>
      withdrawal.copy(failureReason = Some(reason))
    }
  }
}

object Withdrawal {

  private val allowedTransitions: Map[WithdrawalStatus, Set[WithdrawalStatus]] = {
    import WithdrawalStatus.*

    Map(
      Requested -> Set(Reserved, AwaitingApproval, Failed, Cancelled),
      AwaitingApproval -> Set(Approved, Failed, Cancelled),
      Approved -> Set(Reserved, Failed, Cancelled),
      Reserved -> Set(Signing, Failed, Cancelled),
      Signing -> Set(Signed, Failed),
      Signed -> Set(Broadcast, Failed),
      Broadcast -> Set(Confirming, Failed),
      Confirming -> Set(Completed, Failed),
      Completed -> Set.empty,
      Failed -> Set.empty,
      Cancelled -> Set.empty
    )
  }

  def allowed(status: WithdrawalStatus): Set[WithdrawalStatus] = {
    allowedTransitions.getOrElse(status, Set.empty)
  }

  def create(
      id: WithdrawalId,
      tenantId: TenantId,
      walletId: WalletId,
      assetId: AssetId,
      blockchainId: BlockchainId,
      sourceAddressId: WalletAddressId,
      destination: WithdrawalDestination,
      amount: PositiveAmount,
      feePolicy: FeePolicy,
      idempotencyKey: IdempotencyKey,
      now: Instant
  ): Either[DomainError, Withdrawal] = {
    if (amount.assetId != assetId) {
      Left(DomainError.AssetMismatch(assetId, amount.assetId))
    } else if (destination.blockchainId != blockchainId) {
      Left(
        DomainError.InvalidAddressForChain(
          blockchainId,
          s"Destination belongs to ${destination.blockchainId}"
        )
      )
    } else {
      Right(
        Withdrawal(
          id = id,
          tenantId = tenantId,
          walletId = walletId,
          assetId = assetId,
          blockchainId = blockchainId,
          sourceAddressId = sourceAddressId,
          destination = destination,
          amount = amount,
          feePolicy = feePolicy,
          reservationId = None,
          chainTxId = None,
          status = WithdrawalStatus.Requested,
          failureReason = None,
          idempotencyKey = idempotencyKey,
          requestedAt = now,
          updatedAt = now,
          version = AggregateVersion.initial
        )
      )
    }
  }
}

final case class ChainEventKey(
    blockchainId: BlockchainId,
    txId: TransactionId,
    transactionIndex: Option[TransactionIndex],
    logIndex: Option[LogIndex]
)

enum ChainEventFinality {
  case Observed
  case Included
  case Confirmed
  case Finalized
  case Reorged
}

final case class ObservedTokenTransfer(
    key: ChainEventKey,
    assetId: AssetId,
    from: BlockchainAddress,
    to: BlockchainAddress,
    amount: PositiveAmount,
    block: Option[BlockRef],
    confirmations: ConfirmationCount,
    finality: ChainEventFinality,
    observedAt: Instant
)

enum DepositStatus derives CanEqual {
  case Observed
  case AwaitingConfirmations
  case CreditedPending
  case CreditedAvailable
  case Ignored
  case Reorged
}

final case class Deposit(
    id: DepositId,
    tenantId: TenantId,
    walletId: WalletId,
    walletAddressId: WalletAddressId,
    assetId: AssetId,
    blockchainId: BlockchainId,
    source: BlockchainAddress,
    destination: BlockchainAddress,
    amount: PositiveAmount,
    chainEventKey: ChainEventKey,
    ledgerTxId: Option[LedgerTxId],
    status: DepositStatus,
    observedAt: Instant,
    updatedAt: Instant,
    version: AggregateVersion
) {

  def markAvailable(
      ledgerTxId: LedgerTxId,
      now: Instant
  ): Either[DomainError, Deposit] = {
    status match {
      case DepositStatus.Observed | DepositStatus.AwaitingConfirmations | DepositStatus.CreditedPending =>
        Right(
          copy(
            ledgerTxId = Some(ledgerTxId),
            status = DepositStatus.CreditedAvailable,
            updatedAt = now,
            version = AggregateVersion.applyUnsafe(version + 1)
          )
        )

      case _ =>
        Left(
          DomainError.InvalidStateTransition(
            entity = "Deposit",
            from = status.toString,
            to = DepositStatus.CreditedAvailable.toString
          )
        )
    }
  }

  def markReorged(now: Instant): Either[DomainError, Deposit] = {
    status match {
      case DepositStatus.CreditedAvailable =>
        Left(DomainError.ValidationFailed("Cannot mark credited deposit as reorged without compensating ledger entry"))

      case _ =>
        Right(
          copy(
            status = DepositStatus.Reorged,
            updatedAt = now,
            version = AggregateVersion.applyUnsafe(version + 1)
          )
        )
    }
  }
}
