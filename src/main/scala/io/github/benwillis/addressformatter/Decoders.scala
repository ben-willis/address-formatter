package io.github.benwillis.addressformatter

import cats.syntax.either._
import io.circe.Decoder.Result
import io.circe.{Decoder, HCursor}
import io.circe.generic.semiauto.deriveDecoder
import io.github.benwillis.addressformatter.Models.{State, Template}

object Decoders {
  implicit val decodeTemplates: Decoder[Template] =
    deriveDecoder[Template].handleErrorWith(_ =>
      Decoder.decodeString.map(x => Template(address_template = Some(x))))

  implicit val decodeStates: Decoder[Seq[State]] =
    new Decoder[Seq[State]] {
      override def apply(c: HCursor): Result[Seq[State]] = {
        val thing = c.keys.getOrElse(Seq.empty).map { countryCode =>
          for {
            name <- c
              .downField(countryCode)
              .as[String]
              .orElse(c.downField(countryCode).downField("default").as[String])
            translations <- c
              .downField(countryCode)
              .keys
              .getOrElse(Seq.empty)
              .filter(_.contains("alt_"))
              .map(x => {
                val language = x.takeRight(2)
                c.downField(countryCode).downField(x).as[String].map(s => language -> s)
              })
              .foldLeft[Result[Map[String, String]]](Right(Map.empty))((acc, x) =>
                if (x.isLeft) Left(x.left.get) else acc.map(_ + x.right.get))
          } yield State(countryCode, name, translations)
        }
        thing.foldLeft[Result[Seq[State]]](Right(Seq.empty))((acc, x) =>
          if (x.isLeft) Left(x.left.get) else acc.map(_ :+ x.right.get))
      }
    }
}
