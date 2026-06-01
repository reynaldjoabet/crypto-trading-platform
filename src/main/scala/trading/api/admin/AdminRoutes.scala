package trading.api.admin

import cats.effect.*
import cats.syntax.all.*
import trading.auth.Auth
import trading.domain.AppError
import trading.domain.ids.*
import trading.domain.user.{Principal, Role}
import trading.instruments.InstrumentService
import trading.strategies.StrategyService
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder
import org.http4s.dsl.Http4sDsl
import org.http4s.server.AuthMiddleware
import io.circe.syntax.*
import org.typelevel.log4cats.Logger

object AdminRoutes {

  // private given CanEqual[Method, Method] = CanEqual.derived
  // private given CanEqual[Uri.Path, Uri.Path] = CanEqual.derived

  def routes[F[_]: Async: Logger](
      authMw: AuthMiddleware[F, Principal],
      instruments: InstrumentService[F],
      strategies: StrategyService[F]
  ): HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl.*
    import JsonCodecs.given

    def problemResponse(t: Throwable): F[Response[F]] = {
      t match {
        case e: AppError.Validation        => BadRequest(e.asJson)
        case e: AppError.NotFound          => NotFound(e.asJson)
        case e: AppError.Conflict          => Conflict(e.asJson)
        case e: AppError.Forbidden         => Forbidden(e.asJson)
        case e: AppError.Unauthenticated   => Forbidden(e.asJson)
        case e: AppError.KycRequired       => Forbidden(e.asJson)
        case e: AppError.InsufficientFunds => BadRequest(e.asJson)
        case e: AppError.Upstream          => BadGateway(e.asJson)
        case e: AppError                   => InternalServerError(e.asJson)
        case other                         => {
          Logger[F].error(other)("unhandled error") *>
            InternalServerError(AppError.Internal(Option(other.getMessage).getOrElse("unknown")).asJson)
        }
      }
    }

    val authed: AuthedRoutes[Principal, F] = AuthedRoutes.of {

      // ── instruments ──
      case GET -> Root / "instruments" as p => {
        Auth.requireRole(p, Role.Admin).attempt.flatMap {
          case Left(e: AppError) => Forbidden(e.asJson)
          case _                 => instruments.list(activeOnly = false).flatMap(xs => Ok(xs.asJson))
        }
      }

      case authReq @ POST -> Root / "instruments" as p => {
        (for {
          _ <- Auth.requireRole(p, Role.Admin)
          req <- authReq.req.as[JsonCodecs.CreateInstrumentRequest]
          out <- instruments.create(req.symbol, req.base, req.quote, req.venue)
        } yield out).attempt.flatMap {
          case Right(i) => Created(i.asJson)
          case Left(e)  => problemResponse(e)
        }
      }

      case PATCH -> Root / "instruments" / UUIDVar(id) / "active" / boolStr as p => {
        val active = boolStr.equalsIgnoreCase("true")
        (Auth.requireRole(p, Role.Admin) *>
          instruments.setActive(InstrumentId(id), active)).attempt.flatMap {
          case Right(_) => NoContent()
          case Left(e)  => problemResponse(e)
        }
      }

      // ── strategies ──
      case GET -> Root / "strategies" as p => {
        Auth.requireRole(p, Role.Admin) *>
          strategies.listPublished.flatMap(xs => Ok(xs.map(s => s.id.asJson).asJson))
      }

      case POST -> Root / "strategies" / UUIDVar(id) / "publish" as p => {
        Auth.requireRole(p, Role.SuperAdmin).attempt.flatMap {
          case Left(e: AppError) => Forbidden(e.asJson)
          case _                 => {
            strategies.publish(StrategyId(id)).attempt.flatMap {
              case Right(s) => Ok(s.id.asJson)
              case Left(e)  => problemResponse(e)
            }
          }
        }
      }
    }

    authMw(authed)
  }

}
