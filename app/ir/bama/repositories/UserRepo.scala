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

import ir.bama.models.User
import play.api.db.slick.DatabaseConfigProvider
import slick.lifted.ProvenShape
import slick.sql.SqlProfile.ColumnOption.NotNull

import scala.concurrent.ExecutionContext

/**
  * @author ahmad
  */
@Singleton
class UserRepo @Inject()(dbConfigProvider: DatabaseConfigProvider)
                        (implicit ec: ExecutionContext) extends BaseRepo[User](dbConfigProvider) {

  import dbConfig._
  import profile.api._

  class UserTable(tag: Tag) extends Table[User](tag, "T_USER") {

    def id: Rep[Long] = column[Long]("C_ID", O.PrimaryKey, O.AutoInc, NotNull)

    def username: Rep[String] = column[String]("C_USERNAME", O.Unique, O.SqlType("VARCHAR"), O.Length(255), NotNull)

    def passHash: Rep[String] = column[String]("C_PASS_HASH", O.SqlType("VARCHAR"), O.Length(255), NotNull)

    override def * : ProvenShape[User] = (id.?, username, passHash) <> ((User.apply _).tupled, User.unapply)

  }

  override type TableType = UserTable
  override val query: TableQuery[UserTable] = TableQuery[UserTable]
  override protected val idColumn: (UserTable) => Rep[Long] = _.id

  override def persist(user: User): DBIO[Long] =
    super.persist(user.copy(username = user.username.toLowerCase))

  def loadByCredentials(username: String, passwordMatcher: (String) => Boolean): DBIO[Option[User]] =
    query.filter(_.username === username.toLowerCase).result.headOption.map {
      _.filter(user => passwordMatcher(user.passHash))
    }

}
