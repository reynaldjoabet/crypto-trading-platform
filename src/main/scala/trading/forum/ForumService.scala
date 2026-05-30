package trading.forum

import cats.effect.*
import trading.domain.ids.{PostId, UserId}

import java.time.Instant

final case class ForumPost(
    id: PostId,
    userId: UserId,
    parentId: Option[PostId],
    title: Option[String],
    body: String,
    isHidden: Boolean,
    createdAt: Instant
)

trait ForumService[F[_]] {
  def list(parent: Option[PostId], limit: Int): F[List[ForumPost]]
  def post(userId: UserId, parentId: Option[PostId], title: Option[String], body: String): F[ForumPost]
  def hide(postId: PostId): F[Unit]
}

object ForumService {
  def stub[F[_]: Sync]: ForumService[F] = {
    new ForumService[F] {
      def list(parent: Option[PostId], limit: Int): F[List[ForumPost]] = {
        Sync[F].pure(List.empty)
      }
      def post(userId: UserId, parentId: Option[PostId], title: Option[String], body: String): F[ForumPost] = {
        Sync[F].raiseError(NotImplementedError("forum post not implemented"))
      }
      def hide(postId: PostId): F[Unit] = {
        Sync[F].unit
      }
    }
  }
}
