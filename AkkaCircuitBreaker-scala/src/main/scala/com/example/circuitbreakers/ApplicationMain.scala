package com.example.circuitbreakers

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}
import scala.io.StdIn
import scala.language.postfixOps
import scala.util.Random

object ApplicationMain extends App {

  // Create the system and service
  lazy implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "MySystem"
  )

  implicit val executionContext:ExecutionContext = system.executionContext

  val service = new ServiceWithCB
  // val service  = new ServiceWithoutCB

  // Create the user actors
  val userCount = 10
  (1 to userCount).foreach(createUser)

  // Let this run until the user wishes to stop
  println("System running, press enter to shutdown")
  StdIn.readLine()

  // We're done, shutdown
  Await.result(system.whenTerminated, 3.seconds)


  private def createUser(i: Int): Unit = {
    val serviceActor = system.systemActorOf(
      behavior = service.behavior,
      name = "service-actor" + Random.nextInt()
    )
    val user:UserActor = new UserActor(service = serviceActor)
    val userActor = system.systemActorOf(
      behavior = user.behavior(delay = i second),
      name = "user-actor" + Random.nextInt()
    )
    system.scheduler.scheduleOnce(i.seconds, () => userActor ! Service.Start)
  }
}
