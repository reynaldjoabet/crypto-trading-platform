package trading.domain.money

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
  def +(b: Amount): Amount = Amount.assume(a.value + b.value)
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
