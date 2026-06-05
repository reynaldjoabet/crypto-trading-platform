package trading.api.client

import cats.effect.*
import cats.syntax.all.*
import io.circe.{Decoder, Json}
import io.circe.syntax.*
import trading.accounts.AccountService
import trading.api.admin.JsonCodecs
import trading.api.admin.JsonCodecs.given
import trading.domain.AppError
import trading.domain.ids.*
import trading.domain.money.{Amount, CurrencyCode, Price, Quantity}
import trading.domain.market.*
import trading.domain.user.Principal
import trading.instruments.InstrumentService
import trading.orders.{OrderService, PlaceOrderRequest}
import trading.strategies.StrategyService
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl
import org.http4s.server.AuthMiddleware
import org.typelevel.log4cats.Logger
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder
object ClientRoutes {

  private given CanEqual[Method, Method] = CanEqual.derived
  private given CanEqual[Uri.Path, Uri.Path] = CanEqual.derived

  final case class PlaceOrderBody(
      accountId: AccountId,
      instrumentId: InstrumentId,
      side: Side,
      orderType: OrderType,
      quantity: Quantity,
      limitPrice: Option[Price],
      stopPrice: Option[Price],
      timeInForce: TimeInForce,
      strategyId: Option[StrategyId]
  )

  given Decoder[PlaceOrderBody] = Decoder.instance(c => {
    for {
      a <- c.get[AccountId]("accountId")
      i <- c.get[InstrumentId]("instrumentId")
      s <- c.get[Side]("side")
      ot <- c.get[OrderType]("orderType")
      q <- c.get[Quantity]("quantity")
      lp <- c.get[Option[Price]]("limitPrice")
      sp <- c.get[Option[Price]]("stopPrice")
      tif <- c.get[TimeInForce]("timeInForce")
      st <- c.get[Option[StrategyId]]("strategyId")
    } yield PlaceOrderBody(a, i, s, ot, q, lp, sp, tif, st)
  })

  final case class DepositBody(currency: CurrencyCode, amount: Amount, reference: String)
  given Decoder[DepositBody] = Decoder.instance(c => {
    for {
      ccy <- c.get[CurrencyCode]("currency")
      amt <- c.get[Amount]("amount")
      ref <- c.get[String]("reference")
    } yield DepositBody(ccy, amt, ref)
  })

  def routes[F[_]: Async: Logger](
      authMw: AuthMiddleware[F, Principal],
      orders: OrderService[F],
      instruments: InstrumentService[F],
      strategies: StrategyService[F],
      accounts: AccountService[F]
  ): HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl.*

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

      case GET -> Root / "instruments" as _ => {
        instruments.list(activeOnly = true).flatMap(xs => Ok(xs.asJson))
      }

      case GET -> Root / "strategies" as _ => {
        strategies.listPublished.flatMap(xs => Ok(xs.map(_.id.asJson).asJson))
      }

      case GET -> Root / "balance" / currency as p => {
        CurrencyCode.either(currency) match {
          case Left(reason) => BadRequest(AppError.Validation("currency", reason).asJson)
          case Right(ccy)   => {
            accounts.balance(p.user.id, ccy).flatMap { b =>
              Ok(
                Json.obj(
                  "currency" -> ccy.asJson,
                  "available" -> b.available.asJson,
                  "pending" -> b.pending.asJson
                )
              )
            }
          }
        }
      }

      case authReq @ POST -> Root / "orders" as p => {
        val _ = p
        (for {
          body <- authReq.req.as[PlaceOrderBody]
          req = PlaceOrderRequest(
            body.accountId,
            body.instrumentId,
            body.side,
            body.orderType,
            body.quantity,
            body.limitPrice,
            body.stopPrice,
            body.timeInForce,
            body.strategyId
          )
          out <- orders.place(req)
        } yield out).attempt.flatMap {
          case Right(o) => {
            Accepted(
              Json.obj(
                "id" -> o.intent.id.asJson,
                "status" -> o.status.asJson,
                "venue" -> o.venue.asJson
              )
            )
          }
          case Left(e) => problemResponse(e)
        }
      }

      case GET -> Root / "orders" / "by-account" / UUIDVar(acct) as _ => {
        orders
          .list(AccountId(acct), 100)
          .flatMap(xs => Ok(xs.map(o => Json.obj("id" -> o.intent.id.asJson, "status" -> o.status.asJson)).asJson))
      }

      case DELETE -> Root / "orders" / UUIDVar(id) as _ => {
        orders.cancel(OrderId(id)).attempt.flatMap {
          case Right(_) => NoContent()
          case Left(e)  => problemResponse(e)
        }
      }

      case authReq @ POST -> Root / "deposit" as p => {
        (for {
          body <- authReq.req.as[DepositBody]
          _ <- accounts.deposit(p.user.id, body.currency, body.amount, body.reference)
        } yield ()).attempt.flatMap {
          case Right(_) => Accepted(Json.obj("status" -> "ok".asJson))
          case Left(e)  => problemResponse(e)
        }
      }
    }

    val _ = JsonCodecs // ensure object initialised
    authMw(authed)
  }

}
