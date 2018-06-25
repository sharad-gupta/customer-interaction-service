package org.manish.dentalclinic.controller

import akka.actor._
import akka.http.scaladsl.model.{HttpHeader, StatusCodes}
import akka.http.scaladsl.server._
import org.manish.dentalclinic.util.LogUtils
import org.manish.dentalclinic.service.EmailInteractionServiceComponent._
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout

import scala.concurrent.duration._
import akka.pattern._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.HttpMethods._

class EmailInteractionController(implicit system: ActorSystem) extends LogUtils with EmailInteractionJson {

  val actorRef = system.actorOf(EmailInteractionActor.props)
  implicit val timeout = Timeout(20.seconds)

  val corsResponseHeaders = List(
    `Access-Control-Allow-Origin`.*,
    `Access-Control-Request-Headers`,
    `Access-Control-Allow-Methods`(OPTIONS, POST, PUT, GET, DELETE),
  )

  def emailInteractionRoute: Route = {
    path("interaction" / "email") {
      cors {
        post {
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
                  }
                }
              }
          }
        }
      }
    }
  }

  def cors: Directive0 = {
    respondWithHeaders()
  }

}
