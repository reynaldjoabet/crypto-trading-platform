package trading.domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

/** Wallet & email refinements. */
object addresses {

  // EVM addresses: 0x + 40 hex chars
  type EvmAddressRefined = String :| Match["^0x[a-fA-F0-9]{40}$"]
  opaque type EvmAddress = EvmAddressRefined
  object EvmAddress {
    inline def apply(s: String): Either[String, EvmAddress] = s.refineEither
    inline def unsafe(s: String): EvmAddress = s.refineUnsafe
    extension (a: EvmAddress) inline def value: String = a
  }

  // BTC: very loose — covers legacy, segwit, bech32. Tighten per-network in service code.
  type BtcAddressRefined = String :| Match["^(bc1|[13])[a-zA-HJ-NP-Z0-9]{25,87}$"]
  opaque type BtcAddress = BtcAddressRefined
  object BtcAddress {
    inline def apply(s: String): Either[String, BtcAddress] = s.refineEither
    inline def unsafe(s: String): BtcAddress = s.refineUnsafe
    extension (a: BtcAddress) inline def value: String = a
  }

  // Solana base58 (32-44 chars)
  type SolAddressRefined = String :| Match["^[1-9A-HJ-NP-Za-km-z]{32,44}$"]
  opaque type SolAddress = SolAddressRefined
  object SolAddress {
    inline def apply(s: String): Either[String, SolAddress] = s.refineEither
    inline def unsafe(s: String): SolAddress = s.refineUnsafe
    extension (a: SolAddress) inline def value: String = a
  }

  enum Chain derives CanEqual {
    case Ethereum, Polygon, Arbitrum, Optimism, Base, BSC, Bitcoin, Solana
  }

  /** Tagged wallet address for safer cross-chain routing. */
  enum WalletAddress derives CanEqual {
    case Evm(addr: EvmAddress, chain: Chain)
    case Btc(addr: BtcAddress)
    case Sol(addr: SolAddress)
  }

  type EmailRefined = String :| Match["^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"]
  opaque type Email = EmailRefined
  object Email {
    inline def apply(s: String): Either[String, Email] = s.refineEither
    inline def unsafe(s: String): Email = s.refineUnsafe
    extension (e: Email) inline def value: String = e
  }

}
