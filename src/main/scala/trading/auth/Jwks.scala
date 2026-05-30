package trading.auth

import cats.effect.*
import cats.syntax.all.*
import io.circe.parser.parse as circeParse
import org.http4s.*
import org.http4s.client.Client
import org.typelevel.log4cats.Logger

import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import scala.concurrent.duration.*

final case class Jwk(kid: String, kty: String, n: String, e: String, alg: Option[String], use: Option[String])

/** A tiny JWKS client. Fetches from the discovered jwks_uri, caches keys for `ttl`, looks them up by `kid`.
  */
trait JwkProvider[F[_]] {
  def keyFor(kid: String): F[Option[RSAPublicKey]]
}

object JwkProvider {
  final case class State(keys: Map[String, RSAPublicKey], fetchedAt: Long)

  def http[F[_]: Async: Logger](
      client: Client[F],
      jwksUri: Uri,
      ttl: FiniteDuration = 10.minutes
  ): F[JwkProvider[F]] = {
    Ref.of[F, Option[State]](None).map { ref =>
      new JwkProvider[F] {
        def keyFor(kid: String): F[Option[RSAPublicKey]] = {
          for {
            now <- Clock[F].realTime.map(_.toMillis)
            cached <- ref.get
            fresh <- cached match {
              case Some(s) if (now - s.fetchedAt) < ttl.toMillis => s.pure[F]
              case _                                             => fetch(now)
            }
          } yield fresh.keys.get(kid)
        }

        private def fetch(now: Long): F[State] = {
          for {
            _ <- Logger[F].info(s"Fetching JWKS from $jwksUri")
            body <- client.expect[String](jwksUri)
            json <- Async[F].fromEither(circeParse(body))
            ks <- Async[F].fromEither(parseJwks(json))
            st = State(ks.map(k => k.kid -> rsaKey(k)).toMap, now)
            _ <- ref.set(Some(st))
          } yield st
        }
      }
    }
  }

  private def parseJwks(j: io.circe.Json): Either[Throwable, List[Jwk]] = {
    val arrayE = j.hcursor.downField("keys").as[List[io.circe.Json]]
    arrayE.left.map(identity[Throwable]).flatMap { arr =>
      arr
        .traverse { obj =>
          val c = obj.hcursor
          for {
            kid <- c.get[String]("kid")
            kty <- c.get[String]("kty")
            n <- c.get[String]("n")
            e <- c.get[String]("e")
            alg = c.get[String]("alg").toOption
            use = c.get[String]("use").toOption
          } yield Jwk(kid, kty, n, e, alg, use)
        }
        .left
        .map(identity[Throwable])
    }
  }

  private def rsaKey(jwk: Jwk): RSAPublicKey = {
    val dec = Base64.getUrlDecoder
    val n = BigInteger(1, dec.decode(jwk.n))
    val e = BigInteger(1, dec.decode(jwk.e))
    val spec = RSAPublicKeySpec(n, e)
    KeyFactory.getInstance("RSA").generatePublic(spec).asInstanceOf[RSAPublicKey]
  }
}
