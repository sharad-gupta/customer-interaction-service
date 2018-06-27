package org.manish.dentalclinic.service

package CaptchaServiceComponent {

  import java.util.concurrent.TimeUnit

  import akka.actor.{Actor, ActorSystem, Props}
  import akka.http.scaladsl.Http
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
  import akka.http.scaladsl.marshalling.Marshal
  import akka.http.scaladsl.model.{HttpMethods, HttpRequest, RequestEntity}
  import akka.http.scaladsl.unmarshalling.Unmarshal
  import akka.stream.ActorMaterializer
  import akka.stream.scaladsl.{Sink, Source}
  import akka.util.Timeout
  import org.manish.dentalclinic.service.EmailInteractionServiceComponent.ErrorCodes.ErrorCodes
  import org.manish.dentalclinic.util.LogUtils
  import spray.json.DefaultJsonProtocol

  import scala.concurrent.Await
  import scala.concurrent.duration._
  import scala.util.{Failure, Success, Try}

  case class RecaptchaRequest(secret: String, response: String, remoteip: String, attempt: Int, delay: Int)

  case class RecaptchaResponse(success: Boolean)

  case class DownstreamApiError(message: String, errorCode: ErrorCodes)

  trait CaptchaJson extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val recaptchaRequestJson = jsonFormat5(RecaptchaRequest)
    implicit val recaptchaResponseJson = jsonFormat(RecaptchaResponse, "success")
  }

  object CaptchaActor {
    def props: Props = {
      Props(classOf[CaptchaActor])
    }
  }

  class CaptchaActor extends Actor with LogUtils with CaptchaJson {
    val maxAttempt:Int = 3

    implicit val system = ActorSystem()
    implicit val mat = ActorMaterializer()
    implicit val ec = system.dispatcher
    implicit val timeout = Timeout(10.seconds)

    override def receive: Receive = {
      case request: RecaptchaRequest => {
        logger.info("Captcha received " + request)

        val httpClient = Http().outgoingConnectionHttps(host = "www.google.com")
        val recaptchaUrl = "/recaptcha/api/siteverify?secret=" + request.secret + "&response=" + request.response + "&remoteip="
        val flowPost = for {
          requestEntity <- Marshal(request).to[RequestEntity]
          response <-
            Source.single(HttpRequest(
              method = HttpMethods.POST,
              uri = recaptchaUrl,
              entity = requestEntity))
            .via(httpClient)
            .mapAsync(1)(responseFuture => Unmarshal(responseFuture.entity).to[RecaptchaResponse])
            .runWith(Sink.head)
        } yield response

        Try {
          Await.result(flowPost, 5 seconds)
        } match {
          case Success(data) => {
            logger.info(s"Captcha Request Validation Done --> $data")
            sender ! data.success
          }
          case Failure(e) => {
            logger.error(s"Error raised retrying --> initial is " + request.attempt, e)
            if (request.attempt < maxAttempt) {
              context.system.scheduler.scheduleOnce(Duration.create(request.delay, TimeUnit.SECONDS), self,
                request.copy(attempt = request.attempt + 1, delay = request.delay + 2))
              throw e
            } else {
              logger.info("Retry Attempt Exhausted.. ")
            }
          }
        }
      }
    }
  }

}
