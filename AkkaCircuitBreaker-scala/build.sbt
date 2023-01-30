import com.typesafe.sbt.packager.docker.DockerChmodType
import com.typesafe.sbt.packager.docker.DockerPermissionStrategy
import com.typesafe.sbt.packager.docker.DockerVersion
import sbt.Test

import java.time.Year
import java.util.Locale

// *****************************************************************************
// Project
// *****************************************************************************

name := """AkkaCircuitBreaker-scala"""

lazy val acdcProject = project
  .in(file("."))
  .enablePlugins(
    GitVersioning,
    DockerPlugin,
    JavaAppPackaging,
    JUnitXmlReportPlugin
  )
  .settings(
    settings,
    libraryDependencies ++= library.akka,
    libraryDependencies ++= library.log,
    libraryDependencies ++= library.testDeps,
    libraryDependencies ++= Seq(library.jimfs),
    Test / parallelExecution := false
  )

// *****************************************************************************
// Dependency Settings
// *****************************************************************************

lazy val library = new {

  object version {
    val akka = "2.6.18"
    val akkaHttp = "10.2.7"
    val joseJwt = "0.7.8"
    val circe = "0.14.1"
    val tapir = "0.18.3"
    val scalaCsv = "1.3.10"
    val scala213 = "2.13.3"
    val scalaTest = "3.2.10"
    val scalaCheck = "1.15.4"
    val log4j = "2.17.0"
    val typesafeConfig = "1.4.1"
    val disruptor = "3.4.4"
    val acdcConnectors = "1.5.4"
    val cassandraDriver = "4.13.0"
    val cassandraUnit = "4.3.1.0"
    val alpakka = "3.0.4"
    val guava = "25.0-jre" // needed for embedded cassandra to work correctly
    val jimfs = "1.2"
  }

  val scalaCsv = "com.github.tototoshi" %% "scala-csv" % version.scalaCsv
  val jimfs = "com.google.jimfs" % "jimfs" % version.jimfs
  val guava = "com.google.guava" % "guava" % version.guava % Test

  val akka: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %% "akka-actor-typed" % version.akka,
    "com.typesafe.akka" %% "akka-stream" % version.akka,
    "com.lightbend.akka" %% "akka-stream-alpakka-json-streaming" % version.alpakka,
    "com.lightbend.akka" %% "akka-stream-alpakka-file" % version.alpakka,
    "com.typesafe.akka" %% "akka-http" % version.akkaHttp,
    "com.typesafe.akka" %% "akka-http-core" % version.akkaHttp,
    "com.typesafe.akka" %% "akka-http2-support" % version.akkaHttp,
    "com.typesafe.akka" %% "akka-http-spray-json" % version.akkaHttp
  )

  val testDeps: Seq[ModuleID] = Seq(
    "org.scalacheck" %% "scalacheck" % version.scalaCheck % Test,
    "org.scalatest" %% "scalatest" % version.scalaTest % Test,
    "com.typesafe.akka" %% "akka-testkit" % version.akka % Test,
    "com.typesafe.akka" %% "akka-http-testkit" % version.akkaHttp % Test,
    "com.typesafe.akka" %% "akka-multi-node-testkit" % "2.6.18" % Test,
    "com.typesafe.akka" %% "akka-stream-testkit" % "2.6.18" % Test
  )

  val log: Seq[ModuleID] = Seq(
    "com.typesafe" % "config" % version.typesafeConfig,
    "org.apache.logging.log4j" % "log4j-core" % version.log4j,
    "org.apache.logging.log4j" % "log4j-api" % version.log4j,
    "com.lmax" % "disruptor" % version.disruptor,
    "com.typesafe.akka" %% "akka-slf4j" % version.akka,
    "org.apache.logging.log4j" % "log4j-slf4j-impl" % version.log4j
  )
}

// *****************************************************************************
// Settings
// *****************************************************************************

lazy val settings =
  commonSettings ++
    gitSettings ++
    dockerSettings ++
    publishSettings ++
    releaseSettings ++
    resolversSettings

lazy val commonSettings = Seq(
  scalaVersion := library.version.scala213,
  credentials += credentialsProvider(),
  version.~=(_.replaceFirst("SNAPSHOT", s"${branchNameDash}SNAPSHOT"))
)

