package trading.domain.core

import java.time.Instant

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

type UuidText =
  DescribedAs[
    Match["^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"],
    "Expected UUID text"
  ]

type SlugText =
  DescribedAs[
    Match["^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$"],
    "Expected lower-case slug"
  ]

type NonBlank64 =
  DescribedAs[
    Trimmed & MinLength[1] & MaxLength[64],
    "Expected non-blank text up to 64 characters"
  ]

type NonBlank128 =
  DescribedAs[
    Trimmed & MinLength[1] & MaxLength[128],
    "Expected non-blank text up to 128 characters"
  ]

type AssetSymbolText =
  DescribedAs[
    Match["^[A-Z][A-Z0-9]{1,15}$"],
    "Expected upper-case asset symbol"
  ]

type FiatCurrencyText =
  DescribedAs[
    Match["^[A-Z]{3}$"],
    "Expected 3-letter fiat currency code"
  ]

type NonBlank256 =
  DescribedAs[
    Trimmed & MinLength[1] & MaxLength[256],
    "Expected non-blank text up to 256 characters"
  ]

type NonBlank1024 =
  DescribedAs[
    Trimmed & MinLength[1] & MaxLength[1024],
    "Expected non-blank text up to 1024 characters"
  ]

type NonBlank8192 =
  DescribedAs[
    Trimmed & MinLength[1] & MaxLength[8192],
    "Expected non-blank text up to 8192 characters"
  ]

type HttpsUriText =
  DescribedAs[
    Match["^https://[^ #]+$"],
    "Expected HTTPS URI"
  ]

type AbsoluteUriNoFragmentText =
  DescribedAs[
    Match["^[a-zA-Z][a-zA-Z0-9+.-]*:[^# ]+$"],
    "Expected absolute URI without fragment"
  ]

type ScopeNameText =
  DescribedAs[
    Match["^[A-Za-z0-9:._/-]{1,128}$"],
    "Expected OAuth scope name"
  ]

type ClaimNameText =
  DescribedAs[
    Match["^[A-Za-z_][A-Za-z0-9_:.@/-]{0,127}$"],
    "Expected OIDC claim name"
  ]

type AcrText =
  DescribedAs[
    Match["^[A-Za-z0-9:._/-]{1,128}$"],
    "Expected ACR value"
  ]

type AmrText =
  DescribedAs[
    Match["^[A-Za-z0-9:._/-]{1,64}$"],
    "Expected AMR value"
  ]

type JwtCompactText =
  DescribedAs[
    Match["^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*$"],
    "Expected compact JWS"
  ]

type TokenValueText =
  DescribedAs[
    Trimmed & MinLength[16] & MaxLength[8192],
    "Expected token value"
  ]

type SubjectText =
  DescribedAs[
    Trimmed & MinLength[1] & MaxLength[255],
    "Expected OIDC subject up to 255 characters"
  ]

type ClientIdText =
  DescribedAs[
    Trimmed & MinLength[1] & MaxLength[128],
    "Expected client_id"
  ]

type KeyIdText =
  DescribedAs[
    Trimmed & MinLength[1] & MaxLength[128],
    "Expected JOSE key id"
  ]

type Sha256HashText =
  DescribedAs[
    Match["^[A-Za-z0-9_-]{32,128}$"],
    "Expected base64url/hash text"
  ]

type TenantId = TenantId.T
object TenantId extends RefinedType[String, UuidText]

type IssuerId = IssuerId.T
object IssuerId extends RefinedType[String, UuidText]

type EndUserId = EndUserId.T
object EndUserId extends RefinedType[String, UuidText]

type SessionId = SessionId.T
object SessionId extends RefinedType[String, UuidText]

type ClientId = ClientId.T
object ClientId extends RefinedType[String, ClientIdText]
type GrantId = GrantId.T
object GrantId extends RefinedType[String, UuidText]

type ConsentId = ConsentId.T
object ConsentId extends RefinedType[String, UuidText]

type AuthorizationInteractionId = AuthorizationInteractionId.T
object AuthorizationInteractionId extends RefinedType[String, UuidText]

type TokenFamilyId = TokenFamilyId.T
object TokenFamilyId extends RefinedType[String, UuidText]

type AccessTokenId = AccessTokenId.T
object AccessTokenId extends RefinedType[String, UuidText]

