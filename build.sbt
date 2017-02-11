name := """play-react-example"""
organization := "com.mwa"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.8"

libraryDependencies += filters
libraryDependencies +=  "com.typesafe.akka" %% "akka-actor" % "2.4.16"
libraryDependencies +=  "ch.qos.logback"    % "logback-classic" % "1.0.13"
