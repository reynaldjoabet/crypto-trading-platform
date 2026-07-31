package trading.domain.money

import java.time.{Instant, LocalDate}
import java.util.UUID

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import trading.domain.core.*

private type GE[V] = Greater[V] | StrictEqual[V]
private type LE[V] = Less[V] | StrictEqual[V]

type Price = Price.T
object Price extends RefinedType[BigDecimal, Greater[0]]

type Quantity = Quantity.T
object Quantity extends RefinedType[BigDecimal, Greater[0]]

type Amount = Amount.T
object Amount extends RefinedType[BigDecimal, GE[0]] {
  val Zero: Amount = assume(BigDecimal(0))
}

type Percent = Percent.T
object Percent extends RefinedType[Double, GE[0.0] & LE[1.0]]

type FeeBps = FeeBps.T
object FeeBps extends RefinedType[Int, GE[0] & LE[10000]]

type CurrencyCode = CurrencyCode.T
object CurrencyCode extends RefinedType[String, Match["^[A-Z0-9]{2,10}$"]]

extension (p: Price) {
  def *(q: Quantity): Amount = Amount.assume(p.value * q.value)
}

extension (a: Amount) {

  def +(b: Amount): Amount                 = Amount.assume(a.value + b.value)
  def -(b: Amount): Either[String, Amount] = Amount.either(a.value - b.value)

}

final case class Money(amount: Amount, currency: CurrencyCode) derives CanEqual

// final case class Amount(
//   assetId: AssetId,
//   units: AtomicUnits
// ) {

// def +(that: Amount): Either[DomainError, Amount] = {
// 	if (this.assetId != that.assetId) {
// 		Left(DomainError.AssetMismatch(this.assetId, that.assetId))
// 	} else {
// 		Right(copy(units = AtomicUnits.applyUnsafe(this.units + that.units)))
// 	}
// }

// def -(that: PositiveAmount): Either[DomainError, Amount] = {
// 	if (this.assetId != that.assetId) {
// 		Left(DomainError.AssetMismatch(this.assetId, that.assetId))
// 	} else if (this.units < that.units) {
// 		Left(DomainError.InsufficientFunds(this.assetId, this.units, that.units))
// 	} else {
// 		Right(copy(units = AtomicUnits.applyUnsafe(this.units - that.units)))
// 	}
// }

// def isZero: Boolean = {
// 	units == AtomicUnits.zero
// }
// }

// object Amount {

//   def zero(assetId: AssetId): Amount = {
//     Amount(assetId, AtomicUnits.zero)
//   }

//   def fromPositive(value: PositiveAmount): Amount = {
//     Amount(value.assetId, AtomicUnits.applyUnsafe(value.units))
//   }
// }

final case class PositiveAmount(
    assetId: AssetId,
    units: PositiveUnits
) {

//   def toAmount: Amount = {
//     Amount(assetId, AtomicUnits.applyUnsafe(units))
//   }
}

final case class FeeAmount(
    assetId: AssetId,
    units: AtomicUnits
)

enum FeePayer {

  case Customer
  case Platform

}

enum FeeMode {

  case Exact
  case Estimated
  case Subsidized

}

final case class FeePolicy(
    payer: FeePayer,
    mode: FeeMode,
    maxNetworkFee: Option[PositiveAmount],
    platformFee: Option[PositiveAmount]
)

final case class QuotedAmount(
    gross: PositiveAmount,
    networkFee: Option[PositiveAmount],
    platformFee: Option[PositiveAmount],
    net: PositiveAmount
)

/**
  * Refined primitives. Every value that crosses the API boundary is parsed into one of these once,
  * at the edge (jsoniter codecs), and is then valid by construction everywhere else in the program.
  */

/**
  * Cameroonian mobile number in E.164: +237 followed by 9 digits starting with 6 (MTN and Orange
  * mobile ranges). Identity in NjangiPay is the phone number — most users have no email and no bank
  * account.
  */
type CmPhoneRule = Match["""\+2376[0-9]{8}"""] DescribedAs
  "must be a Cameroonian mobile number in the form +2376XXXXXXXX"

type PhoneNumber = PhoneNumber.T
object PhoneNumber extends RefinedType[String, CmPhoneRule]

/**
  * A positive amount of whole XAF francs. XAF has no minor unit — there are no cents — so money is
  * Long, never BigDecimal, never Double.
  */
type AmountRule = Positive DescribedAs "amount must be a positive whole number of XAF francs"

type AmountXAF = AmountXAF.T
object AmountXAF extends RefinedType[Long, AmountRule]

type GroupNameRule =
  (MinLength[3] & MaxLength[80]) DescribedAs "group name must be between 3 and 80 characters"

type GroupName = GroupName.T
object GroupName extends RefinedType[String, GroupNameRule]

type OtpCodeRule = Match["[0-9]{6}"] DescribedAs "OTP code must be exactly 6 digits"

type Otp = Otp.T
object Otp extends RefinedType[String, OtpCodeRule]

