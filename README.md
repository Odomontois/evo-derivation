# Derivation

 [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
 [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.evolution/derivation-core_3/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.evolution/derivation-core_3)
[![Sonatype Nexus (Snapshots) badge](https://img.shields.io/nexus/s/https/s01.oss.sonatype.org/com.evolution/derivation-core_3.svg)](https://s01.oss.sonatype.org/content/repositories/snapshots/com/evolution/derivation-core_3/)
 [![Build](https://github.com/evolution-gaming/derivation/actions/workflows/scala.yml/badge.svg)](https://github.com/evolution-gaming/derivation/actions/workflows/scala.yml)

A derivation library for scala 3 with annotation based configuration.

## Usage

This library os being published to the macen central. Add library to your project as

```sbt
addSbtPlugin("com.evolution" % "sbt-artifactory-plugin" % "0.0.2")

libraryDependencies += "com.evolution" %% "derivation-circe" % "{version}"
```

Finally, define your own type, which derives circe instances


```scala
import evo.derivation.*
import evo.derivation.circe.*
import evo.derivation.config.Config
import java.util.UUID

@SnakeCase
@Discriminator("type")
enum User derives Config, EvoCodec:
    case AuthorizedClient(@Rename("client_id") id: UUID, name: String)
    case Anonymous
```


## Annotation reference

### `@SnakeCase`
 Transforms all the constructor and\or field names to the snake case

### `@Discriminator(name: String)`
 Defines discriminator field for serialization of `sealed trait` or `enum`

### `@Rename(name: String)`

 Renames single `case class` field or `enum` constructor

### `@Embed`
Write\Reads all inner fields of some field on the upper level during serialization


