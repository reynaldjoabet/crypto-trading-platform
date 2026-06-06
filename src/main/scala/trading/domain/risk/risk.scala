package trading.domain.risk

import java.time.Instant

import trading.domain.core.*
import trading.domain.chain.*
//import trading.domain.wallet.*

enum KycStatus {
  case NotRequired
  case Pending
  case Approved
  case Rejected
  case Expired
}

enum SanctionsStatus derives CanEqual {
  case NotScreened
  case Clear
  case PossibleMatch
  case ConfirmedMatch
}

enum RiskDecision derives CanEqual {
  case Approved
  case ReviewRequired
  case Rejected
}

final case class ComplianceSnapshot(
    ownerId: OwnerId,
    kycStatus: KycStatus,
    sanctionsStatus: SanctionsStatus,
    decision: RiskDecision,
    reason: Option[String],
    checkedAt: Instant
) {

  def approvedOrError: Either[DomainError, Unit] = {
    decision match {
      case RiskDecision.Approved =>
        Right(())

      case RiskDecision.ReviewRequired =>
        Left(DomainError.ComplianceRejected("Manual review required"))

      case RiskDecision.Rejected =>
        Left(DomainError.ComplianceRejected(reason.getOrElse("Rejected by compliance policy")))
    }
  }
}

final case class DestinationRisk(
    address: BlockchainAddress,
    blockchainId: BlockchainId,
    sanctionsStatus: SanctionsStatus,
    riskScore: Int,
    checkedAt: Instant
) {

  def approvedOrError(maxScore: Int): Either[DomainError, Unit] = {
    sanctionsStatus match {
      case SanctionsStatus.ConfirmedMatch =>
        Left(DomainError.ComplianceRejected("Destination is a confirmed sanctions match"))

      case _ if riskScore > maxScore =>
        Left(DomainError.ComplianceRejected(s"Destination risk score $riskScore exceeds $maxScore"))

      case _ =>
        Right(())
    }
  }
}