/**
  * Njangi group size: at least 2 people, capped to keep one rotation round shorter than two years
  * even on a weekly schedule.
  */
type MemberTargetRule =
  (GreaterEqual[2] & LessEqual[100]) DescribedAs "member target must be between 2 and 100"

type MemberTarget = MemberTarget.T
object MemberTarget extends RefinedType[Int, MemberTargetRule]

/**
  * Database enumerations. `dbValue` is the PostgreSQL enum label; JSON uses the Scala case name
  * (e.g. "MtnMomo", "Weekly").
  */

enum KycTier derives CanEqual {

  case Tier0, Tier1, Tier2

  def dbValue: String = this match {
    case Tier0 => "TIER_0"
    case Tier1 => "TIER_1"
    case Tier2 => "TIER_2"
  }

}

object KycTier {

  def fromDb(s: String): KycTier = values
    .find(_.dbValue == s)
    .getOrElse(throw new IllegalArgumentException(s"unknown kyc_tier: $s"))

}

enum UserStatus derives CanEqual {

  case Active, Suspended, Closed

  def dbValue: String = this match {
    case Active => "ACTIVE"; case Suspended => "SUSPENDED"; case Closed => "CLOSED"
  }

}

object UserStatus {

  def fromDb(s: String): UserStatus = values
    .find(_.dbValue == s)
    .getOrElse(throw new IllegalArgumentException(s"unknown user_status: $s"))

}

enum Language derives CanEqual {

  case Fr, En

  def dbValue: String = this match {
    case Fr => "FR"; case En => "EN"
  }

}

object Language {

  def fromDb(s: String): Language = values
    .find(_.dbValue == s)
    .getOrElse(throw new IllegalArgumentException(s"unknown language_code: $s"))

}

enum Frequency derives CanEqual {

  case Weekly, Biweekly, Monthly

  def dbValue: String = this match {
    case Weekly => "WEEKLY"; case Biweekly => "BIWEEKLY"; case Monthly => "MONTHLY"
  }

}

object Frequency {

  def fromDb(s: String): Frequency = values
    .find(_.dbValue == s)
    .getOrElse(throw new IllegalArgumentException(s"unknown contribution_frequency: $s"))

}

enum GroupStatus derives CanEqual {

  case Open, Active, Completed, Cancelled

  def dbValue: String = this match {
    case Open      => "OPEN"; case Active         => "ACTIVE"
    case Completed => "COMPLETED"; case Cancelled => "CANCELLED"
  }

}

object GroupStatus {

  def fromDb(s: String): GroupStatus = values
    .find(_.dbValue == s)
    .getOrElse(throw new IllegalArgumentException(s"unknown group_status: $s"))

}

enum MemberStatus derives CanEqual {

  case Active, Left, Expelled

  def dbValue: String = this match {
    case Active => "ACTIVE"; case Left => "LEFT"; case Expelled => "EXPELLED"
  }

}

object MemberStatus {

  def fromDb(s: String): MemberStatus = values
    .find(_.dbValue == s)
    .getOrElse(throw new IllegalArgumentException(s"unknown member_status: $s"))

}

enum CycleStatus derives CanEqual {

  case Scheduled, Collecting, PayoutDue, Paid

  def dbValue: String = this match {
    case Scheduled => "SCHEDULED"; case Collecting => "COLLECTING"
    case PayoutDue => "PAYOUT_DUE"; case Paid      => "PAID"
  }

}

object CycleStatus {

  def fromDb(s: String): CycleStatus = values
    .find(_.dbValue == s)
    .getOrElse(throw new IllegalArgumentException(s"unknown cycle_status: $s"))

}

enum ContributionStatus derives CanEqual {

  case Pending, Paid, Late, Defaulted, Waived

  def dbValue: String = this match {
    case Pending   => "PENDING"; case Paid     => "PAID"; case Late => "LATE"
    case Defaulted => "DEFAULTED"; case Waived => "WAIVED"
  }

}

object ContributionStatus {

  def fromDb(s: String): ContributionStatus = values
    .find(_.dbValue == s)
    .getOrElse(throw new IllegalArgumentException(s"unknown contribution_status: $s"))

}

enum ProviderKind derives CanEqual {

  case MtnMomo, OrangeMoney, InternalWallet

  def dbValue: String = this match {
    case MtnMomo        => "MTN_MOMO"; case OrangeMoney => "ORANGE_MONEY"
    case InternalWallet => "INTERNAL_WALLET"
  }

}

object ProviderKind {

  def fromDb(s: String): ProviderKind = values
    .find(_.dbValue == s)
    .getOrElse(throw new IllegalArgumentException(s"unknown payment_provider: $s"))

}

enum IntentDirection derives CanEqual {

  case Collection, Disbursement

  def dbValue: String = this match {
    case Collection => "COLLECTION"; case Disbursement => "DISBURSEMENT"
  }

}

object IntentDirection {

