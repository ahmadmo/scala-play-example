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

package ir.bama.controllers

import java.time.{LocalDateTime, ZoneId}
import java.util.Date
import javax.inject.{Inject, Singleton}

import akka.actor.{Actor, ActorSystem, Props}
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import io.jsonwebtoken.{Claims, Jwts, SignatureAlgorithm}
import ir.bama.models.LogoutTarget
import ir.bama.services.UserService
import ir.bama.utils.PasswordLike
import play.api.data.Form
import play.api.data.Forms.{mapping, nonEmptyText}
import play.api.http.HeaderNames
import play.api.libs.json.{JsString, JsValue, Json, OFormat}
import play.api.mvc.{Action, _}
import play.api.{Configuration, Logger}

import scala.concurrent.duration.{Duration, MILLISECONDS}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

/**
  * @author ahmad
  */
@Singleton
class AuthController @Inject()(userService: UserService, system: ActorSystem, configs: Configuration)
                              (implicit ec: ExecutionContext) extends Controller {

  private val logger = Logger(getClass)

  private val tokenType = "Bearer"

  private lazy val secretKey = configs.getString("play.crypto.secret").get.getBytes
  private lazy val accessTokenExpiration = Duration(configs.getMilliseconds("controllers.auth.accessTokenExpiration").get, MILLISECONDS).toSeconds
  private lazy val loginIdleTimeout = configs.getMilliseconds("controllers.auth.loginIdleTimeout").get

  // schedule token revoker actor
  QuartzSchedulerExtension(system).schedule("tokenRevoker", system.actorOf(Props(new TokenRevokerActor)), None)

  case class Credentials(username: String, password: String)

  sealed abstract class TokenResult {
    val accessToken: String
    val refreshToken: Option[String]
    val tokenType: String
    val expiresIn: Long
  }

  case class LoginResult(accessToken: String, refreshToken: Option[String], tokenType: String, expiresIn: Long) extends TokenResult

  object LoginResult {
    implicit val format: OFormat[LoginResult] = Json.format[LoginResult]
  }

  case class RefreshResult(accessToken: String, tokenType: String, expiresIn: Long) extends TokenResult {
    override val refreshToken: Option[String] = None
  }

  object RefreshResult {
    implicit val format: OFormat[RefreshResult] = Json.format[RefreshResult]
  }

  val loginForm: Form[Credentials] = Form(
    mapping(
      "username" -> nonEmptyText,
      "password" -> nonEmptyText
    )(Credentials.apply)(Credentials.unapply))

  def login: Action[JsValue] = Action.async(parse.json) { implicit request =>
    loginForm.map { credentials =>
      userService.login(credentials.username, credentials.password.bcryptMatches(_), request.remoteAddress).map {
        case Some(loginId) =>
          LoginResult(AccessToken(loginId), Some(RefreshToken(loginId)), tokenType, accessTokenExpiration).asJson
        case _ => Err.request("Bad Credentials").asJsonError(Results.Unauthorized)
      }
    }
  }

  case class LoginInfo(id: Long, userId: Long)

  private def extractToken[A](request: Request[A]) =
    request.headers.get(HeaderNames.AUTHORIZATION).filter(_.startsWith(s"$tokenType ")).map(_.substring(7))

  class UserRequest[A](val token: String, request: Request[A]) extends WrappedRequest[A](request)

  private object UserAction extends ActionBuilder[UserRequest] with ActionRefiner[Request, UserRequest] {
    override protected def refine[A](request: Request[A]): Future[Either[Result, UserRequest[A]]] =
      (extractToken(request) match {
        case Some(token) => Right(new UserRequest[A](token, request))
        case _ => Left(Err.request("Unauthorized Access").asJsonError(Results.Unauthorized))
      }).future
  }

  class AuthenticatedUserRequest[A](val login: LoginInfo, request: UserRequest[A]) extends WrappedRequest[A](request)

  private object AuthenticatedUserAction extends ActionRefiner[UserRequest, AuthenticatedUserRequest] {
    override protected def refine[A](request: UserRequest[A]): Future[Either[Result, AuthenticatedUserRequest[A]]] =
      AccessToken(request.token) match {
        case Some(loginId) => userService.findUserId(loginId, loginIdleTimeout, updateAccess = true).map {
          case Some(userId) => Right(new AuthenticatedUserRequest[A](LoginInfo(loginId, userId), request))
          case _ => Left(Err.request("Invalid Token").asJsonError(Results.Unauthorized))
        }
        case _ => Left(Err.request("Unauthorized Access").asJsonError(Results.Unauthorized)).future
      }
  }

  def authenticated: ActionBuilder[AuthenticatedUserRequest] = UserAction.andThen(AuthenticatedUserAction)

  class MaybeUserRequest[A](val token: Option[String], request: Request[A]) extends WrappedRequest[A](request)

  private object MaybeUserAction extends ActionBuilder[MaybeUserRequest] with ActionRefiner[Request, MaybeUserRequest] {
    override protected def refine[A](request: Request[A]): Future[Either[Result, MaybeUserRequest[A]]] =
      Right(new MaybeUserRequest[A](extractToken(request), request)).future
  }

  class MaybeAuthenticatedUserRequest[A](val login: Option[LoginInfo], request: MaybeUserRequest[A]) extends WrappedRequest[A](request)

  private object MaybeAuthenticatedUserAction extends ActionRefiner[MaybeUserRequest, MaybeAuthenticatedUserRequest] {
    override protected def refine[A](request: MaybeUserRequest[A]): Future[Either[Result, MaybeAuthenticatedUserRequest[A]]] =
      request.token.flatMap(AccessToken.parse) match {
        case Some(loginId) => userService.findUserId(loginId, loginIdleTimeout, updateAccess = true).map {
          case Some(userId) => Some(LoginInfo(loginId, userId))
          case _ => None
        }.map { maybeLogin =>
          Right(new MaybeAuthenticatedUserRequest[A](maybeLogin, request))
        }
        case _ => Right(new MaybeAuthenticatedUserRequest[A](None, request)).future
      }
  }

  def maybeAuthenticated: ActionBuilder[MaybeAuthenticatedUserRequest] = MaybeUserAction.andThen(MaybeAuthenticatedUserAction)

  def me: Action[AnyContent] = authenticated.async { request =>
    userService.load(request.login.userId).asJson
  }

  def listLogins: Action[AnyContent] = authenticated.async { request =>
    userService.listLogins(request.login.userId, loginIdleTimeout).asJson
  }

  def refreshToken: Action[AnyContent] = UserAction.async { request =>
    RefreshToken(request.token) match {
      case Some(loginId) =>
        userService.updateAccess(loginId, loginIdleTimeout).map { success =>
          if (success) {
            RefreshResult(AccessToken(loginId), tokenType, accessTokenExpiration).asJson
          } else {
            Err.request("Invalid Token").asJsonError(Results.Unauthorized)
          }
        }
      case _ => Err.request("Unauthorized Access").asJsonError(Results.Unauthorized).future
    }
  }

  def logout(from: String): Action[AnyContent] = Try(LogoutTarget.withName(from.toUpperCase)) match {
    case Success(target) => UserAction.async { request =>
      AccessToken(request.token) match {
        case Some(loginId) => userService.logout(loginId, loginIdleTimeout, target).map { success =>
          if (success) Ok else Err.request("Invalid Token").asJsonError(Results.Unauthorized)
        }
        case _ => Err.request("Unauthorized Access").asJsonError(Results.Unauthorized).future
      }
    }
    case _ => Action(Err.request(s"Invalid target.", "target" -> JsString(from)).asJsonError)
  }

  sealed trait Token {

    val sub: String

    def validateSubject(sub: String): Boolean = this.sub.equals(sub)

    def notExpired(exp: Date): Boolean

    def verify(claims: Claims): Option[Long] =
      if (validateSubject(claims.getSubject) && notExpired(claims.getExpiration)) {
        Some(claims.getId.toLong)
      } else {
        None
      }

    def generate(id: Long): String

    def parse(token: String): Option[Long] = Try {
      verify {
        Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody
      }
    } getOrElse None

    def apply(id: Long): String = generate(id)

    def apply(token: String): Option[Long] = parse(token)

  }

  object AccessToken extends Token {

    override val sub: String = "access_token"

    override def notExpired(exp: Date): Boolean = exp.after(new Date())

    override def generate(id: Long): String =
      Jwts.builder()
        .setSubject(sub)
        .setId(id.toString)
        .setIssuedAt(new Date())
        .setExpiration(Date.from(LocalDateTime.now().plusSeconds(accessTokenExpiration).atZone(ZoneId.systemDefault()).toInstant))
        .signWith(SignatureAlgorithm.HS512, secretKey)
        .compact()

  }

  object RefreshToken extends Token {

    override val sub: String = "refresh_token"

    override def notExpired(exp: Date): Boolean = true

    override def generate(id: Long): String =
      Jwts.builder()
        .setSubject(sub)
        .setId(id.toString)
        .setIssuedAt(new Date())
        .signWith(SignatureAlgorithm.HS512, secretKey)
        .compact()

  }

  private class TokenRevokerActor extends Actor {
    override def receive: Receive = {
      case _ => userService.deleteExpiredLogins(loginIdleTimeout).map { c =>
        logger.info(s"$c tokens were revoked")
      }
    }
  }

}
