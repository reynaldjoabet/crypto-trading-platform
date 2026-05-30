package trading.domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import trading.domain.addresses.Email
import trading.domain.ids.*

import java.time.Instant

object user {

  enum Role derives CanEqual {
    case SuperAdmin, Admin, Client
  }

  enum KycStatus derives CanEqual {
    case NotStarted, Pending, Approved, Rejected
  }

  /** Tiered user level — drives fee tier and feature access. */
  enum Tier(val rank: Int) derives CanEqual {
    case Bronze extends Tier(0)
    case Silver extends Tier(1)
    case Gold extends Tier(2)
    case Platinum extends Tier(3)
    case Diamond extends Tier(4)
  }

  final case class User(
      id: UserId,
      email: Email,
      displayName: String :| (Not[Empty] & MaxLength[80]),
      role: Role,
      tier: Tier,
      kyc: KycStatus,
      externalSubject: String, // OIDC sub
      countryIso: String :| Match["^[A-Z]{2}$"],
      createdAt: Instant,
      updatedAt: Instant
  ) derives CanEqual

  /** Authenticated principal, parsed from JWT and pinned to a DB user. */
  final case class Principal(
      user: User,
      scopes: Set[String]
  ) derives CanEqual {
    def hasRole(r: Role): Boolean = user.role == r
    def isAdmin: Boolean = user.role == Role.Admin || user.role == Role.SuperAdmin
  }

}
