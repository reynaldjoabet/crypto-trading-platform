package trading.app

import java.util.UUID

import cats.effect.std.UUIDGen
import cats.effect.Sync

import com.fasterxml.uuid.impl.TimeBasedEpochGenerator
import com.fasterxml.uuid.Generators

/**
  * UUIDGen backed by java-uuid-generator's version 7 (Unix-epoch, time-ordered) generator. IDs sort
  * by creation time, which keeps B-tree primary-key inserts at the right edge of the index instead
  * of scattered like random v4 IDs. The generator is thread-safe and monotonic within a
  * millisecond.
  */
object TimeOrderedUUIDGen {

  def apply[F[_]: Sync]: UUIDGen[F] = {
    val generator: TimeBasedEpochGenerator = Generators.timeBasedEpochGenerator()
    new UUIDGen[F] {
      def randomUUID: F[UUID] = Sync[F].delay(generator.generate())
    }
  }

}
