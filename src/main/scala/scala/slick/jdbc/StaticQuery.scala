package scala.slick.jdbc

import scala.language.implicitConversions
import scala.collection.mutable.ArrayBuffer

import java.sql.PreparedStatement

import scala.slick.dbio.Effect
import scala.slick.profile.SqlStreamingAction


///////////////////////////////////////////////////////////////////////////////// Invoker-based API

/** A builder for Plain SQL queries. */
class StaticQuery[-P,+R](query: String, pconv: SetParameter[P], rconv: GetResult[R]) extends (P => Invoker[R]) {
  /** Append a string to this query */
  def + (s: String) = new StaticQuery(query + s, pconv, rconv)

  /** Append a bind variable to this query */
  def +? [T](v: T)(implicit p: SetParameter[T]) = new StaticQuery(query + '?', new SetParameter[P] {
    def apply(param: P, pp: PositionedParameters) {
      pconv(param, pp)
      p(v, pp)
    }
  }, rconv)

  def apply(param: P): StaticQueryInvoker[P, R] = new StaticQueryInvoker[P, R](query, pconv, param, rconv)
}

object StaticQuery {
  def apply[R](implicit conv: GetResult[R]) = queryNA("")
  def apply[P, R](implicit pconv1: SetParameter[P],  rconv: GetResult[R]) = query[P,R]("")
  def u = updateNA("")
  def u1[P](implicit pconv1: SetParameter[P]) = update[P]("")

  def query[P,R](query: String)(implicit pconv: SetParameter[P], rconv: GetResult[R]) =
    new StaticQuery[P, R](query, pconv, rconv)

  def queryNA[R](query: String)(implicit rconv: GetResult[R]) =
    new StaticQuery[Unit, R](query, SetParameter.SetUnit, rconv)

  def update[P](query: String)(implicit pconv: SetParameter[P]) =
    new StaticQuery[P, Int](query, pconv, GetResult.GetUpdateValue)

  def updateNA(query: String) =
    new StaticQuery[Unit, Int](query, SetParameter.SetUnit, GetResult.GetUpdateValue)

  @inline implicit def interpolation(s: StringContext) = new SQLInterpolation(s)

  /** Automatically apply a parameterless query */
  @inline implicit def staticQueryToInvoker[R](s: StaticQuery[Unit, R]): StaticQueryInvoker[Unit, R] = s(())
}

/** Invoker for Plain SQL queries. */
class StaticQueryInvoker[-P, +R](val getStatement: String, pconv: SetParameter[P], param: P, rconv: GetResult[R]) extends StatementInvoker[R] {
  protected def setParam(st: PreparedStatement) = pconv(param, new PositionedParameters(st))
  protected def extractValue(rs: PositionedResult): R = rconv(rs)
}

@deprecated("Use the new Action-based Plain SQL API from driver.api instead", "3.0")
class SQLInterpolation(val s: StringContext) extends AnyVal {
  /** Build a SQLInterpolationResult via string interpolation */
  def sql[P](param: P)(implicit pconv: SetParameter[P]) =
    new SQLInterpolationResult[P](s.parts, param, pconv)
  /** Build a StaticQuery for an UPDATE statement via string interpolation */
  def sqlu[P](param: P)(implicit pconv: SetParameter[P]) = sql(param).asUpdate
}

object SQLInterpolation {
  def parse[P](strings: Seq[String], param: P, pconv: SetParameter[P]): (String, SetParameter[Unit]) = {
    if(strings.length == 1) (strings(0), SetParameter.SetUnit)
    else {
      val (convs, params) = pconv match {
        case pconv: SetTupleParameter[_] =>
          (pconv.children.iterator, param.asInstanceOf[Product].productIterator)
        case _ => (Iterator(pconv), Iterator(param))
      }
      val b = new StringBuilder
      val remaining = new ArrayBuffer[SetParameter[Unit]]
      convs.zip(params).zip(strings.iterator).foreach { zipped =>
        val p = zipped._1._2
        var literal = false
        def decode(s: String): String =
          if(s.endsWith("##")) decode(s.substring(0, s.length-2)) + "#"
          else if(s.endsWith("#")) { literal = true; s.substring(0, s.length-1) }
          else s
        b.append(decode(zipped._2))
        if(literal) b.append(p.toString)
        else {
          b.append('?')
          remaining += zipped._1._1.asInstanceOf[SetParameter[Any]].applied(p)
        }
      }
      b.append(strings.last)
      (b.toString, new SetParameter[Unit] {
        def apply(u: Unit, pp: PositionedParameters): Unit =
          remaining.foreach(_.apply(u, pp))
      })
    }
  }
}

@deprecated("Use the new Action-based Plain SQL API from driver.api instead", "3.0")
case class SQLInterpolationResult[P](strings: Seq[String], param: P, pconv: SetParameter[P]) {
  def as[R](implicit rconv: GetResult[R]): StaticQuery[Unit, R] = {
    val (sql, unitPConv) = SQLInterpolation.parse(strings, param, pconv)
    new StaticQuery[Unit, R](sql, unitPConv, rconv)
  }
  def asUpdate = as[Int](GetResult.GetUpdateValue)
}


////////////////////////////////////////////////////////////////////////////////// Action-based API

class ActionBasedSQLInterpolation(val s: StringContext) extends AnyVal {
  /** Build a SQLActionBuilder via string interpolation */
  def sql[P](param: P)(implicit pconv: SetParameter[P]): SQLActionBuilder[P] =
    new SQLActionBuilder[P](s.parts, param, pconv)
  /** Build an Action for an UPDATE statement via string interpolation */
  def sqlu[P](param: P)(implicit pconv: SetParameter[P]) = sql(param).asUpdate
}

case class SQLActionBuilder[P](strings: Seq[String], param: P, pconv: SetParameter[P]) {
  def as[R](implicit rconv: GetResult[R]): SqlStreamingAction[Effect, Vector[R], R] = new StreamingInvokerAction[Effect, Vector[R], R] {
    val (sql, unitPConv) = SQLInterpolation.parse(strings, param, pconv)
    def statements = List(sql)
    protected[this] def createInvoker(statements: Iterable[String]) = new StaticQueryInvoker[Unit, R](statements.head, unitPConv, (), rconv)
    protected[this] def createBuilder = Vector.newBuilder[R]
  }
  def asUpdate = as[Int](GetResult.GetUpdateValue)
}
