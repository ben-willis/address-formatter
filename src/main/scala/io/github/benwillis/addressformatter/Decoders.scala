package io.github.benwillis.addressformatter

import io.circe.Decoder.Result
import io.circe.{Decoder, DecodingFailure, HCursor}
import io.circe.generic.semiauto.deriveDecoder
import io.github.benwillis.addressformatter.Models.{State, Template}
import cats.implicits._

object Decoders {
  implicit val decodeTemplates: Decoder[Template] =
    deriveDecoder[Template] or Decoder.decodeString.map(str => Template(address_template = Some(str)))

  implicit val decodeStates: Decoder[Seq[State]] = new Decoder[Seq[State]] {
    override def apply(c: HCursor): Result[Seq[State]] = {
      c.keys.toList.flatten.traverse { countryCode =>
        for {
          name <- c.downField(countryCode).as[String] orElse c.downField(countryCode).downField("default").as[String]
          languages = c.downField(countryCode).keys.toList.flatten.filter(_.contains("alt_")).map(_.takeRight(2))
          translations <- languages.traverse { language =>
            c.downField(countryCode).downField(s"alt_$language").as[String].map(language -> _)
          }
        } yield State(countryCode, name, translations.toMap)
      }
    }
  }
}
