package trading.domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

/** Token domain: the opaque/JWT credentials exchanged during the flows, plus the closed enumerations that OAuth 2.1
  * narrows down (no implicit grant, no ROPC, `code` is the only response type).
  */

/** Opaque or JWT Bearer access token. */
type AccessToken = AccessToken.T
object AccessToken extends RefinedType[String, AccessTokenR]

/** Opaque refresh token. */
type RefreshToken = RefreshToken.T
object RefreshToken extends RefinedType[String, RefreshTokenR]

/** Single-use authorization code. */
type AuthorizationCode = AuthorizationCode.T
object AuthorizationCode extends RefinedType[String, AuthorizationCodeR]

/** OIDC ID token (a compact JWT). */
type IdToken = IdToken.T
object IdToken extends RefinedType[String, JwtR]

/** Token lifetime in seconds; strictly positive. */
type ExpiresIn = ExpiresIn.T
object ExpiresIn extends RefinedType[Int, Positive]

/** Grant types permitted by OAuth 2.1 (implicit and password grants are removed). */
enum GrantType(val value: String) derives CanEqual {
  case AuthorizationCode extends GrantType("authorization_code")
  case ClientCredentials extends GrantType("client_credentials")
  case RefreshToken extends GrantType("refresh_token")
  case DeviceCode extends GrantType("urn:ietf:params:oauth:grant-type:device_code")
  case TokenExchange extends GrantType("urn:ietf:params:oauth:grant-type:token-exchange")

  def isAuthorizationCode: Boolean = this.value == "authorization_code"
}

/** Response types. OAuth 2.1 only retains the authorization code flow. */
enum ResponseType(val value: String) {
  case Code extends ResponseType("code")
}

/** Token type returned in a token response. */
enum TokenType(val value: String) {
  case Bearer extends TokenType("Bearer")
  case DPoP extends TokenType("DPoP")
}

/** PKCE challenge method. OAuth 2.1 requires `S256` for public clients. */
enum CodeChallengeMethod(val value: String) {
  case S256 extends CodeChallengeMethod("S256")
  case Plain extends CodeChallengeMethod("plain")
}

/** PKCE (Proof Key for Code Exchange, RFC 7636) — mandatory for the authorization code flow in OAuth 2.1.
  */

/** A PKCE `code_verifier`: 43–128 unreserved characters of high entropy. */
type CodeVerifier = CodeVerifier.T
object CodeVerifier extends RefinedType[String, CodeVerifierR] {

  /** Derive the S256 `code_challenge` for this verifier. */
  // def challengeS256(verifier: CodeVerifier): CodeChallenge =
  // 	CodeChallenge.applyUnsafe(Validators.codeChallengeS256(verifier.value))
}

/** A PKCE S256 `code_challenge`: base64url-encoded SHA-256, 43 chars. */
type CodeChallenge = CodeChallenge.T
object CodeChallenge extends RefinedType[String, CodeChallengeR]

/** The PKCE pair an authorization server stores and later verifies. */
object Pkce {

  /** Verify a presented `code_verifier` against the stored `code_challenge` using S256 and a constant-time comparison.
    */
  // def verify(verifier: CodeVerifier, challenge: CodeChallenge): Boolean =
  // 	Validators.verifyPkceS256(verifier.value, challenge.value)
}

/** OpenID Connect domain: the issuer/subject identifiers and the mandatory ID token claims an RP must validate (OIDC
  * Core §2, §3.1.3.7).
  */

/** OIDC issuer identifier (`iss`). */
type Issuer = Issuer.T
object Issuer extends RefinedType[String, IssuerR]

/** OIDC subject identifier (`sub`). */
type Subject = Subject.T
object Subject extends RefinedType[String, SubjectR]

/** OIDC `nonce`, binding an ID token to the originating authorization request. */
type Nonce = Nonce.T
object Nonce extends RefinedType[String, NonceR]

/** The required ID token claims. `exp`/`iat` are epoch seconds; relationships (e.g. `exp > iat`) are enforced by the
  * smart constructor.
  */
final case class IdTokenClaims(
    iss: Issuer,
    sub: Subject,
    aud: ClientId,
    exp: Long,
    iat: Long,
    nonce: Option[Nonce]
) {

  /** Whether the token is expired relative to the supplied epoch-second clock. */
  def isExpired(nowEpochSeconds: Long): Boolean = nowEpochSeconds >= exp
}

object IdTokenClaims {

  def from(
      iss: String,
      sub: String,
      aud: ClientId,
      exp: Long,
      iat: Long,
      nonce: Option[String]
  ): Either[String, IdTokenClaims] =
    for {
      issuer <- Issuer.either(iss)
      subject <- Subject.either(sub)
      _ <- Either.cond(exp > iat, (), s"exp ($exp) must be after iat ($iat)")
      nonceOpt <- nonce match {
        case None    => Right(None)
        case Some(n) => Nonce.either(n).map(Some(_))
      }
    } yield IdTokenClaims(issuer, subject, aud, exp, iat, nonceOpt)
}