type RefreshTokenId = RefreshTokenId.T
object RefreshTokenId extends RefinedType[String, UuidText]

type SigningKeyId = SigningKeyId.T
object SigningKeyId extends RefinedType[String, UuidText]

type AuditEventId = AuditEventId.T
object AuditEventId extends RefinedType[String, UuidText]

type IssuerUrl = IssuerUrl.T
object IssuerUrl extends RefinedSubtype[String, HttpsUriText]

type EndpointUrl = EndpointUrl.T
object EndpointUrl extends RefinedSubtype[String, HttpsUriText]

type RedirectUri = RedirectUri.T
object RedirectUri extends RefinedSubtype[String, AbsoluteUriNoFragmentText]

type ScopeName = ScopeName.T
object ScopeName extends RefinedSubtype[String, ScopeNameText] {
  val openid: ScopeName = ScopeName.applyUnsafe("openid")
  val profile: ScopeName = ScopeName.applyUnsafe("profile")
  val email: ScopeName = ScopeName.applyUnsafe("email")
  val phone: ScopeName = ScopeName.applyUnsafe("phone")
  val address: ScopeName = ScopeName.applyUnsafe("address")
  val offlineAccess: ScopeName = ScopeName.applyUnsafe("offline_access")
}

type ClaimName = ClaimName.T
object ClaimName extends RefinedSubtype[String, ClaimNameText] {
  val sub: ClaimName = ClaimName.applyUnsafe("sub")
  val name: ClaimName = ClaimName.applyUnsafe("name")
  val email: ClaimName = ClaimName.applyUnsafe("email")
  val emailVerified: ClaimName = ClaimName.applyUnsafe("email_verified")
  val phoneNumber: ClaimName = ClaimName.applyUnsafe("phone_number")
  val address: ClaimName = ClaimName.applyUnsafe("address")
}

type Subject = Subject.T
object Subject extends RefinedSubtype[String, SubjectText]

type Acr = Acr.T
object Acr extends RefinedSubtype[String, AcrText]

type Amr = Amr.T
object Amr extends RefinedSubtype[String, AmrText]

type State = State.T
object State extends RefinedSubtype[String, NonBlank1024]

type Nonce = Nonce.T
object Nonce extends RefinedSubtype[String, NonBlank1024]

type LoginHint = LoginHint.T
object LoginHint extends RefinedSubtype[String, NonBlank256]

type DisplayName = DisplayName.T
object DisplayName extends RefinedSubtype[String, NonBlank128]

type JsonObjectText = JsonObjectText.T
object JsonObjectText extends RefinedSubtype[String, NonBlank8192]

type CompactJwt = CompactJwt.T
object CompactJwt extends RefinedSubtype[String, JwtCompactText]

type RawTokenValue = RawTokenValue.T
object RawTokenValue extends RefinedSubtype[String, TokenValueText]

type TokenHash = TokenHash.T
object TokenHash extends RefinedSubtype[String, Sha256HashText]

type KeyId = KeyId.T
object KeyId extends RefinedSubtype[String, KeyIdText]

type PositiveSeconds = PositiveSeconds.T
object PositiveSeconds extends RefinedSubtype[Long, Positive]

type NonNegativeSeconds = NonNegativeSeconds.T
object NonNegativeSeconds extends RefinedSubtype[Long, Positive0]

type AggregateVersion = AggregateVersion.T
object AggregateVersion extends RefinedSubtype[Long, Positive0] {
  val initial: AggregateVersion = AggregateVersion.applyUnsafe(0L)
}

final case class ScopeSet private (
    values: Set[ScopeName]
) {

  def contains(scope: ScopeName): Boolean = {
    values.contains(scope)
  }

  def requiresOpenId: Boolean = {
    values.contains(ScopeName.openid)
  }

  def +(scope: ScopeName): ScopeSet = {
    ScopeSet(values + scope)
  }

  def intersect(allowed: ScopeSet): ScopeSet = {
    ScopeSet(values.intersect(allowed.values))
  }

  def asWire: String = {
    values.map(_.toString).toList.sorted.mkString(" ")
  }
}

object ScopeSet {

  val empty: ScopeSet = ScopeSet(Set.empty)

  def make(values: Set[ScopeName]): Either[OidcDomainError, ScopeSet] = {
    Right(ScopeSet(values))
  }

