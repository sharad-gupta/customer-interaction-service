package org.manish.dentalclinic

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import org.manish.dentalclinic.controller.EmailInteractionController
import org.manish.dentalclinic.util.LogUtils

object EmailInteractionApplication extends App with LogUtils {
  implicit val system = ActorSystem("Email-Interaction")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val routes: Route = new EmailInteractionController().emailInteractionRoute

  Http().bindAndHandle(routes, "0.0.0.0", 9098)
  logger.info("Server started successfully on port 9098")
}
