package trading.wallets

import cats.effect.*
import cats.effect.std.UUIDGen
import cats.syntax.all.*
import trading.domain.AppError
import trading.domain.addresses.Chain
import trading.domain.addresses.WalletAddress
import trading.domain.ids.UserId
import trading.domain.ids.WalletId

import java.time.Instant

final case class WalletRecord(
    id: WalletId,
    userId: UserId,
    label: String,
    chain: Chain,
    address: String, // serialised form of WalletAddress
    isVerified: Boolean,
    createdAt: Instant
)

trait WalletService[F[_]] {
  def link(userId: UserId, label: String, addr: WalletAddress): F[WalletRecord]
  def list(userId: UserId): F[List[WalletRecord]]
  def verifyOwnership(userId: UserId, walletId: WalletId, signature: String, nonce: String): F[Unit]
}

/** Wallet ownership verification is "sign-this-nonce" style — we issue a per-user nonce, the client signs it with their
  * wallet (Metamask/WalletConnect), and we recover the address. This skeleton stubs the verification path.
  */
object WalletService {

  def stub[F[_]: Sync: UUIDGen]: WalletService[F] = {
    new WalletService[F] {

      def link(userId: UserId, label: String, addr: WalletAddress): F[WalletRecord] = {
        for {
          id <- UUIDGen[F].randomUUID
          now <- Clock[F].realTimeInstant
          (chain, addrStr) = serialise(addr)
        } yield WalletRecord(WalletId(id), userId, label, chain, addrStr, isVerified = false, now)
      }

      def list(userId: UserId): F[List[WalletRecord]] = {
        Sync[F].pure(List.empty)
      }

      def verifyOwnership(userId: UserId, walletId: WalletId, signature: String, nonce: String): F[Unit] = {
        Sync[F].raiseError(AppError.Internal("wallet ownership verification not implemented"))
      }
    }
  }

  private def serialise(a: WalletAddress): (Chain, String) = {
    a match {
      case WalletAddress.Evm(addr, chain) => (chain, addr.value)
      case WalletAddress.Btc(addr)        => (Chain.Bitcoin, addr.value)
      case WalletAddress.Sol(addr)        => (Chain.Solana, addr.value)
    }
  }

}
