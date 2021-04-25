package io.github.benwillis.addressformatter

import cats.syntax.either._
import io.circe.generic.auto._
import io.circe.yaml.parser
import io.circe.{Decoder, HCursor}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.io.{File, InputStreamReader}
import scala.util.matching.Regex

class AddressFormatterTest extends AnyFunSpec with Matchers {

  val testCaseFilenameRegex: Regex = """([a-z]{2})\.yaml""".r
  val testCaseCountries: Seq[String] =
    new File(getClass.getResource("/countries").getPath).listFiles
      .map(_.getName)
      .collect {
        case testCaseFilenameRegex(countryCode) => countryCode
      }

  implicit val decodeStrings: Decoder[String] = new Decoder[String] {
    final def apply(c: HCursor): Decoder.Result[String] = {
      Decoder.decodeString.tryDecode(c) orElse Decoder.decodeInt
        .tryDecode(c)
        .map(_.toString)
    }
  }

  describe("Address formatter") {
    val addressFormatter = new AddressFormatter
    testCaseCountries.foreach { countryCode =>
      it(s"should format $countryCode addresses") {
        val testCaseReader =
          new InputStreamReader(getClass.getResourceAsStream(s"/countries/$countryCode.yaml"), "UTF8")
        val countryTestCases = parser.parseDocuments(testCaseReader).toList.map(_.flatMap(_.as[TestCase]))
        testCaseReader.close()
        countryTestCases.foreach {
          case Right(testCase) =>
            withClue(testCase.description.getOrElse("")) {
              addressFormatter.format(testCase.components) should equal(testCase.expected)
            }
          case Left(ex) => throw ex
        }
      }
    }
  }

}
