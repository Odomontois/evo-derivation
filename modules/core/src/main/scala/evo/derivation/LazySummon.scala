package evo.derivation

import evo.derivation.config.Config
import scala.annotation.implicitNotFound
import scala.compiletime.{summonAll, uninitialized, erasedValue, summonFrom, error, constValue, summonInline}
import scala.reflect.ClassTag.apply
import scala.reflect.ClassTag
import internal.arrays.*
import internal.{printWarning, showType}
import compiletime.erasedValue
import scala.quoted.Quotes
import scala.quoted.Expr
import scala.quoted.Type

trait LazySummon[+Name, +From, TC[_], +TCC[x], A]:
    type FieldType = A
    def tc: TC[A]

    final def use[R](f: TC[A] ?=> R): R =
        given TC[A] = tc
        f
end LazySummon

object LazySummon:
    type Of[TC[_]] = LazySummon[_, _, TC, _, _]

    type Applied[TC[_], TCC[x], From, NR] = NR match
        case (name, req) => LazySummon[name, From, TC, TCC, req]

    opaque type All[TC[_], Fields <: Tuple] = IArray[Of[TC]]

    trait LazySummonByInstance[TC[_], A] extends LazySummon[Nothing, Nothing, TC, Nothing, A]

    trait LazySummonByConfig[+TCC[_], From, A]:
        def instance(using => Config[A]): TCC[A]

    given byInstance[TC[_], A](using instance: => TC[A]): LazySummonByInstance[TC, A] with
        lazy val tc = instance

    given byConfig[Name <: String, From, TC[_], TCC[x] <: TC[x], A](using
        constructor: LazySummonByConfig[TCC, From, A],
        config: => Config[From],
        name: ValueOf[Name],
    ): LazySummon[Name, From, TC, TCC, A] with
        def tc: TC[A] = constructor.instance(using config.subordinate(name.value).as[A])
    end byConfig

    inline def all[Names <: Tuple, From, TC[_], TCC[x], Fields <: Tuple]: All[TC, Fields] =
        allIter[From, TC, TCC](erasedValue[Names], erasedValue[Fields]).toIArray.map(_.asInstanceOf[Of[TC]])

    private inline def allIter[From, TC[_], TCC[x]](inline names: Tuple, inline fields: Tuple): Tuple =
        inline fields match
            case _: (req *: restFields) =>
                inline names match
                    case t: (name *: restNames) =>
                        type Req  = req
                        type Name = name
                        val head = summonFrom {
                            case lzq: LazySummon[Name, From, TC, TCC, Req] => lzq
                            case _                                         =>
                                printWarning(
                                  "during derivation for field or constructor " +
                                      constValue[name] +
                                      " of " +
                                      showType[From],
                                )
                                LazySummon.byInstance(using summonInline[TC[req]])
                        }
                        head *: allIter[From, TC, TCC](erasedValue[restNames], erasedValue[restFields])
            case _: EmptyTuple          => EmptyTuple
    end allIter

    extension [TC[_], Fields <: Tuple](all: All[TC, Fields])
        def useOn[Src[_], Res[_]](fields: Tuple.Map[Fields, Src])(
            f: [A] => (TC[A], Src[A]) => Res[A],
        ): Tuple.Map[Fields, Res] =
            val elements = all.zip(fields.toIArray).map { case (inst: LazySummon[_, _, TC, tcc, a], field) =>
                f(inst.tc, field.asInstanceOf[Src[a]])
            }

            Tuple.fromIArray(elements).asInstanceOf[Tuple.Map[Fields, Res]]
        end useOn

        def useCollect[Res: ClassTag, Info](fields: Fields, infos: Vector[Info])(
            f: [A] => (Info, A, TC[A]) => Res,
        ): Vector[Res] =
            fields.toArray
                .lazyZip(all)
                .lazyZip(infos)
                .map[Res, Array[Res]] { (field, inst, info) =>
                    f(info, field.asInstanceOf[inst.FieldType], inst.tc)
                }
                .toVector

        def useForeach[U, Info](fields: Fields, infos: Vector[Info])(
            f: [A] => (Info, A, TC[A]) => U,
        ): Unit =
            fields.toArray
                .lazyZip(all)
                .lazyZip(infos)
                .foreach[U] { (field, inst, info) =>
                    f(info, field.asInstanceOf[inst.FieldType], inst.tc)
                }

        def useEitherFast[Info, E](infos: IArray[Info])(
            f: (summon: Of[TC], info: Info) => Either[E, summon.FieldType],
        ): Either[E, Fields] =
            var error: Left[E, Nothing] | Null = null
            var elements                       = IArray.newBuilder[Any]
            all.forEachInline2(infos) { (inst, info) =>
                f(inst, info) match
                    case err: Left[E, Any]     =>
                        error = err.asInstanceOf[Left[E, Nothing]]
                        false
                    case good: Right[Any, Any] =>
                        elements += good.value
                        true
            }
            error match
                case null                 =>
                    Right(Tuple.fromIArray(elements.result()).asInstanceOf[Fields])
                case err: Left[E, Fields] => err
        end useEitherFast

        def useEithers[Info, E](infos: IArray[Info])(
            f: (summon: Of[TC], info: Info) => Either[E, summon.FieldType],
        ): Either[Vector[E], Fields] =
            val errors   = Vector.newBuilder[E]
            var err      = false
            var elements = IArray.newBuilder[Any]

            all.forEachInline2(infos) { (inst, info) =>
                f(inst, info) match
                    case Left(e)          =>
                        errors += e
                        err = true
                        true
                    case Right(a) if !err =>
                        elements += a
                        true
                    case _                => true
            }
            if err
            then Left(errors.result)
            else Right(Tuple.fromIArray(elements.result()).asInstanceOf[Fields])
        end useEithers

        inline def toMap[A](names: Vector[String]): Map[String, TC[A]] =
            inline erasedValue[Tuple.Union[Fields]] match
                case _: A =>
                    names.zip(all).map((name, lz) => name -> lz.asInstanceOf[LazySummon[_, _, TC, _, A]].tc).toMap

        inline def toVector[A]: Vector[TC[A]] =
            inline erasedValue[Tuple.Union[Fields]] match
                case _: A =>
                    all.map(lz => lz.tc.asInstanceOf[TC[A]]).toVector
    end extension
end LazySummon
