package trading.strategies

import cats.effect.*
import cats.syntax.all.*
import trading.domain.AppError
import trading.domain.ids.StrategyId
import trading.domain.strategy.*
import trading.persistence.StrategyRepo

trait StrategyService[F[_]] {
  def listPublished: F[List[Strategy]]
  def get(id: StrategyId): F[Strategy]
  def publish(id: StrategyId): F[Strategy]
  def upsert(s: Strategy): F[Unit]
  def listComponents: F[List[StrategyComponent]]
  def upsertComponent(c: StrategyComponent): F[Unit]
}

object StrategyService {

  def make[F[_]: Sync](repo: StrategyRepo[F]): StrategyService[F] = {
    new StrategyService[F] {
      def listPublished: F[List[Strategy]] = {
        repo.listPublished
      }
      def get(id: StrategyId): F[Strategy] = {
        repo.find(id).flatMap {
          case Some(s) => s.pure[F]
          case None    => Sync[F].raiseError(AppError.NotFound("strategy", id.value.toString))
        }
      }
      def publish(id: StrategyId): F[Strategy] = {
        for {
          s <- get(id)
          now <- Clock[F].realTimeInstant
          out = s.copy(isPublished = true, updatedAt = now)
          _ <- repo.upsert(out)
        } yield out
      }
      def upsert(s: Strategy): F[Unit] = {
        repo.upsert(s)
      }
      def listComponents: F[List[StrategyComponent]] = {
        repo.listComponents
      }
      def upsertComponent(c: StrategyComponent): F[Unit] = {
        repo.upsertComponent(c)
      }
    }
  }

}
