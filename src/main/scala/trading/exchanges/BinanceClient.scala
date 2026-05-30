package trading.exchanges

import cats.effect.*
import cats.syntax.all.*
import org.http4s.*
import org.http4s.client.Client
import org.http4s.implicits.*
import org.typelevel.log4cats.Logger
import trading.domain.AppError
import trading.domain.market.*

final case class BinanceConfig(
    baseUri: Uri = uri"https://api.binance.com",
    apiKey: String,
    apiSecret: String
)

/** Binance Spot REST stub. */
object BinanceClient {

  def make[F[_]: Async: Logger](client: Client[F], cfg: BinanceConfig): ExchangeClient[F] = {
    val _ = (client, cfg)
    new ExchangeClient[F] {
      def venue: Venue = Venue.Binance

      def submit(intent: OrderIntent): F[VenueAck] = {
        Logger[F].info(s"[BINANCE STUB] would submit ${intent.side} ${intent.quantity.value}") *>
          Async[F].raiseError(AppError.Upstream("BINANCE", "live Binance submit not implemented"))
      }

      def cancel(venueOrderId: String): F[Unit] = {
        Async[F].raiseError(AppError.Upstream("BINANCE", "live Binance cancel not implemented"))
      }

      def fetchFills(venueOrderId: String): F[List[VenueFill]] = {
        Async[F].pure(List.empty)
      }
    }
  }

  /** Binance signs requests as HMAC-SHA256(queryString, secret) and appends &signature=… . */
  private[exchanges] def sign(secret: String, query: String): String = {
    import javax.crypto.Mac
    import javax.crypto.spec.SecretKeySpec
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256"))
    mac.doFinal(query.getBytes("UTF-8")).map("%02x".format(_)).mkString
  }

}
