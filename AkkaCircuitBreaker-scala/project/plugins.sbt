addSbtPlugin("com.github.sbt" % "sbt-git"              % "2.0.0")
addSbtPlugin("com.eed3si9n"   % "sbt-assembly"         % "1.2.0")
addSbtPlugin("com.github.sbt" % "sbt-native-packager"  % "1.9.11")
addSbtPlugin("com.github.sbt" % "sbt-release"          % "1.1.0")

ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
