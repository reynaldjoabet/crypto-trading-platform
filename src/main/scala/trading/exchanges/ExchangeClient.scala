package trading.exchanges

import cats.ApplicativeThrow
import cats.syntax.all.*
import trading.domain.AppError
import trading.domain.money.Amount
import trading.domain.money.Price
import trading.domain.money.Quantity
import trading.domain.market.*

import java.time.Instant

/** A normalised exchange interface. Each venue implementation translates between this and the venue's REST/WebSocket
  * protocol. We deliberately keep the surface small and *order-centric*; portfolio/balance reads come from our own
  * ledger, not from the exchange.
  */
trait ExchangeClient[F[_]] {
  def venue: Venue
  def submit(intent: OrderIntent): F[VenueAck]
  def cancel(venueOrderId: String): F[Unit]
  def fetchFills(venueOrderId: String): F[List[VenueFill]]
}

final case class VenueAck(
    venueOrderId: String,
    acceptedAt: Instant
)

final case class VenueFill(
    venueTradeId: String,
    price: Price,
    quantity: Quantity,
    fee: Amount,
    feeCurrency: trading.domain.money.CurrencyCode,
    executedAt: Instant
)

object ExchangeClient {

  /** Resolve the right client for a venue, or fail with a clear error. */
  def select[F[_]: ApplicativeThrow](
      kraken: ExchangeClient[F],
      binance: ExchangeClient[F],
      ftx: Option[ExchangeClient[F]],
      mock: ExchangeClient[F]
  )(venue: Venue): F[ExchangeClient[F]] = {
    venue match {
      case Venue.Kraken  => kraken.pure[F]
      case Venue.Binance => binance.pure[F]
      case Venue.FTX     => ftx.toRight(AppError.Upstream("FTX", "FTX is no longer supported")).liftTo[F]
      case Venue.Mock    => mock.pure[F]
    }
  }

}
