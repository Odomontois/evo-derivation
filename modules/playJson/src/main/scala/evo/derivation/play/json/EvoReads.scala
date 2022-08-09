package evo.derivation.play.json

import scala.deriving.Mirror
import scala.compiletime.*
import evo.derivation.internal.underiveableError

import scala.collection.immutable.Iterable
import evo.derivation.LazySummon
import evo.derivation.LazySummon.LazySummonByConfig
import LazySummon.useEitherFast

import java.util.Arrays
import evo.derivation.internal.mirroredNames
import evo.derivation.ValueClass
import evo.derivation.config.{Config, ForField}
import play.api.libs.json.{JsError, JsNull, JsObject, JsPath, JsResult, JsSuccess, JsValue, Json, JsonValidationError, Reads}

import scala.collection.Seq
import scala.util.Either

trait EvoReads[A] extends Reads[A]

object EvoReads:

    inline def derived[A](using config: => Config[A]): EvoReads[A] =
        summonFrom {
            case given Mirror.ProductOf[A] => deriveForProduct[A].instance
            case given Mirror.SumOf[A]     => deriveForSum[A]
            case given ValueClass[A]       => deriveForValueClass[A]
            case _                         => underiveableError[EvoReads[A], A]
        }

    inline def deriveForProduct[A](using
        mirror: Mirror.ProductOf[A],
    ): LazySummonByConfig[EvoReads, A] =
        val fieldInstances =
            LazySummon.all[mirror.MirroredElemLabels, A, Reads, EvoReads, mirror.MirroredElemTypes]
        ProductReadsMake[A](mirror)(fieldInstances)
    end deriveForProduct

    private inline def deriveForSum[A](using config: => Config[A], mirror: Mirror.SumOf[A]): EvoReads[A] =
        val constInstances =
            LazySummon.all[mirror.MirroredElemLabels, A, Reads, EvoReads, mirror.MirroredElemTypes]
        val names          = mirroredNames[A]
        SumReads(config, mirror)(constInstances.toMap[A](names), names)
    end deriveForSum

    private inline def deriveForValueClass[A](using nt: ValueClass[A]): EvoReads[A] =
        given Reads[nt.Representation] = summonInline
        NewtypeReads[A]()

    inline given [A: Mirror.ProductOf]: LazySummonByConfig[EvoReads, A] = deriveForProduct[A]

    extension [A](oa: Option[A]) private def toFailure(s: => String): JsResult[A] = oa.fold(JsError(s))(JsSuccess(_))

    private def constName(json: JsValue, discriminator: Option[String]): JsResult[(String, JsValue)] =
        discriminator match
            case Some(field) =>
                for {
                    value <- (json \ field).validate[JsValue]
                    str   <- value.validate[String]
                } yield (str, json)
            case None        =>
                for {
                    obj    <- json.validate[JsObject]
                    keys    = obj.keys
                    result <- if keys.size == 1 then JsSuccess(keys.head)
                              else JsError("Expecting an object with a single key")
                } yield (result, obj.apply(result))

    class ProductReadsMake[A](mirror: Mirror.ProductOf[A])(
        fieldInstances: LazySummon.All[Reads, mirror.MirroredElemTypes],
    ) extends LazySummonByConfig[EvoReads, A]:
        def instance(using config: => Config[A]): EvoReads[A] = new:

            lazy val infos = config.top.fieldInfos

            private def onField(
                json: JsValue,
            )(
                decoder: LazySummon.Of[Reads],
                info: ForField,
            ): Either[Seq[(JsPath, Seq[JsonValidationError])], decoder.FieldType] =
                val js = if info.embed then json else (json \ info.name).getOrElse(JsNull)
                decoder.use(js.validate[decoder.FieldType].asEither)
            end onField

            override def reads(json: JsValue): JsResult[A] =
                fieldInstances.useEitherFast[ForField, Seq[(JsPath, Seq[JsonValidationError])]](infos)(
                  onField(json),
                ) match
                    case Left(err)    => JsError(err)
                    case Right(tuple) => JsSuccess(mirror.fromProduct(tuple))
    end ProductReadsMake

    class SumReads[A](config: => Config[A], mirror: Mirror.SumOf[A])(
        mkSubDecoders: => Map[String, Reads[A]],
        names: Vector[String],
    ) extends EvoReads[A]:

        lazy val cfg                                   = config
        lazy val subDecoders                           = mkSubDecoders
        lazy val all                                   = cfg.constrFromRenamed.keys.mkString(", ")
        override def reads(json: JsValue): JsResult[A] =
            for
                subDown              <- constName(json, cfg.discriminator)
                (discriminator, down) = subDown
                subName              <- cfg.constrFromRenamed
                                            .get(discriminator)
                                            .toFailure(s"Constructor $discriminator not found; expected one of:\n $all")
                sub                  <-
                    subDecoders
                        .get(subName)
                        .toFailure(
                          s"Internal error: could not found $subName constructor info.\n This is 99% a bug, contact library authors",
                        )
                result               <- sub.reads(down)
            yield result
    end SumReads

    class NewtypeReads[A](using nt: ValueClass[A])(using reads: Reads[nt.Representation]) extends EvoReads[A]:
        override def reads(json: JsValue): JsResult[A] = json.validate[nt.Representation].map(nt.from)
end EvoReads
