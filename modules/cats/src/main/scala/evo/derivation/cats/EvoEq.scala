package evo.derivation.cats

import evo.derivation.LazySummon.All
import evo.derivation.{LazySummon, ValueClass}
import evo.derivation.config.Config
import evo.derivation.internal.{Matching, mirroredNames, underiveableError}
import evo.derivation.template.{ConsistentTemplate, SummonForProduct}

import scala.compiletime.{summonFrom, summonInline}
import scala.deriving.Mirror
import evo.derivation.template.SummonHierarchy

trait BaseEvoEq[A]:
    def eqv(x: A, y: A): Boolean

object BaseEvoEq:
    inline given [A]: BaseEvoEq[A] =
        summonFrom {
            case byCats: cats.kernel.Eq[A] =>
                new:
                    def eqv(a: A, b: A) = byCats.eqv(a, b)
            case _                         =>
                new:
                    def eqv(a: A, b: A) = Equiv.universal[A].equiv(a, b)
        }
end BaseEvoEq

/** A typeclass providing both cats.Eq and scala.Equiv instances requiring any of them for each of subordinates */
trait EvoEq[A] extends BaseEvoEq[A] with cats.kernel.Eq[A] with Equiv[A]:
    def equiv(x: A, y: A): Boolean = eqv(x, y)

object EvoEq extends ConsistentTemplate[BaseEvoEq, EvoEq] with SummonHierarchy:
    override def product[A](using mirror: Mirror.ProductOf[A])(fields: All[BaseEvoEq, mirror.MirroredElemTypes])(using
        => Config[A],
        A <:< Product,
    ): EvoEq[A] = ProductEq(fields)

    override def sum[A](using mirror: Mirror.SumOf[A])(
        subs: All[BaseEvoEq, mirror.MirroredElemTypes],
        mkSubMap: => Map[String, BaseEvoEq[A]],
    )(using config: => Config[A], matching: Matching[A]): EvoEq[A] = SumEq(mkSubMap)

    override def newtype[A](using nt: ValueClass[A])(using reprEq: BaseEvoEq[nt.Representation]): EvoEq[A] =
        (x, y) => reprEq.eqv(nt.to(x), nt.to(y))

    class ProductEq[A](using mirror: Mirror.ProductOf[A])(using A <:< Product)(
        instances: LazySummon.All[BaseEvoEq, mirror.MirroredElemTypes],
    ) extends EvoEq[A]:

        lazy val stricts = instances.toVector[Any]

        def eqv(x: A, y: A): Boolean =
            (0 until stricts.length).forall(i => stricts(i).eqv(x.productElement(i), y.productElement(i)))

    end ProductEq

    class SumEq[A](
        mkSubEncoders: => Map[String, BaseEvoEq[A]],
    )(using config: => Config[A], mirror: Mirror.SumOf[A], matching: Matching[A])
        extends EvoEq[A]:

        lazy val subs                = mkSubEncoders
        def eqv(x: A, y: A): Boolean =
            val xname = matching.matched(x)
            matching.matched(y) == xname && {
                subs(xname).eqv(x, y)
            }
    end SumEq
end EvoEq
