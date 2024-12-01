package evo.derivation.logstage

import izumi.logstage.api.rendering.{LogstageCodec, LogstageWriter}

trait EvoLogInstances:
    given [A](using e: EvoLog[A]): LogstageCodec[A] =
        (writer: LogstageWriter, value: A) => e.write(writer, value)

object instances extends EvoLogInstances
