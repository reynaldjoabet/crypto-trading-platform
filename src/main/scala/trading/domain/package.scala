package trading
import java.time.Instant

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
package object domain {

  /** Shared constraints */
  type UuidText =
    DescribedAs[ValidUUID, "Expected UUID text"]

  type SlugText =
    DescribedAs[
      Match["^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$"],
      "Expected lower-case slug"
    ]

  type NonBlank128 =
    DescribedAs[
      Trimmed & Not[Blank] & MinLength[1] & MaxLength[128],
      "Expected trimmed non-blank text up to 128 chars"
    ]

  type SymbolText =
    DescribedAs[
      Match["^[A-Z][A-Z0-9]{1,11}$"],
      "Expected upper-case asset symbol, 2-12 chars"
    ]

  type FiatCurrencyText =
    DescribedAs[
      Match["^[A-Z]{3}$"],
      "Expected ISO-like 3-letter fiat currency"
    ]

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

  type IdempotencyKeyText =
    DescribedAs[
      Trimmed & MinLength[8] & MaxLength[128],
      "Expected idempotency key between 8 and 128 chars"
    ]

  /** Strong IDs */
  type WalletId = WalletId.T
  object WalletId extends RefinedType[String, UuidText]

  type OwnerId = OwnerId.T
  object OwnerId extends RefinedType[String, UuidText]

  type AssetId = AssetId.T
  object AssetId extends RefinedType[String, UuidText]

  type TransferId = TransferId.T
  object TransferId extends RefinedType[String, UuidText]

  /** Human/business values */
  type BlockchainId = BlockchainId.T
  object BlockchainId extends RefinedSubtype[String, SlugText]

  type DisplayName = DisplayName.T
  object DisplayName extends RefinedSubtype[String, NonBlank128]

  type AssetSymbol = AssetSymbol.T
  object AssetSymbol extends RefinedSubtype[String, SymbolText]

  type FiatCurrency = FiatCurrency.T
  object FiatCurrency extends RefinedSubtype[String, FiatCurrencyText]

  type WalletLabel = WalletLabel.T
  object WalletLabel extends RefinedSubtype[String, NonBlank128]

  type IdempotencyKey = IdempotencyKey.T
  object IdempotencyKey extends RefinedType[String, IdempotencyKeyText]

  /** Chain-specific primitive values */
  type EvmChainId = EvmChainId.T
  object EvmChainId extends RefinedSubtype[Int, Positive]

  type TokenDecimals = TokenDecimals.T
  object TokenDecimals extends RefinedSubtype[Int, Interval.Closed[0, 36]]

  type ConfirmationCount = ConfirmationCount.T
  object ConfirmationCount extends RefinedSubtype[Int, Interval.Closed[0, 256]]

  type BlockHeight = BlockHeight.T
  object BlockHeight extends RefinedSubtype[Long, Positive0]

  type AtomicUnits = AtomicUnits.T
  object AtomicUnits extends RefinedSubtype[BigInt, Positive0] {
    val zero: AtomicUnits = AtomicUnits.applyUnsafe(BigInt(0))
  }

  /** Positive amount used when transferring/debiting. */
  type TransferUnits = TransferUnits.T
  object TransferUnits extends RefinedSubtype[BigInt, Positive]

  type EvmAddress = EvmAddress.T
  object EvmAddress extends RefinedSubtype[String, EvmAddressText]

  type EvmTxHash = EvmTxHash.T
  object EvmTxHash extends RefinedSubtype[String, EvmTxHashText]

  type SolanaAddress = SolanaAddress.T
  object SolanaAddress extends RefinedSubtype[String, SolanaAddressText]

  type SolanaSignature = SolanaSignature.T
  object SolanaSignature extends RefinedSubtype[String, SolanaSignatureText]

  enum WalletStatus {
    case Active
    case Frozen
    case Closed
  }

  enum AddressStatus {
    case Active
    case Disabled
  }

  final case class WalletAddress(
      blockchainId: BlockchainId,
      address: BlockchainAddress,
      status: AddressStatus
  )

  type WalletAddressList = WalletAddressList.T
  object WalletAddressList extends RefinedSubtype[List[WalletAddress], MinLength[1]]

  final case class Wallet(
      id: WalletId,
      ownerId: OwnerId,
      label: WalletLabel,
      addresses: WalletAddressList,
      status: WalletStatus,
      createdAt: Instant
  ) {
    def addressOn(blockchainId: BlockchainId): Option[WalletAddress] = {
      addresses.find(_.blockchainId == blockchainId)
    }
  }

  enum BlockchainProtocol {
    case Evm
    case Solana
    case Bitcoin
  }

  enum NetworkKind {
    case Mainnet
    case Testnet
    case Devnet
  }

  enum NetworkStatus {
    case Active
    case Degraded
    case Disabled
  }

  enum ChainReference {
    case Evm(chainId: EvmChainId)
    case Named(value: BlockchainId)
  }

  final case class Blockchain(
      id: BlockchainId,
      name: DisplayName,
      protocol: BlockchainProtocol,
      networkKind: NetworkKind,
      reference: ChainReference,
      nativeSymbol: AssetSymbol,
      minConfirmations: ConfirmationCount,
      status: NetworkStatus
  )

  enum BlockchainAddress {
    case Evm(value: EvmAddress)
    case Solana(value: SolanaAddress)

    def protocol: BlockchainProtocol = {
      this match {
        case BlockchainAddress.Evm(_)    => BlockchainProtocol.Evm
        case BlockchainAddress.Solana(_) => BlockchainProtocol.Solana
      }
    }
  }

  enum TransactionId {
    case Evm(value: EvmTxHash)
    case Solana(value: SolanaSignature)

    def protocol: BlockchainProtocol = {
      this match {
        case TransactionId.Evm(_)    => BlockchainProtocol.Evm
        case TransactionId.Solana(_) => BlockchainProtocol.Solana
      }
    }
  }

  final case class BlockRef(
      height: BlockHeight,
      hash: Option[String]
  )

  enum TokenStandard {
    case Native
    case Erc20
    case SplToken
  }

  enum AssetDeploymentStatus {
    case Active
    case Paused
    case Deprecated
  }

  enum AssetLocator derives CanEqual {
    case Native
    case EvmContract(value: EvmAddress)
    case SolanaMint(value: SolanaAddress)

    def protocol: Option[BlockchainProtocol] = {
      this match {
        case AssetLocator.Native         => None
        case AssetLocator.EvmContract(_) => Some(BlockchainProtocol.Evm)
        case AssetLocator.SolanaMint(_)  => Some(BlockchainProtocol.Solana)
      }
    }
  }

  final case class TokenDeployment(
      blockchainId: BlockchainId,
      locator: AssetLocator,
      standard: TokenStandard,
      status: AssetDeploymentStatus
  )

  type DeploymentList = DeploymentList.T
  object DeploymentList extends RefinedSubtype[List[TokenDeployment], MinLength[1]]

  enum StablecoinMechanism {
    case FiatBacked
    case CryptoBacked
    case CommodityBacked
    case Algorithmic
    case Other
  }

  enum Peg {
    case Fiat(currency: FiatCurrency)
    case Crypto(assetId: AssetId)
    case Commodity(symbol: AssetSymbol)
  }

  enum AssetStatus {
    case Active
    case Paused
    case Deprecated
  }

  final case class Stablecoin(
      assetId: AssetId,
      symbol: AssetSymbol,
      name: DisplayName,
      decimals: TokenDecimals,
      peg: Peg,
      mechanism: StablecoinMechanism,
      deployments: DeploymentList,
      status: AssetStatus
  ) {
    def deploymentOn(blockchainId: BlockchainId): Option[TokenDeployment] = {
      deployments.find(_.blockchainId == blockchainId)
    }
  }
  enum DomainError {
    case WalletNotFound(walletId: WalletId)
    case AssetNotFound(assetId: AssetId)
    case BlockchainNotFound(blockchainId: BlockchainId)
    case WalletAddressNotFound(walletId: WalletId, blockchainId: BlockchainId)
    case AssetNotDeployed(assetId: AssetId, blockchainId: BlockchainId)
    case InvalidAddressForChain(blockchainId: BlockchainId, address: BlockchainAddress)
    case InvalidAssetLocatorForChain(blockchainId: BlockchainId, locator: AssetLocator)
    case InsufficientFunds(assetId: AssetId, available: AtomicUnits, requested: TransferUnits)
    case ChainClientFailed(message: String)
  }

  final case class TokenBalance(
      walletId: WalletId,
      assetId: AssetId,
      units: AtomicUnits
  ) {
    def credit(amount: TransferUnits): TokenBalance = {
      copy(units = AtomicUnits.applyUnsafe(units + amount))
    }

    def debit(amount: TransferUnits): Either[DomainError, TokenBalance] = {
      if (units >= amount) {
        Right(copy(units = AtomicUnits.applyUnsafe(units - amount)))
      } else {
        Left(DomainError.InsufficientFunds(assetId, units, amount))
      }
    }
  }

  enum TransferStatus {
    case Pending
    case Submitted
    case Confirmed
    case Failed
  }

  final case class TransferCommand(
      transferId: TransferId,
      walletId: WalletId,
      assetId: AssetId,
      blockchainId: BlockchainId,
      to: BlockchainAddress,
      amount: TransferUnits,
      idempotencyKey: IdempotencyKey
  )

  final case class SubmittedTransfer(
      id: TransferId,
      walletId: WalletId,
      assetId: AssetId,
      blockchainId: BlockchainId,
      txId: TransactionId,
      amount: TransferUnits,
      status: TransferStatus,
      submittedAt: Instant
  )

}
