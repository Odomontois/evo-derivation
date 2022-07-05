package evo.derivation.circe

import evo.derivation.Config
import evo.derivation.Discriminator
import evo.derivation.Embed
import evo.derivation.LazySummon.LazySummonByConfig
import evo.derivation.Rename
import evo.derivation.SnakeCase
import evo.derivation.circe.CheckData.Dictionary
import evo.derivation.circe.CheckData.Document
import evo.derivation.circe.CheckData.Mode
import evo.derivation.circe.CheckData.Person
import evo.derivation.circe.CheckData.User
import evo.derivation.circe.CheckData.*
import io.circe.Decoder
import io.circe.parser.*
import munit.FunSuite

import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import scala.CanEqual.derived
import scala.compiletime.testing.Error
import scala.compiletime.testing.typeCheckErrors

import CheckData.Hmm
import CheckData._
import io.circe.syntax.given

class EvoDecoderChecks extends FunSuite:
    test("Hmm is not deriveable") {
        assertEquals(
          List(s"could not derive $ImplicitTName, look's like $HmmTName is neither case class or enum"),
          typeCheckErrors("ConfiguredDecoder.derived[Hmm]").map(_.message),
        )
    }

    test("plain product") {
        assertEquals(decode[Person](personJson), Right(person))
    }

    test("complex product") {
        assertEquals(decode[Document](documentJson), Right(document))
    }

    test("plain coproduct") {
        assertEquals(decode[User](authorizedJson), Right(User.Authorized("ololo")))

        assertEquals(decode[User](anonymousJson), Right(User.Anonymous))
    }

    test("complex coproduct") {
        assertEquals(decode[Mode](readJson), Right(read))

        assertEquals(decode[Mode](writeJson), Right(write))
    }

    test("recursive product") {
        assertEquals(decode[Dictionary](dictionaryJson), Right(dictionary))
    }

    test("recursive coproduct") {
        assertEquals(decode[BinTree](binTreeJson), Right(binTree))
    }

class EvoEncoderChecks extends FunSuite:
    test("plain product") {
        assertEquals(parse(personJson), Right(person.asJson))
    }

object CheckData:
    val Package       = "evo.derivation.circe"
    val DecoderTName  = s"$Package.ConfiguredDecoder"
    val HmmTName      = s"$Package.CheckData.Hmm"
    val ImplicitTName = s"$DecoderTName[$HmmTName]"
    class Hmm derives Config

    case class Person(name: String, age: Int) derives Config, ConfiguredDecoder, ConfiguredEncoder

    val person = Person(name = "ololo", age = 11)

    val personJson = """{"name": "ololo", "age": 11}"""

    @SnakeCase case class Document(
        @Rename("documentId") id: UUID,
        issueDate: Instant,
        @Embed author: Person,
    ) derives Config,
          ConfiguredDecoder

    val uuid = UUID.fromString("68ede874-fb8a-11ec-a827-00155d6320ce").nn
    val date = Instant.now.nn

    val document = Document(id = uuid, issueDate = date, author = Person(name = "alala", age = 74))

    val documentJson = s"""{"documentId": "$uuid", "issue_date": "$date", "name": "alala", "age": 74}"""

    enum User derives Config, ConfiguredDecoder:
        case Authorized(login: String)
        case Anonymous

    val authorizedJson = s"""{"Authorized" : {"login": "ololo"}}"""

    val anonymousJson = s"""{"Anonymous" : {}}"""

    @Discriminator("mode")
    enum Mode derives Config, ConfiguredDecoder:
        @Rename("r") case Read(@Rename("b") bin: Boolean)
        @Rename("w") case Write(append: Boolean = false, bin: Boolean)

    val readJson = s"""{"mode": "r", "b": false}"""

    val read = Mode.Read(false)

    val write = Mode.Write(append = true, bin = true)

    val writeJson = s"""{"mode" : "w", "append":true, "bin": true}"""

    case class Dictionary(key: String, value: String, next: Option[Dictionary]) derives Config, ConfiguredDecoder

    val dictionaryJson = """{"key" : "a", "value" : "arbuz", "next" : {"key": "b", "value" : "baraban"}}"""

    val dictionary = Dictionary("a", "arbuz", Some(Dictionary("b", "baraban", None)))

    @Discriminator("kind") @SnakeCase
    enum BinTree derives Config, ConfiguredDecoder:
        case Branch(value: Int, left: BinTree, right: BinTree)
        case Nil

    val binTreeJson = """{
            "kind" : "branch", 
            "value": 1, 
            "left": {
              "kind" : "nil"
            }, 
            "right": {
              "kind" : "branch",
              "value" : 3,
              "left" : {
                "kind" : "nil"
              },
              "right" : {
                "kind" : "nil"
              }
            }
          }"""

    val binTree =
        BinTree.Branch(1, left = BinTree.Nil, right = BinTree.Branch(3, left = BinTree.Nil, right = BinTree.Nil))
