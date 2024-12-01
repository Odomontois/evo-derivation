package evo.derivation.logstage

import evo.derivation.LazySummon.All
import evo.derivation.{LazySummon, ValueClass}
import evo.derivation.config.{Config, ForField}
import evo.derivation.internal.{Matching, tupleFromProduct}
import evo.derivation.template.{ConsistentTemplate, HomogenicTemplate, SummonHierarchy, Template}
import izumi.logstage.api.rendering.{LogstageCodec, LogstageWriter}
import logstage.LogstageCodec

import scala.deriving.Mirror
import scala.deriving.Mirror.SumOf

/** Unable to extend LogstageCodec here due to problems with derivations for contravariant type classes
  *
  * LogstageWriter api is too strict to support @Embed, @Discriminator annotations here without additional methods such
  * as `writeInner`
  */
trait EvoLog[A]:
    def write(writer: LogstageWriter, value: A): Unit
    def writeInner: Option[(LogstageWriter, A) => Unit]
end EvoLog

object EvoLog extends EvoLogTemplate:
    def fromLogstage[A](using codec: => LogstageCodec[A]): EvoLog[A] =
        new EvoLog[A]:
            override def writeInner: Option[(LogstageWriter, A) => Unit] = None
            override def write(writer: LogstageWriter, value: A): Unit   = codec.write(writer, value)

    given [A](using LogstageCodec[A]): EvoLog[A] = fromLogstage
end EvoLog

trait EvoLogTemplate extends HomogenicTemplate[EvoLog], SummonHierarchy:
    override def product[A](using mirror: Mirror.ProductOf[A])(
        all: LazySummon.All[OfField, mirror.MirroredElemTypes],
    )(using config: => Config[A], ev: A <:< Product): EvoLog[A] =
        lazy val infos = config.top.fields.map(_._2)

        new EvoLog[A]:
            override def write(writer: LogstageWriter, value: A): Unit =
                writer.openMap()
                writeInnerImpl(writer, value)
                writer.closeMap()

            override val writeInner: Option[(LogstageWriter, A) => Unit] =
                Some(writeInnerImpl)

            def writeInnerImpl(writer: LogstageWriter, value: A): Unit =
                val fields = tupleFromProduct(value)

                all.useForeach[Unit, ForField[_]](fields, infos) {
                    [X] =>
                        (info: ForField[_], a: X, codec: EvoLog[X]) =>
                            val maskOpt = info.annotations.collectFirst { case Masked(mask) => mask }
                            val writeField = (writer: LogstageWriter, a: X) =>
                                maskOpt.fold(codec.write(writer, a))(mask =>
                                    LogstageCodec[String].write(writer, mask)
                                )

                            val writeFieldInner =
                                codec.writeInner.map(f =>
                                    (writer: LogstageWriter, a: X) =>
                                        maskOpt.fold(f(writer, a))(mask =>
                                            LogstageCodec[String].write(writer, info.name)
                                            writer.mapElementSplitter()
                                            LogstageCodec[String].write(writer, mask)
                                        )
                                )

                            writeFieldInner.filter(_ => info.embed).fold {
                                writer.nextMapElementOpen()
                                LogstageCodec[String].write(writer, info.name)
                                writer.mapElementSplitter()
                                writeField(writer, a)
                                writer.nextMapElementClose()
                            }(_(writer, a))
                }
            end writeInnerImpl
        end new
    end product

    override def sum[A](using mirror: SumOf[A])(
        subs: All[EvoLog, mirror.MirroredElemTypes],
        mkSubMap: => Map[String, EvoLog[A]],
    )(using config: => Config[A], matching: Matching[A]): EvoLog[A] =
        lazy val cfg                            = config
        lazy val codecs: Map[String, EvoLog[A]] = mkSubMap

        new EvoLog[A]:
            override def write(writer: LogstageWriter, value: A): Unit =
                writer.openMap()
                writeInnerImpl(writer, value)
                writer.closeMap()

            override val writeInner: Option[(LogstageWriter, A) => Unit] =
                Some(writeInnerImpl)

            def writeInnerImpl(writer: LogstageWriter, value: A): Unit =
                val constructor  = matching.matched(value)
                val discrimValue = cfg.name(constructor)

                (codecs.get(constructor).map(c => (c, c.writeInner)), config.discriminator) match
                    case (Some((_, Some(writeInn))), Some(discr)) =>
                        writer.nextMapElementOpen()
                        LogstageCodec[String].write(writer, discr)
                        writer.mapElementSplitter()
                        LogstageCodec[String].write(writer, discrimValue)
                        writer.nextMapElementClose()
                        writeInn(writer, value)
                    case (Some(codec, _), _)                      =>
                        writer.nextMapElementOpen()
                        LogstageCodec[String].write(writer, discrimValue)
                        writer.mapElementSplitter()
                        codec.write(writer, value)
                        writer.nextMapElementClose()
                    case (_, _)                                   => () // throw exception ?
                end match
            end writeInnerImpl
        end new
    end sum

    override def newtype[A](using nt: ValueClass[A])(using codec: EvoLog[nt.Representation]): EvoLog[A] =
        new EvoLog[A]:
            override def write(writer: LogstageWriter, value: A): Unit =
                codec.write(writer, nt.to(value))

            override val writeInner: Option[(LogstageWriter, A) => Unit] =
                codec.writeInner.map(impl => (writer: LogstageWriter, value: A) => impl(writer, nt.to(value)))
end EvoLogTemplate
