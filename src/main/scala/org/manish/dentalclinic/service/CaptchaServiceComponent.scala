package org.manish.dentalclinic.service

package CaptchaServiceComponent {

  import akka.actor.{Actor, ActorSystem, Props}
  import akka.http.scaladsl.Http
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
  import akka.http.scaladsl.marshalling.Marshal
  import akka.http.scaladsl.model.{HttpMethods, HttpRequest, RequestEntity}
  import akka.http.scaladsl.unmarshalling.Unmarshal
  import akka.stream.ActorMaterializer
  import akka.stream.scaladsl.{Sink, Source}
  import akka.util.Timeout
  import org.manish.dentalclinic.util.LogUtils
  import spray.json.DefaultJsonProtocol

  import scala.concurrent.Await
  import scala.concurrent.duration._

  case class RecaptchaRequest(secret: String, response: String, remoteip: String)

  case class RecaptchaResponse(success: Boolean, errorcodes: List[String])

  trait CaptchaJson extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val recaptchaRequestJson = jsonFormat3(RecaptchaRequest)
    implicit val recaptchaResponseJson = jsonFormat(RecaptchaResponse, "success", "error-codes")
  }

  object CaptchaActor {
    def props: Props = {
      Props(classOf[CaptchaActor])
    }
  }

  class CaptchaActor extends Actor with LogUtils with CaptchaJson {

    implicit val system = ActorSystem()
    implicit val mat = ActorMaterializer()
    implicit val ec = system.dispatcher
    implicit val timeout = Timeout(20.seconds)

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
            .mapAsync(5)(responseFuture => Unmarshal(responseFuture.entity).to[RecaptchaResponse])
            .runWith(Sink.head)
        } yield response

        val resultPost:RecaptchaResponse = Await.result(flowPost, 5 seconds)

        logger.info(s"Captcha Request Validation Done --> $resultPost")

        sender ! resultPost.success
      }
    }
  }

}