val branchNameDash = Option(System.getenv("BRANCH_NAME"))
  .filter(name => !name.toLowerCase.equals("master"))
  .map(_ + "-")
  .getOrElse("")

lazy val gitSettings = Seq(
  git.useGitDescribe := true
)

// -----------------------------------------------------------------------------
// Aliases
// -----------------------------------------------------------------------------

// If you have it enabled, make sure to use QAWithIt instead
addCommandAlias(
  "qa",
  "simpleQA"
)

// -----------------------------------------------------------------------------
// Docker settings
// -----------------------------------------------------------------------------

lazy val dockerSettings = Seq(
  dockerBaseImage := "registry-acdc.tools.msi.audi.com/ubuntu:20.04-jre11-1.1.0",
  dockerPermissionStrategy := DockerPermissionStrategy.Run,
  dockerVersion := Some(DockerVersion(0, 0, 0, None)),
  Docker / daemonUserUid := None,
  Docker / daemonUser := "acdc_nobody",
  dockerAdditionalPermissions += (DockerChmodType.Custom(
    "+x"
  ), s"${(Docker / defaultLinuxInstallLocation).value}/bin/${executableScriptName.value}")
)

lazy val dockerRelease: ReleaseStep = { st: State =>
  val extracted = Project.extract(st)
  val ref = extracted.get(thisProjectRef)
  extracted.runAggregated(ref / Docker / stage, st)
}

// Workaround for using 'sbt docker:stage' without the Docker daemon.
dockerVersion := Some(DockerVersion(0, 0, 0, None))

// -----------------------------------------------------------------------------
// publish settings
// -----------------------------------------------------------------------------

import scala.util.Properties.envOrElse
import scala.util.Properties.envOrNone

val nexusHttpMethod = envOrElse("NEXUS_HTTP_METHOD", "https")
val nexusUrl = envOrElse("NEXUS_URL", "nexus-acdc.tools.msi.audi.com")
val nexusRepositoryPath = envOrElse(
  "NEXUS_REPOSITORY_PATH",
  "repository/acdc-snapshots/"
)
val nexusColonPort = envOrNone("NEXUS_PORT").map(":" + _).getOrElse("")
val nexusUsername = System.getenv("NEXUS_USERNAME")
val nexusPassword = System.getenv("NEXUS_PASSWORD")
val nexusAddress = s"$nexusHttpMethod://$nexusUrl$nexusColonPort"
val publishRepository = MavenRepository(
  "Sonatype Nexus Repository Manager",
  s"$nexusAddress/$nexusRepositoryPath"
)

def credentialsProvider(): Credentials = {
  val fileExists = (Path.userHome / ".sbt" / ".credentials").exists()

  if (fileExists) {
    Credentials(Path.userHome / ".sbt" / ".credentials")
  } else {
    Credentials(
      "Sonatype Nexus Repository Manager",
      nexusUrl,
      nexusUsername,
      nexusPassword
    )
  }
}

def isSnapshot: Boolean = nexusRepositoryPath.toLowerCase.contains("snapshot")

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  Test / publishArtifact := false,
  publishTo := Some(publishRepository)
)

// -----------------------------------------------------------------------------
// Release settings
// -----------------------------------------------------------------------------
import sbtrelease.ReleasePlugin.autoImport._
import ReleaseTransformations._

releaseNextVersion := { ver =>
  import sbtrelease._
  Version(ver)
    .map(_.bumpBugfix.asSnapshot.string)
    .getOrElse(versionFormatError(ver))
}

lazy val releaseSettings = Seq(
  releaseProcess := {
    if (isSnapshot) {
      Seq[ReleaseStep](
        inquireVersions,
        publishArtifacts
      )
    } else {
      Seq[ReleaseStep](
        checkSnapshotDependencies,
        inquireVersions,
        setReleaseVersion,
        commitReleaseVersion,
        tagRelease,
        dockerRelease,
        publishArtifacts,
        setNextVersion,
        commitNextVersion,
        pushChanges
      )
    }
  }
)

// -----------------------------------------------------------------------------
// resolvers settings
// -----------------------------------------------------------------------------

lazy val resolversSettings = Seq(
  resolvers += "acdc-nexus" at s"$nexusAddress/repository/maven-public/"
)
