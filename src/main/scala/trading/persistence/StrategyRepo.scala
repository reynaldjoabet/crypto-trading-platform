package trading.persistence

import cats.effect.*
import cats.syntax.all.*
import trading.domain.ids.{ComponentId, StrategyId}
import trading.domain.strategy.*
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*

trait StrategyRepo[F[_]] {
  def listPublished: F[List[Strategy]]
  def find(id: StrategyId): F[Option[Strategy]]
  def upsert(s: Strategy): F[Unit]
  def listComponents: F[List[StrategyComponent]]
  def upsertComponent(c: StrategyComponent): F[Unit]
}

object StrategyRepo {
  import Codecs.*

  private def hydrateStrategy(
      t: (
          StrategyId,
          String,
          trading.domain.market.Symbol,
          trading.domain.money.Percent,
          Int,
          trading.domain.money.FeeBps,
          trading.domain.money.FeeBps,
          RiskLevel,
          Boolean,
          java.time.Instant,
          java.time.Instant
      ),
      comps: List[ComponentId]
  ): Strategy = {
    val (id, name, sym, dd, lev, mf, pf, risk, pub, c, u) = t
    val rname = StrategyName.applyUnsafe(name)
    val rlev = MaxLeverage.applyUnsafe(lev)
    Strategy(id, rname, sym, comps, dd, rlev, mf, pf, risk, pub, c, u)
  }

  private val selectPublished: Query[
    Void,
    (
        StrategyId,
        String,
        trading.domain.market.Symbol,
        trading.domain.money.Percent,
        Int,
        trading.domain.money.FeeBps,
        trading.domain.money.FeeBps,
        RiskLevel,
        Boolean,
        java.time.Instant,
        java.time.Instant
    )
  ] = {
    sql"""SELECT id, name, symbol, max_drawdown, max_leverage,
                 management_fee_bps, performance_fee_bps, risk, is_published, created_at, updated_at
          FROM strategies WHERE is_published = true ORDER BY updated_at DESC""".query(strategyRow)
  }

  private val selectById: Query[
    StrategyId,
    (
        StrategyId,
        String,
        trading.domain.market.Symbol,
        trading.domain.money.Percent,
        Int,
        trading.domain.money.FeeBps,
        trading.domain.money.FeeBps,
        RiskLevel,
        Boolean,
        java.time.Instant,
        java.time.Instant
    )
  ] = {
    sql"""SELECT id, name, symbol, max_drawdown, max_leverage,
                 management_fee_bps, performance_fee_bps, risk, is_published, created_at, updated_at
          FROM strategies WHERE id = $strategyIdC""".query(strategyRow)
  }

  private val selectComponentIds: Query[StrategyId, ComponentId] = {
    sql"""SELECT component_id FROM strategy_component_links
          WHERE strategy_id = $strategyIdC ORDER BY position""".query(componentIdC)
  }

  private val upsertStrategy: Command[
    (
        StrategyId,
        String,
        trading.domain.market.Symbol,
        trading.domain.money.Percent,
        Int,
        trading.domain.money.FeeBps,
        trading.domain.money.FeeBps,
        RiskLevel,
        Boolean,
        java.time.Instant,
        java.time.Instant
    )
  ] = {
    sql"""INSERT INTO strategies
            (id, name, symbol, max_drawdown, max_leverage,
             management_fee_bps, performance_fee_bps, risk, is_published, created_at, updated_at)
          VALUES ($strategyRow)
          ON CONFLICT (id) DO UPDATE SET
            name = EXCLUDED.name, symbol = EXCLUDED.symbol,
            max_drawdown = EXCLUDED.max_drawdown,
            max_leverage = EXCLUDED.max_leverage,
            management_fee_bps = EXCLUDED.management_fee_bps,
            performance_fee_bps = EXCLUDED.performance_fee_bps,
            risk = EXCLUDED.risk, is_published = EXCLUDED.is_published,
            updated_at = EXCLUDED.updated_at""".command
  }

  private val deleteComponentLinks: Command[StrategyId] = {
    sql"DELETE FROM strategy_component_links WHERE strategy_id = $strategyIdC".command
  }

  private val insertComponentLink: Command[(StrategyId, ComponentId, Int)] = {
    sql"""INSERT INTO strategy_component_links (strategy_id, component_id, position)
          VALUES ($strategyIdC, $componentIdC, $int4)""".command
  }

  private val selectComponents: Query[Void, StrategyComponent] = {
    sql"""SELECT id, name, indicator, params::text, is_active, created_at
          FROM strategy_components ORDER BY name""".query(componentCodec)
  }

  private val upsertComponentCmd: Command[(ComponentId, String, Indicator, String, Boolean, java.time.Instant)] = {
    sql"""INSERT INTO strategy_components (id, name, indicator, params, is_active, created_at)
          VALUES ($componentIdC, $text, $indicatorC, ${text}::jsonb, $bool, $instantTz)
          ON CONFLICT (id) DO UPDATE SET
            name = EXCLUDED.name,
            indicator = EXCLUDED.indicator,
            params = EXCLUDED.params,
            is_active = EXCLUDED.is_active""".command
  }

  def fromSession[F[_]: Concurrent](pool: Resource[F, Session[F]]): StrategyRepo[F] = {
    new StrategyRepo[F] {
      def listPublished: F[List[Strategy]] = {
        pool.use { s =>
          for {
            rows <- s.execute(selectPublished)
            comps <- rows.traverse(r => s.prepare(selectComponentIds).flatMap(_.stream(r._1, 32).compile.toList))
          } yield rows.zip(comps).map(hydrateStrategy)
        }
      }

      def find(id: StrategyId): F[Option[Strategy]] = {
        pool.use { s =>
          s.prepare(selectById).flatMap(_.option(id)).flatMap {
            case None    => Option.empty[Strategy].pure[F]
            case Some(r) => {
              s.prepare(selectComponentIds)
                .flatMap(_.stream(id, 32).compile.toList)
                .map(cs => Some(hydrateStrategy(r, cs)))
            }
          }
        }
      }

      def upsert(strat: Strategy): F[Unit] = {
        pool.use { s =>
          s.transaction.use { _ =>
            val tup = (
              strat.id,
              strat.name.value,
              strat.symbol,
              strat.maxDrawdown,
              strat.maxLeverage.value,
              strat.managementFee,
              strat.performanceFee,
              strat.risk,
              strat.isPublished,
              strat.createdAt,
              strat.updatedAt
            )
            for {
              _ <- s.prepare(upsertStrategy).flatMap(_.execute(tup))
              _ <- s.prepare(deleteComponentLinks).flatMap(_.execute(strat.id))
              _ <- s.prepare(insertComponentLink).flatMap { pc =>
                strat.components.zipWithIndex.traverse_((cid, i) => pc.execute((strat.id, cid, i)))
              }
            } yield ()
          }
        }
      }

      def listComponents: F[List[StrategyComponent]] = {
        pool.use(_.execute(selectComponents))
      }

      def upsertComponent(c: StrategyComponent): F[Unit] = {
        pool.use { s =>
          val tup =
            (c.id, c.name.value, c.indicator, Codecs.serializeParams(c.params), c.isActive, c.createdAt)
          s.prepare(upsertComponentCmd).flatMap(_.execute(tup)).void
        }
      }
    }
  }
}
