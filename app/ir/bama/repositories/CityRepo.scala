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

import ir.bama.models.{City, Province}
import play.api.db.slick.DatabaseConfigProvider
import slick.lifted.{ForeignKeyQuery, Index, ProvenShape}
import slick.sql.SqlProfile.ColumnOption.NotNull

import scala.concurrent.ExecutionContext

/**
  * @author ahmad
  */
@Singleton
class CityRepo @Inject()(dbConfigProvider: DatabaseConfigProvider, provinceRepo: ProvinceRepo)
                        (implicit ec: ExecutionContext) extends BaseRepo[City](dbConfigProvider) {

  import dbConfig._
  import profile.api._

  private type CityRow = (Option[Long], Long, String)

  class CityTable(tag: Tag) extends Table[City](tag, "T_CITY") {

    def id: Rep[Long] = column[Long]("C_ID", O.PrimaryKey, NotNull)

    def provinceId: Rep[Long] = column[Long]("C_PROVINCE_ID", NotNull)

    def province: ForeignKeyQuery[_, Province] = foreignKey("FK_PROVINCE_CITY", provinceId, provinceRepo.query)(_.id,
      onDelete = ForeignKeyAction.Cascade)

    def name: Rep[String] = column[String]("C_NAME", O.SqlType("VARCHAR"), O.Length(255), NotNull)

    def idx: Index = index("unique_name_city", (provinceId, name), unique = true)

    override def * : ProvenShape[City] = (id.?, provinceId, name) <> (toCity, fromCity)

  }

  private val toCity: CityRow => City = {
    case (id, _, name) => City(id, None, name)
  }

  private val fromCity: City => Option[CityRow] = {
    case City(id, Some(province), name) => Some(id, province.id.get, name)
  }

  override type TableType = CityTable
  override val query: TableQuery[CityTable] = TableQuery[CityTable]
  override protected val idColumn: (CityTable) => Rep[Long] = _.id

  override def load(id: Rep[Long]): DBIO[Option[City]] =
    (for {
      c: CityTable <- query if c.id === id
      p: provinceRepo.ProvinceTable <- provinceRepo.query if p.id === c.provinceId
    } yield (c, p)).result.headOption.map(_.map(refineCity))

  override def list(range: Option[Range]): DBIO[Seq[City]] =
    (for {
      c: CityTable <- pagedQuery(range)
      p: provinceRepo.ProvinceTable <- provinceRepo.query if p.id === c.provinceId
    } yield (c, p)).result.map(_.map(refineCity))

  private val refineCity: ((City, Province)) => City = {
    case (c: City, p: Province) => c.copy(province = Some(p))
  }

  def listByProvinceId(provinceId: Long, range: Option[Range]): DBIO[Seq[City]] =
    pagedQuery[CityTable](query.filter(_.provinceId === provinceId), range).result

}
