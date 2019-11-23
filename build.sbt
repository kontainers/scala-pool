name := "scala-pool"

organization := "io.kontainers"
startYear := Some(2015)

version := "0.5.0-SNAPSHOT"

scalaVersion := "2.13.1"

crossScalaVersions := Seq("2.11.12", "2.12.10")

libraryDependencies ++= Seq(
  "org.specs2" %% "specs2-core" % "4.8.1" % "test")

resolvers ++= Seq(
  "snapshots"           at "https://oss.sonatype.org/content/repositories/snapshots",
  "releases"            at "https://oss.sonatype.org/content/repositories/releases",
  "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/")

scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions")

scalacOptions in Test ++= Seq("-Yrangepos")

fork := true

publishTo := Some(
  if (isSnapshot.value)
    Opts.resolver.sonatypeSnapshots
  else
    Opts.resolver.sonatypeStaging)

pomIncludeRepository := { _ => false }

publishArtifact in Test := false

import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import scalariform.formatter.preferences._

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(DoubleIndentConstructorArguments, true)
  .setPreference(PlaceScaladocAsterisksBeneathSecondAsterisk, true)

enablePlugins(SiteScaladocPlugin)
enablePlugins(GhpagesPlugin)
git.remoteRepo := s"""https://${sys.env.getOrElse("GH_TOKEN", "NULL")}@github.com/kontainers/scala-pool.git"""
