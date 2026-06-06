package trading.domain.wallet

import java.time.Instant

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

import trading.domain.core.*
import trading.domain.money.*
import trading.domain.chain.*

type DerivationPathText =
  DescribedAs[
    Match["^m(/[0-9]+'?){1,12}$"],
    "Expected BIP-style derivation path"
  ]

type DerivationPath = DerivationPath.T
object DerivationPath extends RefinedSubtype[String, DerivationPathText]

enum OwnerKind {
  case Individual
  case Business
  case InternalSystem
}

enum OwnerStatus {
  case Active
  case Restricted
  case Suspended
  case Closed
}

final case class Owner(
    id: OwnerId,
    tenantId: TenantId,
    kind: OwnerKind,
    status: OwnerStatus,
    displayName: DisplayName,
    version: AggregateVersion
)

enum CustodyModel {
  case Custodial
  case NonCustodial
  case ExternalObserved
}

enum WalletKind {
  case Customer
  case TreasuryHot
  case TreasuryWarm
  case TreasuryCold
  case FeeCollector
  case StablecoinIssuer
  case StablecoinRedemption
}

enum WalletStatus derives CanEqual {
  case Active
  case Frozen
  case WithdrawalsFrozen
  case DepositsFrozen
  case Closing
  case Closed
}

enum AddressPurpose derives CanEqual {
  case Deposit
  case WithdrawalSource
  case Change
  case Treasury
  case Contract
}

enum WalletAddressStatus derives CanEqual {
  case Active
  case Disabled
  case Retired
}

final case class WalletAddress(
    id: WalletAddressId,
    walletId: WalletId,
    blockchainId: BlockchainId,
    address: BlockchainAddress,
    purpose: AddressPurpose,
    derivationPath: Option[DerivationPath],
    verification: AddressVerification,
    status: WalletAddressStatus,
    createdAt: Instant
) derives CanEqual

enum DestinationPolicy {
  case AnyAddress
  case AllowlistedOnly
  case InternalOnly
  case Disabled
}

final case class WithdrawalLimit(
    assetId: AssetId,
    perTransaction: Option[PositiveAmount],
    daily: Option[PositiveAmount],
    monthly: Option[PositiveAmount]
)

type ApprovalThreshold = ApprovalThreshold.T
object ApprovalThreshold extends RefinedSubtype[Int, Interval.Closed[1, 10]]

final case class ApprovalPolicy(
    required: Boolean,
    threshold: Option[ApprovalThreshold],
    approverGroup: Option[ExternalReference]
)

final case class WalletPolicy(
    allowedAssets: Set[AssetId],
    allowedChains: Set[BlockchainId],
    destinationPolicy: DestinationPolicy,
    withdrawalLimits: List[WithdrawalLimit],
    approvalPolicy: ApprovalPolicy
) {

  def allowsAsset(assetId: AssetId): Boolean = {
    allowedAssets.isEmpty || allowedAssets.contains(assetId)
  }

  def allowsChain(blockchainId: BlockchainId): Boolean = {
    allowedChains.isEmpty || allowedChains.contains(blockchainId)
  }
}

final case class Wallet(
    id: WalletId,
    tenantId: TenantId,
    ownerId: OwnerId,
    kind: WalletKind,
    custodyModel: CustodyModel,
    label: Label,
    status: WalletStatus,
    addresses: List[WalletAddress],
    policy: WalletPolicy,
    audit: AuditInfo,
    version: AggregateVersion
) {

  def activeOrError: Either[DomainError, Wallet] = {
    status match {
      case WalletStatus.Active | WalletStatus.WithdrawalsFrozen | WalletStatus.DepositsFrozen =>
        Right(this)

      case _ =>
        Left(DomainError.WalletInactive(id))
    }
  }

  def withdrawalAllowed: Either[DomainError, Unit] = {
    status match {
      case WalletStatus.Active =>
        Right(())

      case WalletStatus.WithdrawalsFrozen =>
        Left(DomainError.WalletInactive(id))

      case _ =>
        Left(DomainError.WalletInactive(id))
    }
  }

  def depositAllowed: Either[DomainError, Unit] = {
    status match {
      case WalletStatus.Active =>
        Right(())

      case WalletStatus.DepositsFrozen =>
        Left(DomainError.WalletInactive(id))

      case _ =>
        Left(DomainError.WalletInactive(id))
    }
  }

  def addressOn(
      blockchainId: BlockchainId,
      purpose: AddressPurpose
  ): Option[WalletAddress] = {
    addresses.find { address =>
      address.blockchainId == blockchainId &&
      address.purpose == purpose &&
      address.status == WalletAddressStatus.Active
    }
  }

  def validatePolicy(
      assetId: AssetId,
      blockchainId: BlockchainId
  ): Either[DomainError, Unit] = {
    if (!policy.allowsAsset(assetId)) {
      Left(DomainError.ValidationFailed(s"Wallet policy does not allow asset $assetId"))
    } else if (!policy.allowsChain(blockchainId)) {
      Left(DomainError.ValidationFailed(s"Wallet policy does not allow blockchain $blockchainId"))
    } else {
      Right(())
    }
  }
}
