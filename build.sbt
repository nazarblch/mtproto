name := "mtproto"

version := "0.1"

scalaVersion := "2.12.7"

libraryDependencies ++= Seq(
  "org.scalaz" %% "scalaz-zio" % "1.0-RC5",
  "org.scalaz" %% "scalaz-zio-interop" % "0.5.0",
  "org.scodec" %% "scodec-core" % "1.11.3",
  "dev.zio" %% "zio-nio" % "1.0.0-RC6"
)
