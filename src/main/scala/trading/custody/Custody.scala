package trading.custody

import cats.effect.*
import cats.syntax.all.*
import trading.domain.AppError
import trading.domain.addresses.WalletAddress
import trading.domain.money.{Amount, CurrencyCode}
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.implicits.*
import org.typelevel.log4cats.Logger

final case class FireblocksConfig(
    baseUri: Uri = uri"https://api.fireblocks.io",
    apiKey: String,
    apiSecretPemPath: String, // path to RSA-2048 private key (mounted secret)
    vaultAccountId: String
)

/** Custody = takes possession of customer crypto + initiates payouts. Fireblocks signs each request with a JWT carrying
  * a sha256(body) claim; this skeleton wires the request shape but does not implement signing.
  */
trait Custody[F[_]] {
  def transferOut(to: WalletAddress, amount: Amount, ccy: CurrencyCode, externalId: String): F[String]
  def getDepositAddress(ccy: CurrencyCode): F[WalletAddress]
}

object Custody {

  def fireblocks[F[_]: Async: Logger](client: Client[F], cfg: FireblocksConfig): Custody[F] = {
    val _ = (client, cfg)
    new Custody[F] {
      def transferOut(to: WalletAddress, amount: Amount, ccy: CurrencyCode, externalId: String): F[String] = {
        Logger[F].info(
          s"[FIREBLOCKS STUB] transfer ${amount.value} ${ccy: String} → $to (extId=$externalId)"
        ) *>
          Async[F].raiseError(AppError.Upstream("FIREBLOCKS", "transferOut not implemented"))
      }

      def getDepositAddress(ccy: CurrencyCode): F[WalletAddress] = {
        Async[F].raiseError(AppError.Upstream("FIREBLOCKS", "getDepositAddress not implemented"))
      }
    }
  }

}
