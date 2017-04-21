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

package ir.bama

import org.mindrot.jbcrypt.{BCrypt => B}

/**
  * @author ahmad
  */
package object utils {

  implicit class RangeLike(val offset: Int) extends AnyVal {
    def ~(length: Int): Option[Range] = Some(Range(offset, offset + length))
  }

  implicit class OptionalRangeLike(val offset: Option[Int]) {
    def ~(length: Option[Int]): Option[Range] =
      (offset, length) match {
        case (Some(o), Some(l)) => Some(Range(o, o + l))
        case (Some(o), None) => Some(Range(o, 10))
        case (None, Some(l)) => Some(Range(0, l))
        case (None, None) => None
      }
  }

  implicit class PasswordLike(val pass: String) extends AnyVal {

    def bcrypt: String = B.hashpw(pass, B.gensalt())

    def bcrypt(rounds: Int): String = B.hashpw(pass, B.gensalt(rounds))

    def bcrypt(salt: String): String = B.hashpw(pass, salt)

    def bcryptMatches(hashed: String): Boolean = B.checkpw(pass, hashed)

  }

}
