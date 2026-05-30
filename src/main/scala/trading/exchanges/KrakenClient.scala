package trading.exchanges

import cats.effect.*
import cats.syntax.all.*
import io.circe.Json
import io.circe.syntax.*
import org.http4s.*
import org.http4s.client.Client
import org.http4s.implicits.*
import org.typelevel.log4cats.Logger
import trading.domain.AppError
import trading.domain.market.*

/** Kraken REST adapter — *stub*. Wires the request shape and HMAC-SHA512 signing surface, but does not parse the full
  * Kraken response model. Fill in `submit` / `fetchFills` once keys are issued.
  *
  * Reference: https://docs.kraken.com/rest/
  */
final case class KrakenConfig(
    baseUri: Uri = uri"https://api.kraken.com",
    apiKey: String,
    apiSecret: String
)

object KrakenClient {

  def make[F[_]: Async: Logger](client: Client[F], cfg: KrakenConfig): ExchangeClient[F] = {
    val _ = (client, cfg) // wired in by caller; impl stubbed pending API keys
    new ExchangeClient[F] {
      def venue: Venue = Venue.Kraken

      def submit(intent: OrderIntent): F[VenueAck] = {
        // symbol resolution from instrumentId would happen via an Instrument lookup at the service
        // layer; here we just log the venue-side shape we'd post.
        val payload = Json.obj(
          "type" -> (if intent.side == Side.Buy then "buy" else "sell").asJson,
          "ordertype" -> krakenOrderType(intent.orderType).asJson,
          "volume" -> intent.quantity.value.toString.asJson,
          "price" -> intent.limitPrice.map(_.value.toString).getOrElse("").asJson
        )
        Logger[F].info(s"[KRAKEN STUB] would submit $payload") *>
          Async[F].raiseError(AppError.Upstream("KRAKEN", "live Kraken submit not implemented"))
      }

      def cancel(venueOrderId: String): F[Unit] = {
        Logger[F].info(s"[KRAKEN STUB] cancel $venueOrderId") *>
          Async[F].raiseError(AppError.Upstream("KRAKEN", "live Kraken cancel not implemented"))
      }

      def fetchFills(venueOrderId: String): F[List[VenueFill]] = {
        Async[F].pure(List.empty)
      }
    }
  }

  private def krakenOrderType(o: OrderType): String = {
    o match {
      case OrderType.Market     => "market"
      case OrderType.Limit      => "limit"
      case OrderType.StopLimit  => "stop-loss-limit"
      case OrderType.TakeProfit => "take-profit-limit"
    }
  }

  // Kraken signs payloads as: HMAC-SHA512(URI + SHA256(nonce + POST-data), base64(secret))
  private[exchanges] def sign(secret: String, uriPath: String, nonce: String, postData: String): String = {
    import javax.crypto.Mac
    import javax.crypto.spec.SecretKeySpec
    import java.security.MessageDigest
    import java.util.Base64

    val sha256 = MessageDigest.getInstance("SHA-256")
    val message = uriPath.getBytes("UTF-8") ++ sha256.digest((nonce + postData).getBytes("UTF-8"))
    val key = Base64.getDecoder.decode(secret)
    val mac = Mac.getInstance("HmacSHA512")
    mac.init(SecretKeySpec(key, "HmacSHA512"))
    Base64.getEncoder.encodeToString(mac.doFinal(message))
  }

}
