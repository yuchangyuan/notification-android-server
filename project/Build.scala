import sbt._

import Keys._
import AndroidKeys._

object General {
  val settings = Defaults.defaultSettings ++ Seq (
    name := "notification-android-server",
    version := "0.3-SNAPSHOT",
    versionCode := 0,
    scalaVersion := "2.9.2",
    platformName in Android := "android-10",
    javacOptions := Seq("-target", "1.6", "-source", "1.6"),
    resolvers += "local maven" at (
      Path.userHome.asFile.toURI.toURL + ".m2/repository"
    ),
    libraryDependencies := Seq(
      "net.databinder" % "dispatch-json_2.9.1" % "0.8.8",
      "org.java_websocket" % "Java-WebSocket" % "1.0.0-SNAPSHOT"
    )
  )

  val proguardSettings = Seq (
    useProguard in Android := true
  )

  lazy val fullAndroidSettings =
    General.settings ++
    AndroidProject.androidSettings ++
    TypedResources.settings ++
    proguardSettings ++
    AndroidManifestGenerator.settings ++
    AndroidMarketPublish.settings ++ Seq (
      keyalias in Android := "change-me",
      libraryDependencies += "org.scalatest" %% "scalatest" % "1.8" % "test"
    )
}

object AndroidBuild extends Build {
  lazy val main = Project (
    "notification-android-server",
    file("."),
    settings = General.fullAndroidSettings
  )

  lazy val tests = Project (
    "tests",
    file("tests"),
    settings = General.settings ++
               AndroidTest.androidSettings ++
               General.proguardSettings ++ Seq (
      name := "notificationAndroidServerTests"
    )
  ) dependsOn main
}
