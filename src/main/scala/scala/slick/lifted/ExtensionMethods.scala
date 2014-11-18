package scala.slick.lifted

import scala.language.{ implicitConversions, higherKinds }
import scala.slick.ast._
import FunctionSymbolExtensionMethods._
import ScalaBaseType._
import scala.slick.SlickException

trait ExtensionMethods[B1, P1] extends Any {
  protected[this] def c: Column[P1]
  @inline protected[this] def n = c.toNode
  @inline protected[this] implicit def p1Type = c.tpe
  implicit protected[this] def b1Type = (c.tpe match {
    case o: OptionTypedType[_] => o.elementType
    case b                     => b
  }).asInstanceOf[TypedType[B1]]
  implicit protected[this] def optionType = (c.tpe match {
    case o: OptionTypedType[_] => o
    case b                     => b.optionType
  }).asInstanceOf[TypedType[Option[B1]]]
  protected[this]type o = OptionMapperDSL.arg[B1, P1]
}

/** Extension methods for all Columns and all primitive values that can be lifted to Columns */
final class AnyExtensionMethods(val n: Node) extends AnyVal {
  def asColumnOf[U: TypedType] = Library.Cast.column[U](n)
  def asColumnOfType[U: TypedType](typeName: String) =
    Library.Cast.column[U](n, LiteralNode(implicitly[TypedType[U]], typeName))
}

/** Extension methods for all Columns */
trait ColumnExtensionMethods[B1, P1] extends Any with ExtensionMethods[B1, P1] {
  protected[this] def c: Column[P1]

  @deprecated("Use 'isEmpty' instead of 'isNull'", "2.1")
  def isNull = Library.==.column[Boolean](n, LiteralNode(null))
  @deprecated("Use 'isDefined' instead of 'isNotNull'", "2.1")
  def isNotNull = Library.Not.column[Boolean](Library.==.typed[Boolean](n, LiteralNode(null)))

