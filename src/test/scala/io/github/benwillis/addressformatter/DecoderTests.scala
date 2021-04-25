package io.github.benwillis.addressformatter

import io.circe.Json
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import io.circe._
import io.circe.yaml.parser._
import io.github.benwillis.addressformatter.Models.State

class DecoderTests extends AnyFunSpec with Matchers {
  describe("StateDecoder") {
    it("should decode state codes") {
      val stateCodes = """AO:
                         |    BGO: Bengo
                         |    BGU: Benguela
                         |AR:
                         |    A: Salta
                         |    B: Buenos Aires
                         |    C:
                         |      default: Ciudad Autonoma de Buenos Aires
                         |      alt_en: Autonomous City of Buenos Aires""".stripMargin

      import Decoders._

      val states = parse(stateCodes).map(_.hcursor).flatMap(_.as[Map[String, Seq[State]]])

      states shouldBe Right(
        Map(
          "AO" -> Seq(
            State("BGO", "Bengo", Map.empty),
            State("BGU", "Benguela", Map.empty)
          ),
          "AR" -> Seq(
            State("A", "Salta", Map.empty),
            State("B", "Buenos Aires", Map.empty),
            State("C", "Ciudad Autonoma de Buenos Aires", Map("en" -> "Autonomous City of Buenos Aires"))
          )
        ))
    }
  }
}
