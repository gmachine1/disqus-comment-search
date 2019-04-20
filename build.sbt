val ScalatraVersion = "2.6.5"

organization := "com.gmachine1729"

name := "Disqus Comment Search"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.12.6"

resolvers += Classpaths.typesafeReleases

libraryDependencies ++= Seq(
  "org.scalatra" %% "scalatra" % ScalatraVersion,
  "org.scalatra" %% "scalatra-scalatest" % ScalatraVersion % "test",
  "ch.qos.logback" % "logback-classic" % "1.2.3" % "runtime",
  "org.eclipse.jetty" % "jetty-webapp" % "9.4.9.v20180320" % "container",
  "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided",
  "org.scalaj" %% "scalaj-http" % "2.4.1",
  "com.lihaoyi" %% "upickle" % "0.7.1",
  "com.lihaoyi" %% "scalatags" % "0.6.7",
  "org.scalatra" %% "scalatra-scalate" % ScalatraVersion
)

javaOptions ++= Seq(
  "-Xdebug",
  "-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
)

enablePlugins(SbtTwirl)
enablePlugins(ScalatraPlugin)
enablePlugins(JettyPlugin)