  def ===[P2, R](e: Column[P2])(implicit om: o#arg[B1, P2]#to[Boolean, R]) =
    om.column(Library.==, n, e.toNode)
  @deprecated("Use '===' instead of 'is'", "2.1")
  def is[P2, R](e: Column[P2])(implicit om: o#arg[B1, P2]#to[Boolean, R]) =
    ===[P2, R](e)
  @deprecated("Use '=!=' instead of 'isNot'", "2.1")
  def isNot[P2, R](e: Column[P2])(implicit om: o#arg[B1, P2]#to[Boolean, R]) =
    =!=[P2, R](e)
  def =!=[P2, R](e: Column[P2])(implicit om: o#arg[B1, P2]#to[Boolean, R]) =
    om.column(Library.Not, Library.==.typed(om.liftedType, n, e.toNode))

  def <[P2, R](e: Column[P2])(implicit om: o#arg[B1, P2]#to[Boolean, R]) =
    om.column(Library.<, n, e.toNode)
  def <=[P2, R](e: Column[P2])(implicit om: o#arg[B1, P2]#to[Boolean, R]) =
    om.column(Library.<=, n, e.toNode)
  def >[P2, R](e: Column[P2])(implicit om: o#arg[B1, P2]#to[Boolean, R]) =
    om.column(Library.>, n, e.toNode)
  def >=[P2, R](e: Column[P2])(implicit om: o#arg[B1, P2]#to[Boolean, R]) =
    om.column(Library.>=, n, e.toNode)

  def in[P2, R, C[_]](e: Query[Column[P2], _, C])(implicit om: o#arg[B1, P2]#to[Boolean, R]) =
    om.column(Library.In, n, e.toNode)
  def inSet[R](seq: Traversable[B1])(implicit om: o#to[Boolean, R]) =
    if (seq.isEmpty) om(LiteralColumn(false))
    else om.column(Library.In, n, ProductNode(seq.map { v => LiteralNode(implicitly[TypedType[B1]], v) }.toSeq))
  def inSetBind[R](seq: Traversable[B1])(implicit om: o#to[Boolean, R]) =
    if (seq.isEmpty) om(LiteralColumn(false))
    else om.column(Library.In, n, ProductNode(seq.map(v => LiteralNode(implicitly[TypedType[B1]], v, vol = true)).toSeq))

  def between[P2, P3, R](start: Column[P2], end: Column[P3])(implicit om: o#arg[B1, P2]#arg[B1, P3]#to[Boolean, R]) =
    om.column(Library.Between, n, start.toNode, end.toNode)
  def ifNull[B2, P2, R](e: Column[P2])(implicit om: o#arg[B2, P2]#to[Boolean, R]): Column[P2] =
    Library.IfNull.column[P2](n, e.toNode)(e.tpe)
}

final class PlainColumnExtensionMethods[P1](val c: Column[P1]) extends AnyVal with ColumnExtensionMethods[P1, P1] {
  def ? : Column[Option[P1]] = Column.forNode(OptionApply(c.toNode))(c.tpe.optionType)
}

final class OptionColumnExtensionMethods[B1](val c: Column[Option[B1]]) extends AnyVal with ColumnExtensionMethods[B1, Option[B1]] {
  def getOrElse(default: => B1): Column[B1] =
    Column.forNode[B1](GetOrElse(c.toNode, () => default))(c.tpe.asInstanceOf[OptionType].elementType.asInstanceOf[TypedType[B1]])
  def get: Column[B1] =
    getOrElse { throw new SlickException("Read NULL value for column " + this) }
  /** Check if this Option column is empty (i.e. the underlying value is NULL) */
  def isEmpty = Library.==.column[Boolean](n, LiteralNode(null))
  /** Check if this Option column is not empty (i.e. the underlying value is not NULL) */
  def isDefined = Library.Not.column[Boolean](Library.==.typed[Boolean](n, LiteralNode(null)))
  /** Check if this Option column is not empty (i.e. the underlying value is not NULL) */
  def nonEmpty = isDefined
}

/** Extension methods for numeric Columns */
final class NumericColumnExtensionMethods[B1, P1](val c: Column[P1]) extends AnyVal with ExtensionMethods[B1, P1] {
  def +[P2, R](e: Column[P2])(implicit om: o#arg[B1, P2]#to[B1, R]) =
    om.column(Library.+, n, e.toNode)
  def -[P2, R](e: Column[P2])(implicit om: o#arg[B1, P2]#to[B1, R]) =
    om.column(Library.-, n, e.toNode)
  def *[P2, R](e: Column[P2])(implicit om: o#arg[B1, P2]#to[B1, R]) =
    om.column(Library.*, n, e.toNode)
  def /[P2, R](e: Column[P2])(implicit om: o#arg[B1, P2]#to[B1, R]) =
    om.column(Library./, n, e.toNode)
  def %[P2, R](e: Column[P2])(implicit om: o#arg[B1, P2]#to[B1, R]) =
    om.column(Library.%, n, e.toNode)
  def abs = Library.Abs.column[P1](n)
  def ceil = Library.Ceiling.column[P1](n)
  def floor = Library.Floor.column[P1](n)
  def sign[R](implicit om: o#to[Int, R]) =
    om.column(Library.Sign, n)
  def toDegrees = Library.Degrees.column[P1](n)
  def toRadians = Library.Radians.column[P1](n)
}

/** Extension methods for Column[Boolean] and Column[Option[Boolean]] */
final class BooleanColumnExtensionMethods[P1](val c: Column[P1]) extends AnyVal with ExtensionMethods[Boolean, P1] {
  def &&[P2, R](b: Column[P2])(implicit om: o#arg[Boolean, P2]#to[Boolean, R]) =
    om.column(Library.And, n, b.toNode)
  def ||[P2, R](b: Column[P2])(implicit om: o#arg[Boolean, P2]#to[Boolean, R]) =
    om.column(Library.Or, n, b.toNode)
  def unary_! = Library.Not.column[Boolean](n)
}

/** Extension methods for Column[String] and Column[Option[String]] */
final class StringColumnExtensionMethods[P1](val c: Column[P1]) extends AnyVal with ExtensionMethods[String, P1] {
  def length[R](implicit om: o#to[Int, R]) =
    om.column(Library.Length, n)
  def like[P2, R](e: Column[P2], esc: Char = '\u0000')(implicit om: o#arg[String, P2]#to[Boolean, R]) =
    if (esc == '\u0000') om.column(Library.Like, n, e.toNode)
    else om.column(Library.Like, n, e.toNode, LiteralNode(esc))
  def ++[P2, R](e: Column[P2])(implicit om: o#arg[String, P2]#to[String, R]) =
    om.column(Library.Concat, n, e.toNode)
  def startsWith[R](s: String)(implicit om: o#to[Boolean, R]) =
    om.column(Library.StartsWith, n, LiteralNode(s))
  def endsWith[R](s: String)(implicit om: o#to[Boolean, R]) =
    om.column(Library.EndsWith, n, LiteralNode(s))
  def toUpperCase = Library.UCase.column[P1](n)
  def toLowerCase = Library.LCase.column[P1](n)
  def ltrim = Library.LTrim.column[P1](n)
  def rtrim = Library.RTrim.column[P1](n)
  def trim = Library.Trim.column[P1](n)
  def reverseString = Library.Reverse.column[P1](n)
  def substring[P2, P3, R](start: Column[P2], end: Column[P3])(implicit om: o#arg[Int, P2]#arg[Int, P3]#to[String, R]) =
    om.column(Library.Substring, n, start.toNode, end.toNode)
  def substring[P2, R](start: Column[P2])(implicit om: o#arg[Int, P2]#to[String, R]) =
    om.column(Library.Substring, n, start.toNode)
  def take[P2, R](num: Column[P2])(implicit om: o#arg[Int, Int]#arg[Int, P2]#to[String, R]) =
    substring[Int, P2, R](LiteralColumn(0), num)
  def drop[P2, R](num: Column[P2])(implicit om: o#arg[Int, P2]#to[String, R]) =
    substring[P2, R](num)
  def replace[P2, P3, R](target: Column[P2], replacement: Column[P3])(implicit om: o#arg[String, P2]#arg[String, P3]#to[String, R]) =
    om.column(Library.Replace, n, target.toNode, replacement.toNode)
  def indexOf[P2, R](str: Column[P2])(implicit om: o#arg[String, P2]#to[Int, R]) =
    om.column(Library.IndexOf, n, str.toNode)
}

/** Extension methods for Queries of a single Column */
final class SingleColumnQueryExtensionMethods[B1, P1, C[_]](val q: Query[Column[P1], _, C]) extends AnyVal {
  type OptionTM = TypedType[Option[B1]]
  def min(implicit tm: OptionTM) = Library.Min.column[Option[B1]](q.toNode)
  def max(implicit tm: OptionTM) = Library.Max.column[Option[B1]](q.toNode)
  def avg(implicit tm: OptionTM) = Library.Avg.column[Option[B1]](q.toNode)
  def sum(implicit tm: OptionTM) = Library.Sum.column[Option[B1]](q.toNode)
}

final class StringColumnQueryExtensionMethods[B1 <: String, P1, C[_]](val q: Query[Column[P1], _, C]) extends AnyVal {
  type OptionTM = TypedType[Option[B1]]
  def mkString(separator: String)(implicit tm: OptionTM) = Library.MkString.column[String](q.toNode) //, LiteralColumn(separator).toNode)
}

trait ExtensionMethodConversions {
  implicit def anyColumnExtensionMethods[B1: BaseTypedType](c: Column[B1]) = new AnyExtensionMethods(c.toNode)
  implicit def anyOptionColumnExtensionMethods[B1](c: Column[Option[B1]]) = new AnyExtensionMethods(c.toNode)
  implicit def anyValueExtensionMethods[B1: BaseTypedType](v: B1) = new AnyExtensionMethods(LiteralNode(implicitly[TypedType[B1]], v))
  implicit def anyOptionValueExtensionMethods[B1: TypedType](v: Option[B1]) = new AnyExtensionMethods(LiteralNode(implicitly[TypedType[Option[B1]]], v))
  implicit def columnExtensionMethods[B1: BaseTypedType](c: Column[B1]) = new PlainColumnExtensionMethods[B1](c)
  implicit def optionColumnExtensionMethods[B1](c: Column[Option[B1]]) = new OptionColumnExtensionMethods[B1](c)
  implicit def numericColumnExtensionMethods[B1](c: Column[B1])(implicit tm: BaseTypedType[B1] with NumericTypedType) = new NumericColumnExtensionMethods[B1, B1](c)
  implicit def numericOptionColumnExtensionMethods[B1](c: Column[Option[B1]])(implicit tm: BaseTypedType[B1] with NumericTypedType) = new NumericColumnExtensionMethods[B1, Option[B1]](c)
  implicit def stringColumnExtensionMethods(c: Column[String]) = new StringColumnExtensionMethods[String](c)
  implicit def stringOptionColumnExtensionMethods(c: Column[Option[String]]) = new StringColumnExtensionMethods[Option[String]](c)
  implicit def booleanColumnExtensionMethods(c: Column[Boolean]) = new BooleanColumnExtensionMethods[Boolean](c)
  implicit def booleanOptionColumnExtensionMethods(c: Column[Option[Boolean]]) = new BooleanColumnExtensionMethods[Option[Boolean]](c)

  implicit def singleColumnQueryExtensionMethods[B1: BaseTypedType, C[_]](q: Query[Column[B1], _, C]) = new SingleColumnQueryExtensionMethods[B1, B1, C](q)
  implicit def singleOptionColumnQueryExtensionMethods[B1, C[_]](q: Query[Column[Option[B1]], _, C]) = new SingleColumnQueryExtensionMethods[B1, Option[B1], C](q)
  
  implicit def stringColumnQueryExtensionMethods[B1 <: String : BaseTypedType, C[_]](q: Query[Column[B1], _, C]) = new StringColumnQueryExtensionMethods[B1, B1, C](q)
  implicit def stringOptionColumnQueryExtensionMethods[B1 <: String, C[_]](q: Query[Column[Option[B1]], _, C]) = new StringColumnQueryExtensionMethods[B1, Option[B1], C](q)   
}
