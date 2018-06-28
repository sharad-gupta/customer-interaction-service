package org.manish.dentalclinic.service

import akka.actor.{Actor, Props}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.HttpRequest
import org.manish.dentalclinic.util.LogUtils
import spray.json.DefaultJsonProtocol

import scala.concurrent.duration._

package EmailInteractionServiceComponent {

  import akka.actor.ActorSystem
  import akka.pattern.ask
  import akka.stream.ActorMaterializer
  import akka.util.Timeout
  import com.typesafe.config.{Config, ConfigFactory}
  import org.manish.dentalclinic.service.CaptchaServiceComponent.{CaptchaActor, DownstreamApiError, RecaptchaRequest}
  import org.manish.dentalclinic.service.EmailInteractionServiceComponent.ErrorCodes.ErrorCodes
  import spray.json.{DeserializationException, JsString, JsValue, RootJsonFormat}

  import scala.concurrent.Await
  import scala.util.{Failure, Success, Try}

  case class EmailSummary(name: String, emailAddress: String, phoneNumber: String, transcript: String, response: String)

  case class InteractionStatus(message: String, code: ErrorCodes)

  object ErrorCodesFormat extends RootJsonFormat[ErrorCodes] {
    def write(obj: ErrorCodes): JsValue = JsString(obj.toString)

    def read(json: JsValue): ErrorCodes = json match {
      case JsString(str) => ErrorCodes.withName(str)
      case _ => throw new DeserializationException("Enum string expected")
    }
  }

  object ErrorCodes extends Enumeration {
    type ErrorCodes = Value
    val E20012, E20015, E20018 = Value
  }

  object EmailInteractionActor {
    def props: Props = Props(classOf[EmailInteractionActor])
  }

  trait EmailInteractionJson extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val errorCodesJson = ErrorCodesFormat
    implicit val emailInteractionJson = jsonFormat5(EmailSummary)
    implicit val interactionStatusJson = jsonFormat2(InteractionStatus)
  }

  class EmailInteractionActor extends Actor with LogUtils with EmailInteractionJson {

    implicit val system = ActorSystem()
    implicit val mat = ActorMaterializer()
    implicit val ec = system.dispatcher
    implicit val timeout = Timeout(10.seconds)

    val actorRef = system.actorOf(CaptchaActor.props)

    def config: Config = ConfigFactory.load()

    override def receive: Receive = {
      case summary: EmailSummary => {
        val secret = config.getString("google.recaptcha.secret")

        logger.info("Email interaction summary received " + summary)
        val ret = actorRef ? RecaptchaRequest(secret.trim, summary.response.trim, "", 1, 1)
        Try {
          Await.result(ret, timeout duration)
        } match {
          case Success(flag) => {
            if (flag == true) {
              logger.info("Successfully posting the message to customer")
              sender ! InteractionStatus("Thank you for contacting us, will revert back shortly", ErrorCodes.E20012)
            } else {
              logger.info("Failure raised")
              sender ! InteractionStatus("Please check the data, retry", ErrorCodes.E20015)
            }
          }
          case Failure(error) => {
            logger.error("Failure observed ", error)
            sender ! InteractionStatus("Downstream Api Error", ErrorCodes.E20018)
          }
        }
      }
    }
  }

}
