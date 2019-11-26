package io.odin.loggers

import java.io.BufferedWriter
import java.nio.file.{Files, Paths}

import cats.syntax.all._
import cats.effect.syntax.all._
import cats.instances.list._
import cats.effect.{Clock, Resource, Sync}
import io.odin.formatter.Formatter
import io.odin.{Logger, LoggerMessage}

/**
  * Write to given log writer with provided formatter
  */
case class FileLogger[F[_]: Clock](buffer: BufferedWriter, formatter: Formatter)(implicit F: Sync[F])
    extends DefaultLogger[F] {
  def log(msg: LoggerMessage): F[Unit] =
    write(msg, formatter).guarantee(flush)

  override def log(msgs: List[LoggerMessage]): F[Unit] =
    msgs.traverse(write(_, formatter)).void.guarantee(flush)

  private def write(msg: LoggerMessage, formatter: Formatter): F[Unit] =
    F.delay {
      buffer.write(formatter.format(msg) + System.lineSeparator())
    }

  private def flush: F[Unit] = F.delay(buffer.flush()).handleErrorWith(_ => F.unit)
}

object FileLogger {
  def apply[F[_]: Clock](fileName: String, formatter: Formatter)(implicit F: Sync[F]): Resource[F, Logger[F]] = {
    def mkBuffer: F[BufferedWriter] = F.delay(Files.newBufferedWriter(Paths.get(fileName)))
    def closeBuffer(buffer: BufferedWriter): F[Unit] = F.delay {
      buffer.close()
    }

    Resource.make(mkBuffer)(closeBuffer).map { buffer =>
      FileLogger(buffer, formatter)
    }
  }
}