  def fromDb(s: String): IntentDirection = values
    .find(_.dbValue == s)
    .getOrElse(throw new IllegalArgumentException(s"unknown intent_direction: $s"))

}

enum IntentStatus derives CanEqual {

  case Created, Submitted, Succeeded, Failed, Expired

  def dbValue: String = this match {
    case Created   => "CREATED"; case Submitted => "SUBMITTED"
    case Succeeded => "SUCCEEDED"; case Failed  => "FAILED"; case Expired => "EXPIRED"
  }

  def isTerminal: Boolean = this match {
    case Succeeded | Failed | Expired => true
    case _                            => false
  }

}

object IntentStatus {

  def fromDb(s: String): IntentStatus = values
    .find(_.dbValue == s)
    .getOrElse(throw new IllegalArgumentException(s"unknown intent_status: $s"))

}

enum PayoutStatus derives CanEqual {

  case Pending, Paid, Failed

  def dbValue: String = this match {
    case Pending => "PENDING"; case Paid => "PAID"; case Failed => "FAILED"
  }

}

object PayoutStatus {

  def fromDb(s: String): PayoutStatus = values
    .find(_.dbValue == s)
    .getOrElse(throw new IllegalArgumentException(s"unknown payout_status: $s"))

}

enum EntryType derives CanEqual {

  case CashIn, CashOut, Contribution, Payout, Penalty, Fee, Reversal, Adjustment

  def dbValue: String = this match {
    case EntryType.CashIn       => "CASH_IN"; case EntryType.CashOut     => "CASH_OUT"
    case EntryType.Contribution => "CONTRIBUTION"; case EntryType.Payout => "PAYOUT"
    case EntryType.Penalty      => "PENALTY"; case EntryType.Fee         => "FEE"
    case EntryType.Reversal     => "REVERSAL"; case EntryType.Adjustment => "ADJUSTMENT"
  }

}

object EntryType {

  def fromDb(s: String): EntryType = values
    .find(_.dbValue == s)
    .getOrElse(throw new IllegalArgumentException(s"unknown journal_entry_type: $s"))

}

/**
  * Entities, mirroring the schema. Amounts are whole XAF (Long).
  */

final case class User(
    id: UUID,
    phone: PhoneNumber,
    fullName: String,
    language: Language,
    kycTier: KycTier,
    status: UserStatus,
    createdAt: Instant
)

final case class KycLimits(
    tier: KycTier,
    maxWalletBalance: Long,
    maxDailyCashIn: Long,
    maxMonthlyCashIn: Long,
    maxSingleOperation: Long
)

final case class NjangiGroup(
    id: UUID,
    name: String,
    ownerUserId: UUID,
    contributionAmount: Long,
    frequency: Frequency,
    memberTarget: Int,
    gracePeriodDays: Int,
    penaltyAmount: Long,
    status: GroupStatus,
    startsOn: Option[LocalDate],
    createdAt: Instant
)

final case class GroupMember(
    id: UUID,
    groupId: UUID,
    userId: UUID,
    position: Int,
    status: MemberStatus
)

final case class Cycle(
    id: UUID,
    groupId: UUID,
    cycleNumber: Int,
    beneficiaryMemberId: UUID,
    opensOn: LocalDate,
    dueOn: LocalDate,
    status: CycleStatus
)

final case class Contribution(
    id: UUID,
    cycleId: UUID,
    memberId: UUID,
    amountDue: Long,
    status: ContributionStatus,
    paidAt: Option[Instant],
    journalEntryId: Option[UUID],
    penaltyAmount: Long
)

final case class PaymentIntent(
    id: UUID,
    userId: UUID,
    direction: IntentDirection,
    provider: ProviderKind,
    msisdn: String,
    amount: Long,
    status: IntentStatus,
    providerRef: String,
    providerTxnId: Option[String],
    contributionId: Option[UUID],
    journalEntryId: Option[UUID],
    expiresAt: Instant
)

/**
  * The verified caller, decoded from the JWT.
  */
final case class AuthContext(userId: UUID, phone: String, kycTier: KycTier)

/**
  * A domain failure that maps to an HTTP status at the route boundary. Services raise it inside
  * transactions so a failure also rolls back.
  */
final case class ApiError(code: String, message: String)

object ApiError {

  def notFound(what: String): ApiError  = ApiError("NOT_FOUND", s"$what not found")
  def forbidden(msg: String): ApiError  = ApiError("FORBIDDEN", msg)
  def conflict(msg: String): ApiError   = ApiError("CONFLICT", msg)
  def validation(msg: String): ApiError = ApiError("VALIDATION", msg)

  def insufficientFunds: ApiError =
    ApiError("INSUFFICIENT_FUNDS", "wallet balance is insufficient for this operation")

  def limitExceeded(msg: String): ApiError = ApiError("KYC_LIMIT_EXCEEDED", msg)

}

final case class DomainException(error: ApiError) extends RuntimeException(error.message)
