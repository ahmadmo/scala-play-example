name := "bama_api_demo"

version := "1.0"

lazy val `bama_api_demo` = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  cache, ws, specs2 % Test,
  "com.typesafe.play" %% "play-slick" % "2.1.0",
  "com.typesafe.play" %% "play-slick-evolutions" % "2.1.0",
  "com.enragedginger" %% "akka-quartz-scheduler" % "1.6.0-akka-2.4.x",
  "com.h2database" % "h2" % "1.4.194",
  "ch.qos.logback" % "logback-classic" % "1.2.2",
  "ch.qos.logback" % "logback-core" % "1.2.2",
  "io.jsonwebtoken" % "jjwt" % "0.7.0",
  "org.mindrot" % "jbcrypt" % "0.4",
  "com.chuusai" %% "shapeless" % "2.3.2",
  compilerPlugin("org.scalamacros" %% "paradise" % "2.1.0" cross CrossVersion.full)
)

unmanagedResourceDirectories in Test += baseDirectory(_ / "target/web/public/test").value

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

scalacOptions ++= Seq("-feature", "-language:implicitConversions", "-language:postfixOps")