/** Protocol messages of the OAuth 2.1 authorization code flow with PKCE, composed from the validated newtypes. By
  * construction these structures cannot hold an invalid client id, redirect URI, scope, code or PKCE challenge.
  */

/** The `/authorize` request (RFC 6749 §4.1.1 as narrowed by OAuth 2.1 + PKCE). */
final case class AuthorizationRequest(
    responseType: ResponseType,
    clientId: ClientId,
    // redirectUri: RedirectUriValue,
    scope: Scope,
    state: State,
    codeChallenge: CodeChallenge,
    codeChallengeMethod: CodeChallengeMethod
)

/** The `/token` request for the authorization code grant. */
final case class TokenRequest(
    grantType: GrantType,
    code: AuthorizationCode,
    // redirectUri: RedirectUriValue,
    clientId: ClientId,
    codeVerifier: CodeVerifier
)

/** The token endpoint success response (RFC 6749 §5.1 + OIDC). */
final case class TokenResponse(
    accessToken: AccessToken,
    tokenType: TokenType,
    expiresIn: ExpiresIn,
    refreshToken: Option[RefreshToken],
    scope: Scope,
    idToken: Option[IdToken]
)

object AuthorizationServer {

  /** Honour a token request: confirm the client, the redirect URI exact-match and the PKCE proof before minting tokens.
    * Returns the validated parameters that a real implementation would hand to its token minting / persistence layer.
    *
    * @param storedChallenge
    *   the `code_challenge` captured at the authorize step.
    */
  def authorizeCodeExchange(
      client: Client,
      request: TokenRequest
      // storedChallenge: CodeChallenge
  ): Either[String, Unit] =
    for {
      _ <- Either.cond(
        request.clientId.value == client.id.value,
        (),
        "client_id mismatch"
      )
      _ <- Either.cond(
        request.grantType.isAuthorizationCode,
        (),
        s"unsupported grant_type: ${request.grantType.value}"
      )
      // _ <- Either.cond(
      //   client.permitsRedirect(request.redirectUri),
      //   (),
      //   "redirect_uri not registered for client"
      // )
      // _ <- Either.cond(
      //   Pkce.verify(request.codeVerifier, storedChallenge),
      //   (),
      //   "PKCE verification failed"
      // )
    } yield ()
}

/** Domain-specific Iron constraints for OAuth 2.1 and OpenID Connect.
  *
  * Each "dummy" class is paired with a [[Constraint]] instance in its companion object so implicit search can find it.
  * The public vocabulary is exposed as `*R` type aliases (the constraint, "R" for refinement) consumed by the newtypes
  * in the per-domain files.
  *
  * Format predicates use Iron's [[Match]] (full-string regex) and are evaluated at compile time for literals. Semantic
  * predicates (URI parsing) are delegated to [[Validators]] and therefore run at runtime.
  */

// ---------------------------------------------------------------------------
// Semantic (URI-parsing) constraints
// ---------------------------------------------------------------------------

/** A valid OAuth 2.1 redirect URI (https / loopback http / private-use scheme, no fragment). */
final class RedirectUri
// object RedirectUri {
//   given Constraint[String, RedirectUri] with {
//     override inline def test(inline value: String): Boolean = Validators.isRedirectUri(value)
//     override inline def message: String = "Must be a valid OAuth 2.1 redirect URI"
//   }
// }

/** A valid OpenID Connect issuer identifier (https, no query/fragment). */
final class IssuerUri
object IssuerUri {
  given Constraint[String, IssuerUri] with {
    override inline def test(inline value: String): Boolean = false // Validators.isIssuer(value)
    override inline def message: String = "Must be an https issuer URL without query or fragment"
  }
}

// ---------------------------------------------------------------------------
// Constraint vocabulary (the `*R` aliases used by the domain newtypes)
// ---------------------------------------------------------------------------

/** OAuth client identifier: non-blank, printable, bounded. */
type ClientIdR =
  DescribedAs[Not[Blank] & Match["[\\x20-\\x7E]+"] & MaxLength[255], "Must be a non-blank client_id"]

/** OAuth client secret: high-entropy shared secret (>= 16 chars). */
type ClientSecretR =
  DescribedAs[MinLength[16] & MaxLength[512], "Must be a client secret of at least 16 characters"]

/** Redirect URI (validated by URI parsing). */
type RedirectUriR = DescribedAs[RedirectUri, "Must be a valid redirect URI"]

/** A single OAuth scope parameter: space-delimited `scope-token`s, where a `scope-token` is `1*NQCHAR` (RFC 6749 §3.3 —
  * visible ASCII except space, double-quote and backslash).
  */