  def requireOpenId(values: Set[ScopeName]): Either[OidcDomainError, ScopeSet] = {
    if (values.contains(ScopeName.openid)) {
      Right(ScopeSet(values))
    } else {
      Left(OidcDomainError.ProtocolFailure(OAuthErrorCode.InvalidScope, Some("OIDC requests require openid scope")))
    }
  }
}

enum OAuthErrorCode(val wire: String) {
  case InvalidRequest extends OAuthErrorCode("invalid_request")
  case UnauthorizedClient extends OAuthErrorCode("unauthorized_client")
  case AccessDenied extends OAuthErrorCode("access_denied")
  case UnsupportedResponseType extends OAuthErrorCode("unsupported_response_type")
  case InvalidScope extends OAuthErrorCode("invalid_scope")
  case ServerError extends OAuthErrorCode("server_error")
  case TemporarilyUnavailable extends OAuthErrorCode("temporarily_unavailable")
  case InvalidClient extends OAuthErrorCode("invalid_client")
  case InvalidGrant extends OAuthErrorCode("invalid_grant")
  case InvalidToken extends OAuthErrorCode("invalid_token")
  case UnsupportedGrantType extends OAuthErrorCode("unsupported_grant_type")

  case LoginRequired extends OAuthErrorCode("login_required")
  case InteractionRequired extends OAuthErrorCode("interaction_required")
  case ConsentRequired extends OAuthErrorCode("consent_required")
  case AccountSelectionRequired extends OAuthErrorCode("account_selection_required")
}

final case class ProtocolError(
    code: OAuthErrorCode,
    description: Option[String],
    errorUri: Option[EndpointUrl]
)

enum OidcDomainError {
  case ValidationFailed(message: String)
  case ProtocolFailure(code: OAuthErrorCode, description: Option[String])

  case TenantNotFound(tenantId: TenantId)
  case IssuerNotFound(issuerId: IssuerId)
  case ClientNotFound(clientId: ClientId)
  case EndUserNotFound(endUserId: EndUserId)
  case SessionNotFound(sessionId: SessionId)

  case ClientDisabled(clientId: ClientId)
  case ClientPolicyViolation(clientId: ClientId, message: String)
  case InvalidRedirectUri(clientId: ClientId, redirectUri: RedirectUri)
  case InvalidScope(clientId: ClientId, scope: ScopeName)
  case InvalidPrompt(message: String)

  case LoginRequired
  case ConsentRequired
  case AccessDenied(message: String)
  case MaxAgeExceeded
  case AcrNotSatisfied(required: Set[Acr], actual: Option[Acr])

  case InteractionNotFound(id: AuthorizationInteractionId)
  case InteractionExpired(id: AuthorizationInteractionId)
  case InteractionAlreadyCompleted(id: AuthorizationInteractionId)

  case GrantNotFound(grantId: GrantId)
  case GrantRevoked(grantId: GrantId)
  case InvalidGrant(message: String)

  case TokenNotFound
  case TokenExpired
  case TokenRevoked
  case RefreshTokenReuseDetected(familyId: TokenFamilyId)

  case KeyNotFound(kid: KeyId)
  case SigningFailure(message: String)
  case AuthleteFailed(message: String)
  case RepositoryFailed(message: String)

  case ConcurrencyConflict(entity: String, expected: AggregateVersion, actual: AggregateVersion)
}

type IdempotencyKeyText =
  DescribedAs[
    Trimmed & MinLength[8] & MaxLength[128],
    "Expected idempotency key between 8 and 128 characters"
  ]

type ExternalReferenceText =
  DescribedAs[Trimmed & MinLength[1] & MaxLength[128], "Expected external reference up to 128 characters"]

type OwnerId = OwnerId.T
object OwnerId extends RefinedType[String, UuidText]

type WalletId = WalletId.T
object WalletId extends RefinedType[String, UuidText]

type WalletAddressId = WalletAddressId.T
object WalletAddressId extends RefinedType[String, UuidText]

type AssetId = AssetId.T
object AssetId extends RefinedType[String, UuidText] {
  given CanEqual[AssetId, AssetId] = CanEqual.derived
}

type BlockchainId = BlockchainId.T
object BlockchainId extends RefinedType[String, SlugText] {
  given CanEqual[BlockchainId, BlockchainId] = CanEqual.derived
}

type LedgerAccountId = LedgerAccountId.T
object LedgerAccountId extends RefinedType[String, UuidText]

