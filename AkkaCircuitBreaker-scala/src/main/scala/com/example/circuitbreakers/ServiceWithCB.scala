package com.example.circuitbreakers

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.pattern.CircuitBreaker

import scala.concurrent.{ExecutionContext, Future, TimeoutException}
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object ServiceWithCB {}
class ServiceWithCB(implicit system: ActorSystem[Nothing]) extends Service {

  implicit val exectitionContext: ExecutionContext = system.executionContext

  val classicSystem = akka.actor.ActorSystem("ClassicToTypedSystem")

  val breaker =
    new CircuitBreaker(
      scheduler = classicSystem.scheduler,
      maxFailures = 1,
      callTimeout = 2.seconds,
      resetTimeout = 10.seconds
    ).onOpen(notifyMe("Open"))
      .onClose(notifyMe("Closed"))
      .onHalfOpen(notifyMe("Half Open"))

  private def notifyMe(state: String): Unit = println(
    s"My CircuitBreaker is now $state"
  )

  def behavior(): Behavior[Service.Message] =
    Behaviors.receive { (ctx, message) =>
      val self = ctx.self
      message match {
        case Service.Request(replyTo) =>
          val response: Future[Service.Response.type] =
            breaker.withCircuitBreaker(Future(callWebService()))
          response.onComplete(t => {
            t match {
              case Success(s) => replyTo ! s
              case Failure(_: TimeoutException) =>
                replyTo ! Service.TimeoutFailure
              case Failure(exception: Exception) =>
                // println(s"exception $exception")
                replyTo ! Service.Failure(exception)
            }
          })
          Behaviors.same
      }
    }

}
