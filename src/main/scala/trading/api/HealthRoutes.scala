package trading.api

import cats.effect.*
import cats.syntax.all.*
import io.circe.Json
import org.http4s.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.Http4sDsl

/** Unauthenticated operational probes, mounted at the root of every server.
  *
  *   - `GET /health` — liveness: the process is up and serving. Cheap, no dependencies.
  *   - `GET /ready` — readiness: dependencies (Postgres) are reachable. Returns 503 when not, so load balancers / k8s
  *     pull the instance out of rotation instead of routing failing traffic.
  */
object HealthRoutes {
  // private given CanEqual[Method, Method] = CanEqual.derived
  // private given CanEqual[Uri.Path, Uri.Path] = CanEqual.derived

  def routes[F[_]: Concurrent](dbPing: F[Unit]): HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl.*

    HttpRoutes.of[F] {
      case GET -> Root / "health" =>
        Ok(Json.obj("status" -> Json.fromString("ok")))

      case GET -> Root / "ready" =>
        dbPing.attempt.flatMap {
          case Right(_) => Ok(Json.obj("status" -> Json.fromString("ready")))
          case Left(e)  =>
            ServiceUnavailable(
              Json.obj(
                "status" -> Json.fromString("unready"),
                "error" -> Json.fromString(Option(e.getMessage).getOrElse("dependency check failed"))
              )
            )
        }
    }
  }

}
