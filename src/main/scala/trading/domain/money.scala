package trading.domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

object money {

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

}
