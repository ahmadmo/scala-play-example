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

package ir.bama.models

import java.time.LocalDateTime

import ir.bama.utils.{Dates, Enums}
import play.api.libs.functional.syntax._
import play.api.libs.json._

/**
  * @author ahmad
  */
case class User(id: Option[Long], username: String, passHash: String) extends Identifiable

object User {

  private val reads: Reads[User] =
    ((JsPath \ "id").readNullable[Long] and
      (JsPath \ "username").read[String]).tupled.map {
      case (id, username) => User(id, username, null)
    }

  private val writes: Writes[User] = Writes[User] { user =>
    Json.obj(
      "id" -> user.id,
      "username" -> user.username
    )
  }

  implicit val format: Format[User] = Format(reads, writes)

}

case class UserLogin(id: Option[Long], user: Option[User], ip: String, lastAccessTime: LocalDateTime) extends Identifiable

object UserLogin {

  implicit val dateFormat: Format[LocalDateTime] = Dates.dateFormat

  private val reads: Reads[UserLogin] =
    ((JsPath \ "id").readNullable[Long] and
      (JsPath \ "ip").read[String] and
      (JsPath \ "lastAccessTime").read[LocalDateTime]).tupled.map {
      case (id, ip, lastAccessTime) => UserLogin(id, None, ip, lastAccessTime)
    }

  private val writes: Writes[UserLogin] = Writes[UserLogin] { login =>
    Json.obj(
      "ip" -> login.ip,
      "lastAccessTime" -> login.lastAccessTime
    )
  }

  implicit val format: Format[UserLogin] = Format(reads, writes)

}

object LogoutTarget extends Enumeration {

  val THIS = Value("THIS")
  val ALL = Value("ALL")
  val OTHER = Value("OTHER")
  type LogoutTarget = Value

  implicit val format: Format[LogoutTarget] = Enums.enumFormat(LogoutTarget)

}

