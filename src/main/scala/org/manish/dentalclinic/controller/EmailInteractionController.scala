package org.manish.dentalclinic.controller

import akka.actor._
import akka.http.scaladsl.model.{HttpHeader, HttpResponse, StatusCodes}
import akka.http.scaladsl.server._
import org.manish.dentalclinic.util.LogUtils
import org.manish.dentalclinic.service.EmailInteractionServiceComponent._
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout

import scala.concurrent.duration._
import akka.pattern._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.HttpMethods._
import com.typesafe.config.{Config, ConfigFactory}

class EmailInteractionController(implicit system: ActorSystem) extends LogUtils with EmailInteractionJson {

  val actorRef = system.actorOf(EmailInteractionActor.props)
  implicit val timeout = Timeout(10.seconds)

  def config: Config = ConfigFactory.load()

  def emailInteractionRoute: Route = {
    path("interaction" / "email") {
      options {
        cors {
          logger.info("Options request received")
          complete(HttpResponse(StatusCodes.OK))
        }
      } ~
        post {
          cors {
            entity(as[EmailSummary]) {
              emailSummary =>
                onSuccess(actorRef ? emailSummary) {
                  case status: InteractionStatus => {
                    status code match {
                      case ErrorCodes.E20012 => {
                        complete(StatusCodes.Created, status)
                      }
                      case ErrorCodes.E20015 => {
                        complete(StatusCodes.BadRequest, status)
                      }
                      case ErrorCodes.E20018 => {
                        complete(StatusCodes.InternalServerError, status)
                      }
                    }
                  }
                }
            }
          }
        }
    }
  }

  def cors: Directive0 = {
    respondWithHeaders(`Access-Control-Allow-Origin`(config.getString("access.control.allow.origin")),
      `Access-Control-Allow-Headers`("Origin", "X-Requested-With", "Content-Type", "Accept"),
      `Access-Control-Allow-Methods`(OPTIONS, POST, PUT, GET, DELETE))
  }
}
