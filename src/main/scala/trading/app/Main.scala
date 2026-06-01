package trading.app

import cats.effect.*
import cats.effect.std.UUIDGen
//import cats.effect.Network
import cats.syntax.all.*
import com.comcast.ip4s.{Host, Port, host, port}
import trading.accounts.AccountService
import trading.api.admin.AdminRoutes
import trading.api.client.ClientRoutes
import trading.auth.{Auth, JwkProvider, JwtClaims}
import trading.custody.Custody
import trading.domain.AppError
import trading.domain.user.{Principal, Role, Tier, KycStatus, User, DisplayName, CountryIso}
import trading.domain.ids.UserId
import trading.domain.addresses.Email
import trading.exchanges.{BinanceClient, ExchangeClient, KrakenClient, MockExchange}
import trading.instruments.InstrumentService
import trading.orders.OrderService
import trading.persistence.*
import trading.strategies.StrategyService
import io.github.iltotore.iron.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.{ErrorAction, ErrorHandling, Logger as HttpLogger}
import org.http4s.server.Router
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.{Logger, SelfAwareStructuredLogger}
import cats.effect.std.{Console, SecureRandom}
import fs2.io.net.Network

object Main extends IOApp.Simple {
  val logger = Slf4jLogger.getLogger[IO]
  given Network[IO] = Network.forAsync
  def run: IO[Unit] = program[IO]

  def program[F[_]: Async: Console: SecureRandom: Network]: F[Unit] = {
    given SelfAwareStructuredLogger[F] = Slf4jLogger.getLogger[F]
    given UUIDGen[F] = UUIDGen.fromSecureRandom[F]
    Resources.allocate[F].use(_ => Async[F].never)
  }

  /** All resources wired up here. Result is the running pair of HTTP servers. */
  object Resources {

    def allocate[F[_]: Async: Logger: UUIDGen: cats.effect.std.Console: Network]: Resource[F, Unit] = {
      for {
        cfg <- Resource.eval(AppConfig.load[F])
        _ <- Resource.eval(
          Logger[F].info(
            s"Booting trading-platform — public=${cfg.publicHttp.port} admin=${cfg.adminHttp.port}"
          )
        )
        _ <- Resource.eval(Database.migrate[F](cfg.db))
        pool <- Database.pool[F](cfg.db)
        httpCli <- EmberClientBuilder.default[F].build

        // repositories
        instRepo = InstrumentRepo.fromSession(pool)
        orderRepo = OrderRepo.fromSession(pool)
        acctRepo = AccountRepo.fromSession(pool)
        userRepo = UserRepo.fromSession(pool)
        stratRepo = StrategyRepo.fromSession(pool)

        // services
        instSvc = InstrumentService.make(instRepo)
        acctSvc = AccountService.make(acctRepo)
        stratSvc = StrategyService.make(stratRepo)

        // exchanges
        mock <- Resource.eval(MockExchange.make[F])
        kraken = if cfg.enableLiveExchanges then KrakenClient.make(httpCli, cfg.kraken) else mock
        binance = if cfg.enableLiveExchanges then BinanceClient.make(httpCli, cfg.binance) else mock
        selectEx = ExchangeClient.select[F](kraken, binance, ftx = None, mock)
        custody = Custody.fireblocks(httpCli, cfg.fireblocks)
        _ = custody // wired for routes to reach later

        orderSvc = OrderService.make[F](orderRepo, instSvc, acctSvc, selectEx)

        // auth
        jwks <- Resource.eval(JwkProvider.http(httpCli, cfg.auth.jwksUri))
        lookup = principalFromClaims[F](userRepo)
        authMw = Auth.middleware[F](cfg.auth, jwks, lookup)

        // routes
        adminR = AdminRoutes.routes(authMw, instSvc, stratSvc)
        clientR = ClientRoutes.routes(authMw, orderSvc, instSvc, stratSvc, acctSvc)

        publicApp = withMiddleware(Router("/api/v1" -> clientR).orNotFound)
        adminApp = withMiddleware(Router("/admin/v1" -> adminR).orNotFound)

        _ <- EmberServerBuilder
          .default[F]
          .withHost(Host.fromString(cfg.publicHttp.host).getOrElse(host"0.0.0.0"))
          .withPort(Port.fromInt(cfg.publicHttp.port).getOrElse(port"8080"))
          .withHttpApp(publicApp)
          .build
        _ <- EmberServerBuilder
          .default[F]
          .withHost(Host.fromString(cfg.adminHttp.host).getOrElse(host"0.0.0.0"))
          .withPort(Port.fromInt(cfg.adminHttp.port).getOrElse(port"8081"))
          .withHttpApp(adminApp)
          .build
        _ <- Resource.eval(Logger[F].info("Trading platform up."))
      } yield ()
    }

    private def withMiddleware[F[_]: Async: Logger](app: org.http4s.HttpApp[F]): org.http4s.HttpApp[F] = {
      val withLog = HttpLogger.httpApp(logHeaders = false, logBody = false)(app)
      ErrorHandling.Recover.total(
        ErrorAction.log(
          withLog,
          messageFailureLogAction = (t, msg) => Logger[F].warn(t)(msg),
          serviceErrorLogAction = (t, msg) => Logger[F].error(t)(msg)
        )
      )
    }

    /** Look up a DB user from JWT claims. Auto-provision on first sight (Client role + KYC NotStarted) so the first
      * request after login succeeds; tighten this in production.
      */
    private def principalFromClaims[F[_]: Sync: UUIDGen](
        repo: UserRepo[F]
    ): JwtClaims => F[Either[AppError, Principal]] = { claims =>
      {
        repo.findBySubject(claims.sub).flatMap {
          case Some(u) => Sync[F].pure(Right(Principal(u, claims.scopes)))
          case None    => {
            for {
              uid <- UUIDGen[F].randomUUID
              now <- Clock[F].realTimeInstant
              email = claims.email
                .flatMap(Email.option(_))
                .getOrElse(Email.applyUnsafe(s"unknown-${uid.toString.take(8)}@local"))
              name = claims.name.getOrElse("Anonymous")
              displayName = DisplayName.option(name).getOrElse(DisplayName.applyUnsafe("User"))
              role = {
                if claims.roles.contains("super-admin") then Role.SuperAdmin
                else if claims.roles.contains("admin") then Role.Admin
                else Role.Client
              }
              user = User(
                id = UserId(uid),
                email = email,
                displayName = displayName,
                role = role,
                tier = Tier.Bronze,
                kyc = KycStatus.NotStarted,
                externalSubject = claims.sub,
                countryIso = CountryIso.applyUnsafe("US"),
                createdAt = now,
                updatedAt = now
              )
              _ <- repo.upsert(user)
            } yield Right(Principal(user, claims.scopes)): Either[AppError, Principal]
          }
        }
      }
    }

  }

}