type LedgerTxId = LedgerTxId.T
object LedgerTxId extends RefinedType[String, UuidText]

type ReservationId = ReservationId.T
object ReservationId extends RefinedType[String, UuidText]

type WithdrawalId = WithdrawalId.T
object WithdrawalId extends RefinedType[String, UuidText]

type DepositId = DepositId.T
object DepositId extends RefinedType[String, UuidText]

type ChainTxId = ChainTxId.T
object ChainTxId extends RefinedType[String, UuidText]

type StablecoinOperationId = StablecoinOperationId.T
object StablecoinOperationId extends RefinedType[String, UuidText]

type ReserveReportId = ReserveReportId.T
object ReserveReportId extends RefinedType[String, UuidText]

type IdempotencyKey = IdempotencyKey.T
object IdempotencyKey extends RefinedType[String, IdempotencyKeyText]

type ExternalReference = ExternalReference.T
object ExternalReference extends RefinedType[String, ExternalReferenceText]

type Label = Label.T
object Label extends RefinedSubtype[String, NonBlank64]

type AssetSymbol = AssetSymbol.T
object AssetSymbol extends RefinedSubtype[String, AssetSymbolText]

type FiatCurrency = FiatCurrency.T
object FiatCurrency extends RefinedSubtype[String, FiatCurrencyText]

type TokenDecimals = TokenDecimals.T
object TokenDecimals extends RefinedSubtype[Int, Interval.Closed[0, 36]]

type ConfirmationCount = ConfirmationCount.T
object ConfirmationCount extends RefinedSubtype[Int, Interval.Closed[0, 2048]]

type BlockHeight = BlockHeight.T
object BlockHeight extends RefinedSubtype[Long, Positive0]

type TransactionIndex = TransactionIndex.T
object TransactionIndex extends RefinedSubtype[Int, Positive0]

type LogIndex = LogIndex.T
object LogIndex extends RefinedSubtype[Int, Positive0]

type ChainNonce = ChainNonce.T
object ChainNonce extends RefinedSubtype[BigInt, Positive0]

type BasisPoints = BasisPoints.T
object BasisPoints extends RefinedSubtype[Int, Interval.Closed[0, 10000]]

type AtomicUnits = AtomicUnits.T
object AtomicUnits extends RefinedSubtype[BigInt, Positive0] {
  val zero: AtomicUnits = AtomicUnits.applyUnsafe(BigInt(0))
}

type PositiveUnits = PositiveUnits.T
object PositiveUnits extends RefinedSubtype[BigInt, Positive]

enum DomainError {
  case ValidationFailed(message: String)
  case AssetMismatch(expected: AssetId, actual: AssetId)
  case AmountMustBePositive(assetId: AssetId)
  case InsufficientFunds(assetId: AssetId, available: AtomicUnits, requested: PositiveUnits)

  case TenantNotFound(tenantId: TenantId)
  case OwnerNotFound(ownerId: OwnerId)
  case WalletNotFound(walletId: WalletId)
  case AssetNotFound(assetId: AssetId)
  case BlockchainNotFound(blockchainId: BlockchainId)

  case WalletInactive(walletId: WalletId)
  case AssetInactive(assetId: AssetId)
  case BlockchainInactive(blockchainId: BlockchainId)
  case AssetNotDeployed(assetId: AssetId, blockchainId: BlockchainId)

  case InvalidAddressForChain(blockchainId: BlockchainId, message: String)
  case InvalidAssetLocatorForChain(blockchainId: BlockchainId, message: String)

  case UnbalancedLedgerTransaction(message: String)
  case LedgerAccountMismatch(message: String)
  case DuplicateIdempotencyKey(key: IdempotencyKey)

  case ReservationNotFound(reservationId: ReservationId)
  case ReservationNotAvailable(reservationId: ReservationId)
  case InvalidStateTransition(entity: String, from: String, to: String)

  case ComplianceRejected(reason: String)
  case LimitExceeded(reason: String)
  case ChainClientFailed(message: String)
  case ConcurrencyConflict(entity: String, expected: AggregateVersion, actual: AggregateVersion)
}

final case class AuditInfo(
    createdAt: Instant,
    updatedAt: Instant,
    createdBy: Option[ExternalReference],
    updatedBy: Option[ExternalReference]
)
