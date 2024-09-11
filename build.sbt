import sbt.librarymanagement.For3Use2_13

name           := "derivation"
publish / skip := true

ThisBuild / scalaVersion := Version.scala

ThisBuild / organization := "com.evolution"

ThisBuild / startYear := Some(2022)

ThisBuild / organizationName := "Evolution"

ThisBuild / organizationHomepage := Some(url("https://evolution.com"))

ThisBuild / versionScheme := Some("early-semver")

testFrameworks += new TestFramework("munit.Framework")

val scala3Settings = scalacOptions ++= Vector(
  "-Yexplicit-nulls",
  "-encoding",
  "utf-8",
  "-Yshow-suppressed-errors",
)

val testDependencies = libraryDependencies ++= Vector(
  "org.scalameta" %% "munit" % Version.munit % Test,
)

lazy val publishSettings = Vector(
  homepage               := Some(url("https://github.com/evolution-gaming/derivation")),
  developers             := List(
    Developer(
      "Odomontois",
      "Oleg Nizhnik",
      "onizhnikov@evolution.com",
      url("https://github.com/Odomontois"),
    ),
    Developer(
      "FunFunFine",
      "Anton Voitsishevskii",
      "avoitsishevskii@evolution.com",
      url("https://github.com/FunFunFine"),
    ),
  ),
  publishMavenStyle      := true,
  Test / publishArtifact := false,
  versionScheme          := Some("early-semver"),
  description            := "A derivation library for scala 3 with annotation based configuration.",
  scmInfo                := Some(
    ScmInfo(
      url("https://github.com/evolution-gaming/derivation"),
      "git@github.com:evolution-gaming/derivation.git",
    ),
  ),
  licenses += ("MIT", url("https://opensource.org/licenses/MIT")),
  releaseCrossBuild      := true,
  publishTo              := Some(Resolver.evolutionReleases),
)

val defaultSettings = testDependencies ++ scala3Settings ++ publishSettings

val modules = file("modules")

lazy val derivation = project
    .in(modules / "core")
    .settings(
      name := "derivation-core",
    )
    .settings(defaultSettings)

lazy val tests = project
    .in(modules / "tests")
    .settings(
      publish / skip                    := true,
      defaultSettings,
      libraryDependencies += "io.circe" %% "circe-parser" % Version.circe % Test,
    )
    .dependsOn(derivation, circe, tapir, cats, playJson)

lazy val circe = project
    .in(modules / "circe")
    .settings(
      name                              := "derivation-circe",
      libraryDependencies += "io.circe" %% "circe-core" % Version.circe,
      defaultSettings,
    )
    .dependsOn(derivation)

// circe codecs which for compatibility uses circe-core_2.13 artifact
lazy val `circe-compat_213` = project
    .in(modules / "circe")
    .settings(
      name                              := "derivation-circe-compat213",
      target                            := (file("modules") / "circe" / "target-compat213" / "jvm").getAbsoluteFile,
      libraryDependencies += "io.circe" %% "circe-core" % Version.circe cross For3Use2_13(),
      defaultSettings,
    )
    .dependsOn(derivation)

lazy val tapir = project
    .in(modules / "tapir")
    .settings(
      name                                                 := "derivation-tapir",
      libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-core" % Version.tapir,
      defaultSettings,
    )
    .dependsOn(derivation)

lazy val cats = project
    .in(modules / "cats")
    .settings(
      name                                   := "derivation-cats",
      libraryDependencies += "org.typelevel" %% "cats-kernel" % Version.cats,
      defaultSettings,
    )
    .dependsOn(derivation)

lazy val playJson = project
    .in(modules / "playJson")
    .settings(
      name                                       := "derivation-play-json",
      libraryDependencies += "com.typesafe.play" %% "play-json" % Version.playJson cross CrossVersion.for3Use2_13,
      defaultSettings,
    )
    .dependsOn(derivation)
