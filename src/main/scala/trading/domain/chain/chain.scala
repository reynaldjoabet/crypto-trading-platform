package trading.domain.chain

import java.time.Instant

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

import trading.domain.core.*
import trading.domain.money.*

type EvmAddressText =
  DescribedAs[
    Match["^0x[a-fA-F0-9]{40}$"],
    "Expected EVM address"
  ]

type EvmTxHashText =
  DescribedAs[
    Match["^0x[a-fA-F0-9]{64}$"],
    "Expected EVM transaction hash"
  ]

type SolanaAddressText =
  DescribedAs[
    Match["^[1-9A-HJ-NP-Za-km-z]{32,44}$"],
    "Expected Solana base58 address"
  ]

type SolanaSignatureText =
  DescribedAs[
    Match["^[1-9A-HJ-NP-Za-km-z]{64,88}$"],
    "Expected Solana transaction signature"
  ]

type BitcoinTxHashText =
  DescribedAs[
    Match["^[a-fA-F0-9]{64}$"],
    "Expected Bitcoin transaction hash"
  ]

type EvmChainId = EvmChainId.T
object EvmChainId extends RefinedSubtype[Long, Positive]

type EvmAddress = EvmAddress.T
object EvmAddress extends RefinedSubtype[String, EvmAddressText]

type EvmTxHash = EvmTxHash.T
object EvmTxHash extends RefinedSubtype[String, EvmTxHashText]

type SolanaAddress = SolanaAddress.T
object SolanaAddress extends RefinedSubtype[String, SolanaAddressText]

type SolanaSignature = SolanaSignature.T
object SolanaSignature extends RefinedSubtype[String, SolanaSignatureText]

type BitcoinTxHash = BitcoinTxHash.T
object BitcoinTxHash extends RefinedSubtype[String, BitcoinTxHashText]

enum BlockchainProtocol derives CanEqual {
  case Evm
  case Solana
  case Bitcoin
}

enum NetworkKind {
  case Mainnet
  case Testnet
  case Devnet
  case Regtest
}

enum BlockchainStatus derives CanEqual {
  case Active
  case ReadOnly
  case DepositsPaused
  case WithdrawalsPaused
  case Disabled
}

enum ChainReference {
  case Evm(chainId: EvmChainId)
  case Named(value: BlockchainId)
}

final case class ChainCapabilities(
    supportsTokenTransfers: Boolean,
    supportsSmartContracts: Boolean,
    supportsMempool: Boolean,
    supportsNonce: Boolean,
    supportsFeeMarket: Boolean,
    supportsFinality: Boolean
)

final case class Blockchain(
    id: BlockchainId,
    name: DisplayName,
    protocol: BlockchainProtocol,
    networkKind: NetworkKind,
    reference: ChainReference,
    nativeAssetId: AssetId,
    minConfirmations: ConfirmationCount,
    reorgTolerance: ConfirmationCount,
    capabilities: ChainCapabilities,
    status: BlockchainStatus,
    version: AggregateVersion
) {

  def acceptsDeposits: Boolean = {
    status == BlockchainStatus.Active
  }

  def acceptsWithdrawals: Boolean = {
    status == BlockchainStatus.Active
  }
}

enum BlockchainAddress(val value: String) derives CanEqual {
  case Evm(addr: EvmAddress) extends BlockchainAddress(addr)
  case Solana(addr: SolanaAddress) extends BlockchainAddress(addr)

  def protocol: BlockchainProtocol = {
    this match {
      case BlockchainAddress.Evm(_)    => BlockchainProtocol.Evm
      case BlockchainAddress.Solana(_) => BlockchainProtocol.Solana
    }
  }
}

enum AddressVerification {
  case StructurallyValid
  case ChecksumVerified
  case ChainVerified
  case OwnedByPlatform
}

final case class VerifiedBlockchainAddress(
    address: BlockchainAddress,
    verification: AddressVerification
)

enum TransactionId(val value: String) derives CanEqual {
  case Evm(hash: EvmTxHash) extends TransactionId(hash)
  case Solana(sig: SolanaSignature) extends TransactionId(sig)
  case Bitcoin(hash: BitcoinTxHash) extends TransactionId(hash)

  def protocol: BlockchainProtocol = {
    this match {
      case TransactionId.Evm(_)     => BlockchainProtocol.Evm
      case TransactionId.Solana(_)  => BlockchainProtocol.Solana
      case TransactionId.Bitcoin(_) => BlockchainProtocol.Bitcoin
    }
  }
}

final case class BlockRef(
    blockchainId: BlockchainId,
    height: BlockHeight,
    hash: Option[String],
    observedAt: Instant
)

enum ChainTxKind {
  case CustomerWithdrawal
  case DepositSweep
  case InternalTreasuryMove
  case StablecoinMint
  case StablecoinBurn
  case ContractCall
}

enum ChainTxStatus derives CanEqual {
  case Draft
  case AwaitingSignature
  case Signed
  case Broadcast
  case SeenInMempool
  case Included
  case Confirmed
  case Finalized
  case Failed
  case Dropped
  case Reorged
}

final case class ChainTx(
    id: ChainTxId,
    tenantId: TenantId,
    blockchainId: BlockchainId,
    kind: ChainTxKind,
    from: Option[BlockchainAddress],
    to: Option[BlockchainAddress],
    assetId: Option[AssetId],
    amount: Option[PositiveAmount],
    networkFee: Option[PositiveAmount],
    nonce: Option[ChainNonce],
    txId: Option[TransactionId],
    includedIn: Option[BlockRef],
    confirmations: ConfirmationCount,
    requiredConfirmations: ConfirmationCount,
    status: ChainTxStatus,
    idempotencyKey: Option[IdempotencyKey],
    createdAt: Instant,
    updatedAt: Instant,
    version: AggregateVersion
) {

  def canBeRebroadcast: Boolean = {
    status match {
      case ChainTxStatus.Signed | ChainTxStatus.Broadcast | ChainTxStatus.Dropped => true
      case _                                                                      => false
    }
  }

  def isTerminal: Boolean = {
    status match {
      case ChainTxStatus.Finalized | ChainTxStatus.Failed => true
      case _                                              => false
    }
  }
}
