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

package ir.bama.services

import java.util.Date
import javax.inject.{Inject, Singleton}

import ir.bama.models.LogoutTarget.LogoutTarget
import ir.bama.models.{LogoutTarget, User, UserLogin}
import ir.bama.repositories.{UserLoginRepo, UserRepo}

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author ahmad
  */
@Singleton
class UserService @Inject()(userRepo: UserRepo, userLoginRepo: UserLoginRepo)
                           (implicit ec: ExecutionContext) extends BaseService[User, UserRepo](userRepo) {

  import repo.dbConfig._
  import profile.api._

  def login(username: String, passwordMatcher: (String) => Boolean, ip: String): Future[Option[Long]] = db.run {
    val action = repo.loadByCredentials(username, passwordMatcher).flatMap {
      case someUser@Some(_) => userLoginRepo.persist(UserLogin(None, someUser, ip, new Date())).map(Some(_))
      case _ => DBIO.successful(None)
    }
    action
  }

  def updateAccess(loginId: Long, idleTimeout: Long): Future[Boolean] =
    db.run(userLoginRepo.updateAccess(loginId, idleTimeout))

  def findUserId(loginId: Long, idleTimeout: Long, updateAccess: Boolean = false): Future[Option[Long]] = db.run {
    (if (updateAccess) userLoginRepo.updateAccess(loginId, idleTimeout) else DBIO.successful(true)).flatMap { success =>
      val action = if (success) userLoginRepo.findUserId(loginId, idleTimeout) else DBIO.successful(None)
      action
    }
  }

  def listLogins(userId: Long, idleTimeout: Long): Future[Seq[UserLogin]] =
    db.run(userLoginRepo.listByUserId(userId, idleTimeout))

  def logout(loginId: Long, idleTimeout: Long, target: LogoutTarget): Future[Boolean] = db.run {
    (if (target == LogoutTarget.OTHER) userLoginRepo.updateAccess(loginId, idleTimeout) else DBIO.successful(true)).flatMap { success =>
      val action = if (success) target match {
        case LogoutTarget.THIS => userLoginRepo.delete(loginId, idleTimeout)
        case LogoutTarget.OTHER => userLoginRepo.deleteOther(loginId, idleTimeout)
        case LogoutTarget.ALL => userLoginRepo.deleteAll(loginId, idleTimeout)
      } else DBIO.successful(false)
      action
    }
  }

  def deleteExpiredLogins(idleTimeout: Long): Future[Int] =
    db.run(userLoginRepo.deleteExpiredLogins(idleTimeout))

}
