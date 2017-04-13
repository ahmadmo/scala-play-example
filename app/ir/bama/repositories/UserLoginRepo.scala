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

import java.util.Date
import javax.inject.{Inject, Singleton}

import ir.bama.models.{User, UserLogin}
import play.api.db.slick.DatabaseConfigProvider
import slick.lifted.{ForeignKeyQuery, ProvenShape}
import slick.sql.SqlProfile.ColumnOption.NotNull

import scala.concurrent.ExecutionContext

/**
  * @author ahmad
  */
@Singleton
class UserLoginRepo @Inject()(dbConfigProvider: DatabaseConfigProvider, userRepo: UserRepo)
                             (implicit ec: ExecutionContext) extends BaseRepo[UserLogin](dbConfigProvider) {

  import dbConfig._
  import profile.api._

  private type LoginRow = (Option[Long], Long, String, Date)

  class UserLoginTable(tag: Tag) extends Table[UserLogin](tag, "T_USER_LOGIN") {

    def id: Rep[Long] = column[Long]("C_ID", O.PrimaryKey, O.AutoInc, NotNull)

    def userId: Rep[Long] = column[Long]("C_USER_ID", NotNull)

    def user: ForeignKeyQuery[_, User] = foreignKey("FK_USER_LOGIN", userId, userRepo.query)(_.id, onDelete = ForeignKeyAction.Cascade)

    def ip: Rep[String] = column[String]("C_IP", NotNull)

    def lastAccessTime: Rep[Date] = column[Date]("C_LAST_ACCESS_TIME", NotNull)

    override def * : ProvenShape[UserLogin] = (id.?, userId, ip, lastAccessTime) <> (toLogin, fromLogin)

  }

  private val toLogin: LoginRow => UserLogin = {
    case (id, _, ip, lastAccessTime) => UserLogin(id, None, ip, lastAccessTime)
  }

  private val fromLogin: UserLogin => Option[LoginRow] = {
    case UserLogin(id, Some(user), ip, lastAccessTime) => Some(id, user.id.get, ip, lastAccessTime)
  }

  override type TableType = UserLoginTable
  override val query: TableQuery[UserLoginTable] = TableQuery[UserLoginTable]
  override protected val idColumn: (UserLoginTable) => Rep[Long] = _.id

  def updateAccess(loginId: Long, idleTimeout: Long): DBIO[Boolean] =
    filterNotExpired(loginId, idleTimeout).map(_.lastAccessTime).update(new Date()).map(_ == 1)

  def findUserId(loginId: Long, idleTimeout: Long): DBIO[Option[Long]] =
    filterNotExpired(loginId, idleTimeout).map(_.userId).result.headOption

  def listByUserId(userId: Long, idleTimeout: Long): DBIO[Seq[UserLogin]] =
    filterNotExpiredByUserId(userId, idleTimeout).sortBy(_.lastAccessTime desc).result

  def delete(loginId: Long, idleTimeout: Long): DBIO[Boolean] =
    filterNotExpired(loginId, idleTimeout).delete.map(_ == 1)

  def deleteOther(loginId: Long, idleTimeout: Long): DBIO[Boolean] =
    filterNotExpired(loginId, idleTimeout).map(_.userId).result.headOption.flatMap {
      case Some(userId) => query.filter(login => login.id =!= loginId && login.userId === userId).delete.map(_ => true)
      case _ => DBIO.successful(false)
    }

  def deleteAll(loginId: Long, idleTimeout: Long): DBIO[Boolean] =
    filterNotExpired(loginId, idleTimeout).map(_.userId).result.headOption.flatMap {
      case Some(userId) => query.filter(_.userId === userId).delete.map(_ > 0)
      case _ => DBIO.successful(false)
    }

  private def filterNotExpired(loginId: Long, idleTimeout: Long) = query.filter { login =>
    login.id === loginId && notExpired(login, idleTimeout)
  }

  private def filterNotExpiredByUserId(userId: Long, idleTimeout: Long) = query.filter { login =>
    login.userId === userId && notExpired(login, idleTimeout)
  }

  private def notExpired(login: UserLoginTable, idleTimeout: Long) =
    login.lastAccessTime > new Date(System.currentTimeMillis - idleTimeout)

  def deleteExpiredLogins(idleTimeout: Long): DBIO[Int] = query.filter {
    _.lastAccessTime <= new Date(System.currentTimeMillis - idleTimeout)
  }.delete

}
