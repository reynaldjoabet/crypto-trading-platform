package trading.domain.asset

import java.time.Instant

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

import trading.domain.core.*
import trading.domain.money.*
import trading.domain.chain.*

enum AssetKind {
  case Native
  case Stablecoin
  case Wrapped
  case UtilityToken
}

enum AssetStatus derives CanEqual {
  case Active
  case Paused
  case Deprecated
  case Delisted
}

final case class Asset(
    id: AssetId,
    symbol: AssetSymbol,
    name: DisplayName,
    kind: AssetKind,
    decimals: TokenDecimals,
    status: AssetStatus,
    version: AggregateVersion
) {

  def activeOrError: Either[DomainError, Asset] = {
    if (status == AssetStatus.Active) {
      Right(this)
    } else {
      Left(DomainError.AssetInactive(id))
    }
  }
}

enum TokenStandard {
  case Native
  case Erc20
  case SplToken
  case Other
}

enum AssetDeploymentStatus derives CanEqual {
  case Active
  case DepositsPaused
  case WithdrawalsPaused
  case Paused
  case Deprecated
}

enum AssetLocator derives CanEqual {
  case Native
  case EvmContract(address: EvmAddress)
  case SolanaMint(address: SolanaAddress)

  def protocol: Option[BlockchainProtocol] = {
    this match {
      case AssetLocator.Native         => None
      case AssetLocator.EvmContract(_) => Some(BlockchainProtocol.Evm)
      case AssetLocator.SolanaMint(_)  => Some(BlockchainProtocol.Solana)
    }
  }
}

final case class AssetDeployment(
    assetId: AssetId,
    blockchainId: BlockchainId,
    standard: TokenStandard,
    locator: AssetLocator,
    chainDecimals: TokenDecimals,
    requiredConfirmations: ConfirmationCount,
    minDeposit: Option[PositiveAmount],
    minWithdrawal: Option[PositiveAmount],
    maxWithdrawal: Option[PositiveAmount],
    withdrawalFeePolicy: FeePolicy,
    status: AssetDeploymentStatus,
    version: AggregateVersion
) {

  def depositsEnabled: Boolean = {
    status == AssetDeploymentStatus.Active || status == AssetDeploymentStatus.WithdrawalsPaused
  }

  def withdrawalsEnabled: Boolean = {
    status == AssetDeploymentStatus.Active || status == AssetDeploymentStatus.DepositsPaused
  }

  def validateForChain(chain: Blockchain): Either[DomainError, Unit] = {
    locator.protocol match {
      case None =>
        Right(())

      case Some(protocol) if protocol == chain.protocol =>
        Right(())

      case Some(_) =>
        Left(
          DomainError.InvalidAssetLocatorForChain(
            chain.id,
            s"Asset locator ${locator.toString} does not match chain protocol ${chain.protocol.toString}"
          )
        )
    }
  }
}

enum StablecoinMechanism {
  case FiatBacked
  case CryptoBacked
  case CommodityBacked
  case Algorithmic
  case Hybrid
}

enum Peg {
  case Fiat(currency: FiatCurrency)
  case Crypto(assetId: AssetId)
  case Commodity(symbol: AssetSymbol)
  case Basket(items: PegBasket)
}

final case class PegBasketItem(
    assetId: AssetId,
    weight: BasisPoints
)

type PegBasketItems = PegBasketItems.T
object PegBasketItems extends RefinedSubtype[List[PegBasketItem], MinLength[1]]

final case class PegBasket private (
    items: PegBasketItems
)

object PegBasket {

  def make(items: List[PegBasketItem]): Either[DomainError, PegBasket] = {
    for {
      refinedItems <- PegBasketItems.either(items).left.map(DomainError.ValidationFailed.apply)
      totalWeight = refinedItems.foldLeft(0)(_ + _.weight)
      _ <- Either.cond(
        totalWeight == 10000,
        (),
        DomainError.ValidationFailed(s"Basket weights must sum to 10000 bps, got $totalWeight")
      )
    } yield {
      PegBasket(refinedItems)
    }
  }
}

enum ReserveProofStatus {
  case NotRequired
  case Pending
  case Verified
  case Stale
  case Failed
}

final case class StablecoinProfile(
    assetId: AssetId,
    peg: Peg,
    mechanism: StablecoinMechanism,
    issuerName: DisplayName,
    redemptionSupported: Boolean,
    reserveProofStatus: ReserveProofStatus,
    updatedAt: Instant
)

final case class Stablecoin(
    asset: Asset,
    profile: StablecoinProfile,
    deployments: List[AssetDeployment]
) {

  def deploymentOn(blockchainId: BlockchainId): Option[AssetDeployment] = {
    deployments.find(_.blockchainId == blockchainId)
  }

  def activeDeploymentOn(blockchainId: BlockchainId): Either[DomainError, AssetDeployment] = {
    deploymentOn(blockchainId) match {
      case Some(deployment) if deployment.status == AssetDeploymentStatus.Active =>
        Right(deployment)

      case Some(_) =>
        Left(DomainError.AssetInactive(asset.id))

      case None =>
        Left(DomainError.AssetNotDeployed(asset.id, blockchainId))
    }
  }
}
