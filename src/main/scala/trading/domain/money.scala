package trading.domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import io.github.iltotore.iron.refineEither

/** Money / price / quantity primitives, refined.
  *
  * All cash amounts use BigDecimal with at least 8 decimal places of room — enough for any sub-satoshi precision we'll
  * see in DeFi flows. We refine to non-negative ranges; *signed* deltas live on ledger entries and use BigDecimal
  * directly.
  */
object money {

  private type GE[V] = Greater[V] | StrictEqual[V]
  private type LE[V] = Less[V] | StrictEqual[V]

  type NonNegBD = BigDecimal :| GE[0]
  type PosBD = BigDecimal :| Greater[0]
  type ZeroToOne = Double :| (GE[0.0] & LE[1.0])
  type Bps = Int :| (GE[0] & LE[10000])

  opaque type Price = PosBD
  opaque type Quantity = PosBD
  opaque type Amount = NonNegBD
  opaque type Percent = ZeroToOne
  opaque type FeeBps = Bps

  object Price {
    inline def apply(b: BigDecimal): Either[String, Price] = b.refineEither[Greater[0]]
    inline def unsafe(b: BigDecimal): Price = b.refineUnsafe[Greater[0]]
    extension (p: Price) {
      inline def value: BigDecimal = p
      inline def *(q: Quantity): Amount = (p * q).assume[GE[0]]
    }
  }

  object Quantity {
    inline def apply(b: BigDecimal): Either[String, Quantity] = b.refineEither[Greater[0]]
    inline def unsafe(b: BigDecimal): Quantity = b.refineUnsafe[Greater[0]]
    extension (q: Quantity) inline def value: BigDecimal = q
  }

  object Amount {
    val Zero: Amount = BigDecimal(0).assume[GE[0]]
    def apply(b: BigDecimal): Either[String, Amount] = {
      if b >= 0 then Right(b.assume[GE[0]])
      else Left(s"Expected non-negative amount, got $b")
    }
    def unsafe(b: BigDecimal): Amount = b.assume[GE[0]]
    extension (a: Amount) {
      inline def value: BigDecimal = a
      inline def +(b: Amount): Amount = (a + b).assume[GE[0]]
      def -(b: Amount): Either[String, Amount] = {
        val r = a - b
        if r >= 0 then Right(r.assume[GE[0]])
        else Left(s"Subtraction would underflow: $a - $b = $r")
      }
    }
  }

  object Percent {
    def apply(d: Double): Either[String, Percent] = {
      if d >= 0.0 && d <= 1.0 then Right(d.assume[GE[0.0] & LE[1.0]])
      else Left(s"Expected percent in [0, 1], got $d")
    }
    def unsafe(d: Double): Percent = d.assume[GE[0.0] & LE[1.0]]
    extension (p: Percent) inline def value: Double = p
  }

  object FeeBps {
    def apply(n: Int): Either[String, FeeBps] = {
      if n >= 0 && n <= 10000 then Right(n.assume[GE[0] & LE[10000]])
      else Left(s"Expected fee in [0, 10000] bps, got $n")
    }
    def unsafe(n: Int): FeeBps = n.assume[GE[0] & LE[10000]]
    extension (b: FeeBps) inline def value: Int = b
  }

  /** ISO-4217 alpha-3 fiat or 2-10 char ticker for crypto. */
  type CurrencyCode = String :| (Match["^[A-Z0-9]{2,10}$"])
  object CurrencyCode {
    inline def apply(s: String): Either[String, CurrencyCode] = s.refineEither[Match["^[A-Z0-9]{2,10}$"]]
    inline def unsafe(s: String): CurrencyCode = s.refineUnsafe[Match["^[A-Z0-9]{2,10}$"]]
  }

  final case class Money(amount: Amount, currency: CurrencyCode) derives CanEqual

}
