package trading.domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

object addresses {

  type EvmAddress = EvmAddress.T
  object EvmAddress extends RefinedType[String, Match["^0x[a-fA-F0-9]{40}$"]]

  type BtcAddress = BtcAddress.T
  object BtcAddress extends RefinedType[String, Match["^(bc1|[13])[a-zA-HJ-NP-Z0-9]{25,87}$"]]

  type SolAddress = SolAddress.T
  object SolAddress extends RefinedType[String, Match["^[1-9A-HJ-NP-Za-km-z]{32,44}$"]]

  enum Chain derives CanEqual {
    case Ethereum, Polygon, Arbitrum, Optimism, Base, BSC, Bitcoin, Solana
  }

  enum WalletAddress derives CanEqual {
    case Evm(addr: EvmAddress, chain: Chain)
    case Btc(addr: BtcAddress)
    case Sol(addr: SolAddress)
  }

  type Email = Email.T
  object Email extends RefinedType[String, Match["^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"]]

}
