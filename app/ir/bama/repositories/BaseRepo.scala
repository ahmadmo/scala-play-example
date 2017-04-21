/*
 * Copyright 2017 Ahmad Mozafarnia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ir.bama.repositories

import java.sql.Timestamp
import java.time.LocalDateTime

import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import slick.basic.DatabaseConfig
import slick.jdbc.{JdbcActionComponent, JdbcProfile}
import slick.lifted.AbstractTable
import slick.relational.RelationalTableComponent

import scala.concurrent.ExecutionContext

/**
  * @author ahmad
  */
abstract class BaseRepo[Entity](dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  protected val logger = Logger(getClass)

  val dbConfig: DatabaseConfig[JdbcProfile] = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import profile.api._

  type TableType <: RelationalTableComponent#Table[Entity]

  type Q[T <: AbstractTable[_]] = Query[T, T#TableElementType, Seq]
  type Transformer[T <: AbstractTable[_]] = (Q[T]) => Q[T]
  type MaybeTransform[T <: AbstractTable[_]] = Option[(Q[T]) => Q[T]]

  val query: TableQuery[TableType]

  protected val idColumn: (TableType) => Rep[Long]

  protected lazy val persistenceAction: JdbcActionComponent#ReturningInsertActionComposer[Entity, Long] = query.returning(query.map(idColumn))

  def persist(entity: Entity): DBIO[Long] = persistenceAction += entity

  def load(id: Rep[Long]): DBIO[Option[Entity]] = query.filter(idColumn(_) === id).result.headOption

  def list(range: Option[Range]): DBIO[Seq[Entity]] = pagedQuery(range).result

  protected def enumMapper[E <: Enumeration](enum: E): BaseColumnType[E#Value] =
    MappedColumnType.base[E#Value, String](_.toString, s => enum.withName(s))

  protected implicit val dateMapper: BaseColumnType[LocalDateTime] = MappedColumnType.base[LocalDateTime, Timestamp](
    dateTime => Timestamp.valueOf(dateTime),
    timestamp => timestamp.toLocalDateTime
  )

  protected def pagedQuery(range: Option[Range]): Q[TableType] = pagedQuery[TableType](query, range)

  protected def pagedQuery[T <: AbstractTable[_]](q: Q[T], range: Option[Range]): Q[T] =
    range.map(r => q.drop(r.start).take(r.length)).getOrElse(q)

  protected def transformAll[T <: AbstractTable[_]](xs: Transformer[T]*): Transformer[T] =
    xs.foldLeft(identity(_: Q[T])) {
      case (a, f) => a.andThen(f)
    }

  protected def maybeTransformAll[T <: AbstractTable[_]](xs: MaybeTransform[T]*): Transformer[T] =
    transformAll(xs.filter(_.isDefined).map(_.get): _*)

  protected def withIntRange[T <: AbstractTable[_]]
  (range: (Option[Int], Option[Int]), column: (T) => Rep[Int])(q: Q[T]): Q[T] =
    withRange[Int, T](range, column, (r, v) => r >= v, (r, v) => r <= v)(q)

  protected def withLongRange[T <: AbstractTable[_]]
  (range: (Option[Long], Option[Long]), column: (T) => Rep[Long])(q: Q[T]): Q[T] =
    withRange[Long, T](range, column, (r, v) => r >= v, (r, v) => r <= v)(q)

  protected def withDateRange[T <: AbstractTable[_]]
  (range: (Option[LocalDateTime], Option[LocalDateTime]), column: (T) => Rep[LocalDateTime])(q: Q[T]): Q[T] =
    withRange[LocalDateTime, T](range, column, (r, v) => r >= v, (r, v) => r <= v)(q)

  protected def withRange[A, T <: AbstractTable[_]]
  (range: (Option[A], Option[A]), column: (T) => Rep[A],
   minExpr: (Rep[A], A) => Rep[Boolean], maxExpr: (Rep[A], A) => Rep[Boolean])(q: Q[T]): Q[T] = {
    range match {
      case (Some(min), None) => q.filter(t => minExpr(column(t), min))
      case (None, Some(max)) => q.filter(t => maxExpr(column(t), max))
      case (Some(min), Some(max)) => q.filter(t => minExpr(column(t), min) && maxExpr(column(t), max))
      case _ => q
    }
  }

}
