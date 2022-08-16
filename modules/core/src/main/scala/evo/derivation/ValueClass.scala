package evo.derivation

import scala.quoted.*

trait ValueClass[A]:
    type AccessorName <: String
    type TypeName <: String
    type Representation
    def accessorName: AccessorName

    def to(value: A): Representation
    def from(repr: Representation): A
end ValueClass

object ValueClass:
    transparent inline given derived[A]: ValueClass[A] = ${ derivedMacro[A] }

    def derivedMacro[A: Type](using q: Quotes): Expr[ValueClass[A]] = ValueClassMacro().result

private class ValueClassMacro[A: Type](using q: Quotes):
    import q.reflect.*

    private val aType = TypeRepr.of[A].typeSymbol

    if !aType.isClassDef && !aType.flags.is(Flags.Case) then
        report.errorAndAbort(s"${Type.show[A]} is not a case class")

    private val ValDef(name, tpe, _) = aType.primaryConstructor.paramSymss match
        case List(List(single)) => single.tree
        case _                  => report.errorAndAbort("should contain a single field")

    def result: Expr[ValueClass[A]] =

        val accNameType = ConstantType(StringConstant(name)).asType

        val reprType = tpe.tpe.asType

        (accNameType, reprType) match
            case ('[accNameT], '[reprT]) =>
                val nameExpr = Expr(name).asExprOf[accNameT]

                // TODO find a way to inline expressions directly without lambdas
                val fromRepr: Expr[reprT => A] = '{ (expr: reprT) => ${ fromExpr[reprT]('expr) } }

                val toRepr: Expr[A => reprT] = '{ (a: A) => ${ toExpr[reprT]('a) } }

                '{
                    new ValueClass[A] {
                        type AccessorName = accNameT

                        type Representation = reprT

                        def accessorName: AccessorName = $nameExpr

                        def from(repr: Representation): A = $fromRepr(repr)

                        def to(value: A): Representation = $toRepr(value)
                    }
                }
        end match
    end result

    private def fromExpr[Repr: Type](repr: Expr[Repr]): Expr[A] =

        val companion = aType.companionClass

        val apply = companion
            .declaredMethod("apply")
            .headOption
            .getOrElse(
              report.errorAndAbort(s"could not find apply method in $companion"),
            )

        val prefix = TypeRepr.of[A] match
            case TypeRef(prefix, _) => prefix
            case t                  => report.errorAndAbort(s"can't derive that for the type $t, sorry")

        val ref = Select(Ident(TermRef(prefix, aType.name)), apply)

        Apply(ref, List(repr.asTerm)).asExpr.asExprOf[A]
    end fromExpr

    private def toExpr[Repr: Type](value: Expr[A]): Expr[Repr] =
        val term = value.asTerm

        val valueField = term.symbol.declaredField(name)

        Select(term, valueField).asExpr.asExprOf[Repr]
    end toExpr
end ValueClassMacro

trait IntValueClass[A] extends ValueClass[A]:
    type Representation <: Int

    def to(value: A): Representation

    def from(repr: Representation): A
end IntValueClass

trait LongValueClass[A] extends ValueClass[A]:
    type Representation <: Long

    def to(value: A): Representation

    def from(repr: Representation): A
end LongValueClass

trait ShortValueClass[A] extends ValueClass[A]:
    type Representation <: Short

    def to(value: A): Representation

    def from(repr: Representation): A
end ShortValueClass

trait ByteValueClass[A] extends ValueClass[A]:
    type Representation <: Byte

    def to(value: A): Representation

    def from(repr: Representation): A
end ByteValueClass

trait BooleanValueClass[A] extends ValueClass[A]:
    type Representation <: Boolean

    def to(value: A): Representation

    def from(repr: Representation): A
end BooleanValueClass

trait DoubleValueClass[A] extends ValueClass[A]:
    type Representation <: Double

    def to(value: A): Representation

    def from(repr: Representation): A
end DoubleValueClass

trait FloatValueClass[A] extends ValueClass[A]:
    type Representation <: Float

    def to(value: A): Representation

    def from(repr: Representation): A
end FloatValueClass
