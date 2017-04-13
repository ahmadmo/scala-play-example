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

import javax.inject.{Inject, Singleton}

import ir.bama.models.Province
import play.api.db.slick.DatabaseConfigProvider
import slick.lifted.ProvenShape
import slick.sql.SqlProfile.ColumnOption.NotNull

import scala.concurrent.ExecutionContext

/**
  * @author ahmad
  */
@Singleton
class ProvinceRepo @Inject()(dbConfigProvider: DatabaseConfigProvider)
                            (implicit ec: ExecutionContext) extends BaseRepo[Province](dbConfigProvider) {

  import dbConfig._
  import profile.api._

  class ProvinceTable(tag: Tag) extends Table[Province](tag, "T_PROVINCE") {

    def id: Rep[Long] = column[Long]("C_ID", O.PrimaryKey, NotNull)

    def name: Rep[String] = column[String]("C_NAME", O.Unique, O.SqlType("VARCHAR"), O.Length(255), NotNull)

    override def * : ProvenShape[Province] = (id.?, name) <> ((Province.apply _).tupled, Province.unapply)

  }

  override type TableType = ProvinceTable
  override val query: TableQuery[ProvinceTable] = TableQuery[ProvinceTable]
  override protected val idColumn: (ProvinceTable) => Rep[Long] = _.id

}

