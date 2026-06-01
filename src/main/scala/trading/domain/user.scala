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

  enum Tier(val rank: Int) derives CanEqual {
    case Bronze extends Tier(0)
    case Silver extends Tier(1)
    case Gold extends Tier(2)
    case Platinum extends Tier(3)
    case Diamond extends Tier(4)
  }

  type DisplayName = DisplayName.T
  object DisplayName extends RefinedType[String, Not[Empty] & MaxLength[80]]

  type CountryIso = CountryIso.T
  object CountryIso extends RefinedType[String, Match["^[A-Z]{2}$"]]

  final case class User(
      id: UserId,
      email: Email,
      displayName: DisplayName,
      role: Role,
      tier: Tier,
      kyc: KycStatus,
      externalSubject: String,
      countryIso: CountryIso,
      createdAt: Instant,
      updatedAt: Instant
  ) derives CanEqual

  final case class Principal(
      user: User,
      scopes: Set[String]
  ) derives CanEqual {
    def hasRole(r: Role): Boolean = user.role == r
    def isAdmin: Boolean = user.role == Role.Admin || user.role == Role.SuperAdmin
  }

}
