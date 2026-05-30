package trading.exchanges

import cats.effect.*
import cats.effect.std.Random
import cats.effect.std.UUIDGen
import cats.syntax.all.*
import trading.domain.money.*
import trading.domain.market.*

import scala.concurrent.duration.*

/** In-memory mock exchange. Fills any market order at a synthetic mid-price within ~50 bps of the (optional) limit
  * price, after a small latency. Used for local dev, integration tests, and the demo client portal until real keys are
  * wired in.
  */
object MockExchange {

  def make[F[_]: Async: UUIDGen]: F[ExchangeClient[F]] = {
    Random.scalaUtilRandom[F].map { rng =>
      new ExchangeClient[F] {
        def venue: Venue = Venue.Mock

        def submit(intent: OrderIntent): F[VenueAck] = {
          for {
            _ <- Async[F].sleep(20.millis)
            uid <- UUIDGen[F].randomUUID
            now <- Clock[F].realTimeInstant
          } yield VenueAck(s"mock-${uid.toString.take(8)}", now)
        }

        def cancel(venueOrderId: String): F[Unit] = {
          Async[F].unit
        }

        def fetchFills(venueOrderId: String): F[List[VenueFill]] = {
          for {
            now <- Clock[F].realTimeInstant
            drift <- rng.nextDouble.map(d => 1.0 + (d - 0.5) * 0.001) // ±5 bps
            px <- Async[F].pure(Price.unsafe(BigDecimal(100) * drift))
            qty <- Async[F].pure(Quantity.unsafe(BigDecimal(1)))
            fee <- Async[F].pure(Amount.unsafe(BigDecimal("0.10")))
          } yield List(VenueFill(s"$venueOrderId-fill-1", px, qty, fee, CurrencyCode.unsafe("USD"), now))
        }
      }
    }
  }

}
