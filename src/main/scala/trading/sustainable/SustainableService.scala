package trading.sustainable

import cats.effect.*
import cats.effect.std.UUIDGen
import trading.domain.AppError
import trading.domain.ids.DonationId
import trading.domain.ids.ProjectId
import trading.domain.ids.UserId
import trading.domain.money.Amount
import trading.domain.money.CurrencyCode

import java.time.Instant

final case class SustainableProject(
    id: ProjectId,
    name: String,
    description: String,
    targetAmount: Amount,
    currency: CurrencyCode,
    raisedAmount: Amount,
    isActive: Boolean,
    createdAt: Instant
)

final case class Donation(
    id: DonationId,
    userId: UserId,
    projectId: ProjectId,
    amount: Amount,
    currency: CurrencyCode,
    createdAt: Instant
)

trait SustainableService[F[_]] {
  def listProjects: F[List[SustainableProject]]
  def donate(userId: UserId, projectId: ProjectId, amount: Amount, ccy: CurrencyCode): F[Donation]
}

object SustainableService {
  def stub[F[_]: Sync: UUIDGen]: SustainableService[F] = {
    new SustainableService[F] {
      def listProjects: F[List[SustainableProject]] = {
        Sync[F].pure(List.empty)
      }
      def donate(userId: UserId, projectId: ProjectId, amount: Amount, ccy: CurrencyCode): F[Donation] = {
        Sync[F].raiseError(AppError.Internal("donation flow not implemented"))
      }
    }
  }
}