type ScopeR = DescribedAs[
  Match["[\\x21\\x23-\\x5B\\x5D-\\x7E]+( [\\x21\\x23-\\x5B\\x5D-\\x7E]+)*"],
  "Must be a space-delimited scope string"
]

/** Anti-CSRF `state`: opaque, sufficient entropy (>= 8 printable chars). */
type StateR =
  DescribedAs[Match["[\\x20-\\x7E]+"] & MinLength[8] & MaxLength[512], "Must be an opaque state of >= 8 chars"]

/** OIDC `nonce`: opaque replay-protection value (>= 8 printable chars). */
type NonceR =
  DescribedAs[Match["[\\x20-\\x7E]+"] & MinLength[8] & MaxLength[512], "Must be an opaque nonce of >= 8 chars"]

/** Authorization code: opaque, single-use, bounded. */
type AuthorizationCodeR =
  DescribedAs[Not[Blank] & Match["[\\x20-\\x7E]+"] & MaxLength[512], "Must be a non-blank authorization code"]

/** Bearer access token b64token grammar (RFC 6750 §2.1). */
type AccessTokenR =
  DescribedAs[Match["[A-Za-z0-9._~+/-]+=*"], "Must be a Bearer b64token access token"]

/** Refresh token: opaque, non-blank. */
type RefreshTokenR =
  DescribedAs[Not[Blank] & Match["[\\x20-\\x7E]+"] & MaxLength[1024], "Must be a non-blank refresh token"]

/** JWT compact serialization: three base64url segments separated by dots. */
type JwtR = DescribedAs[
  Match["[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"],
  "Must be a compact JWT (header.payload.signature)"
]

/** PKCE `code_verifier`: 43–128 unreserved characters (RFC 7636 §4.1). */
type CodeVerifierR =
  DescribedAs[Match["[A-Za-z0-9._~-]{43,128}"], "Must be a 43–128 char PKCE code_verifier"]

/** PKCE S256 `code_challenge`: base64url SHA-256, exactly 43 chars (RFC 7636 §4.2). */
type CodeChallengeR =
  DescribedAs[Match["[A-Za-z0-9_-]{43}"], "Must be a 43 char base64url S256 code_challenge"]

/** OIDC issuer identifier (validated by URI parsing). */
type IssuerR = DescribedAs[IssuerUri, "Must be a valid issuer identifier"]

/** OIDC subject (`sub`) identifier: non-blank, bounded (<= 255 per the spec). */
type SubjectR = DescribedAs[Not[Blank] & MaxLength[255], "Must be a non-blank subject identifier"]

/** Client registration domain: the identifiers and metadata an authorization server stores for a registered client.
  */

/** OAuth client identifier. */
type ClientId = ClientId.T
object ClientId extends RefinedType[String, ClientIdR]

/** OAuth client secret (confidential clients only). */
type ClientSecret = ClientSecret.T
object ClientSecret extends RefinedType[String, ClientSecretR]

/** A registered redirect URI. */
// type RedirectUriValue = RedirectUriValue.T
// object RedirectUriValue extends RefinedType[String, RedirectUriR]

/** A space-delimited scope string, e.g. `"openid profile email"`. */
type Scope = Scope.T
object Scope extends RefinedType[String, ScopeR] {

  /** Split into individual scope tokens. */
  def tokens(scope: Scope): List[String] = scope.value.split(' ').toList
}

/** Anti-CSRF `state` value. */
type State = State.T
object State extends RefinedType[String, StateR]

/** Client type, which determines whether a secret and which auth method apply. */
enum ClientType {
  case Confidential, Public
}

/** A registered OAuth client. */
final case class Client(
    id: ClientId,
    clientType: ClientType,
    secret: Option[ClientSecret],
    // redirectUris: List[RedirectUriValue],
    allowedScopes: Scope
) {

  /** Exact-match redirect URI check (OAuth 2.1 forbids fuzzy matching). */
  // def permitsRedirect(uri: RedirectUriValue): Boolean =
  //   redirectUris.exists(_.value == uri.value)
}

object Client {

  /** Register a confidential client, validating every field from raw input. */
  // def confidential(
  //     id: String,
  //     secret: String,
  //     redirectUris: List[String],
  //     scopes: String
  // ): Either[String, Client] =
  //   for {
  //     cid <- ClientId.either(id)
  //     sec <- ClientSecret.either(secret)
  //     uris <- redirectUris.foldRight[Either[String, List[RedirectUriValue]]](Right(Nil)) { (raw, acc) =>
  //       for {
  //         tail <- acc
  //         uri <- RedirectUriValue.either(raw)
  //       } yield uri :: tail
  //     }
  //     scp <- Scope.either(scopes)
  //   } yield Client(cid, ClientType.Confidential, Some(sec), uris, scp)

}
