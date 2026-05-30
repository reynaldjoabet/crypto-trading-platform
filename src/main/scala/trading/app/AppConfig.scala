package trading.app

import cats.effect.kernel.Async
import cats.effect.Sync
//import cats.syntax.all.*
import pureconfig.*
import scala.reflect.ClassTag
import trading.auth.AuthConfig
import trading.custody.FireblocksConfig
import trading.exchanges.{BinanceConfig, KrakenConfig}
import trading.persistence.DbConfig
import org.http4s.Uri
import cats.data.EitherT
import pureconfig.error.{ConfigReaderException, CannotConvert}
import org.http4s.ParseResult

// Scala 3 extension syntax for pureconfig
extension (cs: ConfigSource) {
  def loadF[F[_], A](using F: Sync[F], reader: ConfigReader[A], ct: ClassTag[A]): F[A] = {
    AppConfig.loadF(cs)
  }
}

// Custom ConfigReaders for http4s types
// given ConfigReader[Uri] =
//   ConfigReader.fromString[Uri](s => Uri.fromString(s).left.map(e => new CannotConvert(e.toString(),"","")))

final case class HttpConfig(host: String, port: Int) derives ConfigReader

final case class AppConfig(
    publicHttp: HttpConfig,
    adminHttp: HttpConfig,
    db: DbConfig,
    auth: AuthConfig,
    kraken: KrakenConfig,
    binance: BinanceConfig,
    fireblocks: FireblocksConfig,
    enableLiveExchanges: Boolean
) derives ConfigReader

object AppConfig {
  private def mkConfigReader[A](f: String => ParseResult[A])(implicit ct: ClassTag[A]): ConfigReader[A] = {
    ConfigReader.fromString { str =>
      val className = ct.runtimeClass.getSimpleName()
      f(str).fold(
        err => Left(CannotConvert(str, className, err.sanitized)),
        value => Right(value)
      )
    }
  }

  implicit val uriReader: ConfigReader[Uri] = {
    mkConfigReader[Uri](Uri.fromString)
  }

  implicit val uriWriter: ConfigWriter[Uri] = {
    ConfigWriter[String].contramap(_.renderString)
  }

  implicit val uriSchemeReader: ConfigReader[Uri.Scheme] = {
    mkConfigReader[Uri.Scheme](Uri.Scheme.fromString)
  }

  implicit val uriSchemeWriter: ConfigWriter[Uri.Scheme] = {
    ConfigWriter[String].contramap(_.value)
  }

  implicit val uriPathReader: ConfigReader[Uri.Path] = {
    ConfigReader.stringConfigReader.map(Uri.Path.unsafeFromString) // .fromString is deprecated
  }

  implicit val uriPathWriter: ConfigWriter[Uri.Path] = {
    ConfigWriter[String].contramap(_.renderString)
  }

  implicit val uriHostReader: ConfigReader[Uri.Host] = {
    mkConfigReader[Uri.Host](Uri.Host.fromString)
  }

  implicit val uriHostWriter: ConfigWriter[Uri.Host] = {
    ConfigWriter[String].contramap(_.renderString)
  }

  implicit val uriIpv4AddressReader: ConfigReader[Uri.Ipv4Address] = {
    mkConfigReader[Uri.Ipv4Address](Uri.Ipv4Address.fromString)
  }

  implicit val uriIpv4AddressWriter: ConfigWriter[Uri.Ipv4Address] = {
    ConfigWriter[String].contramap(_.renderString)
  }

  def load[F[_]: Async]: F[AppConfig] = {
    Async[F].blocking {
      ConfigSource.default.loadOrThrow[AppConfig]
    }
  }

  /** Load a configuration of type `A` from a config source
    *
    * @param cs
    *   the config source from where the configuration will be loaded
    * @return
    *   The returned action will complete with `A` if it is possible to create an instance of type `A` from the
    *   configuration source, or fail with a ConfigReaderException which in turn contains details on why it isn't
    *   possible
    */
  def loadF[F[_], A](
      cs: ConfigSource
  )(implicit F: Sync[F], reader: ConfigReader[A], ct: ClassTag[A]): F[A] = {
    EitherT(F.blocking(cs.cursor()))
      .subflatMap(reader.from)
      .leftMap(ConfigReaderException[A])
      .rethrowT
  }

  /** Load a configuration of type `A` from the standard configuration files
    *
    * @return
    *   The returned action will complete with `A` if it is possible to create an instance of type `A` from the
    *   configuration files, or fail with a ConfigReaderException which in turn contains details on why it isn't
    *   possible
    */
  def loadConfigF[F[_], A](implicit F: Sync[F], reader: ConfigReader[A], ct: ClassTag[A]): F[A] = {
    loadF(ConfigSource.default)
  }
}
