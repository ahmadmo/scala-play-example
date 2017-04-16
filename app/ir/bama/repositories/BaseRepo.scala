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

import ir.bama.utils.Range
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

  protected def pagedQuery(range: Option[Range]): Query[TableType, Entity, Seq] = pagedQuery[TableType](query, range)

  protected def pagedQuery[T <: AbstractTable[_]](q: Query[T, T#TableElementType, Seq], range: Option[Range])
  : Query[T, T#TableElementType, Seq] = range.map(r => q.drop(r.start).take(r.length)).getOrElse(q)

}
