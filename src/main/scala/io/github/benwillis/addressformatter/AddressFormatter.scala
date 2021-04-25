package io.github.benwillis.addressformatter

import cats.syntax.either._
import io.circe.Decoder.Result
import io.circe.generic.auto._
import io.circe.generic.semiauto._
import io.circe.yaml.parser
import io.circe.{Decoder, HCursor}
import io.github.benwillis.addressformatter.Models.{Component, State, Template}

import java.io.InputStreamReader
import java.util.regex.Pattern

class AddressFormatter {

  import Decoders._

  val templates: Map[String, Template]  = decodeYaml[Map[String, Template]]("/countries/worldwide.yaml")
  val components: Seq[Component]        = decodeYamlDocuments[Component]("/components.yaml")
  val states: Map[String, Seq[State]]   = decodeYaml[Map[String, Seq[State]]]("/state_codes.yaml")
  val counties: Map[String, Seq[State]] = decodeYaml[Map[String, Seq[State]]]("/county_codes.yaml")

  private def decodeYaml[T: Decoder](path: String): T = {
    val fileReader = new InputStreamReader(getClass.getResourceAsStream(path), "UTF8")
    val decoded    = parser.parse(fileReader).flatMap(_.as[T])
    fileReader.close()
    decoded match {
      case Left(ex) => throw ex
      case Right(x) => x
    }
  }

  private def decodeYamlDocuments[T: Decoder](path: String): Seq[T] = {
    val fileReader = new InputStreamReader(getClass.getResourceAsStream(path), "UTF8")
    val decoded    = parser.parseDocuments(fileReader).map(_.flatMap(_.as[T])).force
    fileReader.close()
    decoded.map {
      case Left(ex) => throw ex
      case Right(x) => x
    }
  }

  type AddressMap = Map[String, String]

  def format(addressMap: AddressMap, countryCode: Option[String] = None): String = {
    val augmentedAddress = Seq[Map[String, String] => Map[String, String]](
      _ ++ countryCode.map("country_code" -> _),
      determineCountryCode,
      setAliases,
      sanityCleaning,
      applyReplacements,
      addCodes,
      addAttention
    ).reduce(_.andThen(_))(addressMap)

    val rawAddressString = renderTemplate(augmentedAddress)

    val postformatReplacements = augmentedAddress
      .get("country_code")
      .flatMap(templates.get)
      .flatMap(_.postformat_replace)
      .getOrElse(Seq.empty)
    (postformatReplace(postformatReplacements) _)
      .andThen(cleanAddress)(rawAddressString)
  }

  def determineCountryCode(addressMap: AddressMap): AddressMap = {
    val template =
      addressMap.get("country_code").map(_.toUpperCase).flatMap(templates.get)

    val extraTerms =
      template
        .flatMap(_.use_country)
        .orElse(addressMap.get("country_code").map(_.toUpperCase))
        .map("country_code" -> _) ++
        template
          .flatMap(_.change_country)
          .map("country" -> _.replaceAll("""\$state""", addressMap.getOrElse("state", ""))) ++
        template.flatMap(_.add_component).map(_.split("=", 2).toSeq).collect {
          case Seq(component, value) => (component -> value)
        }

    val nlEdgeCasesExtraTerm = addressMap.get("state") match {
      case Some(x) if x matches """Curaçao""" =>
        Seq("country_code" -> "CW", "country" -> x)
      case Some(x) if x matches """(?i)^sint maarten""" =>
        Seq("country_code" -> "SX", "country" -> "Sint Maarten")
      case Some(x) if x matches """(?i)^Aruba""" =>
        Seq("country_code" -> "AR", "country" -> "Aruba")
      case _ => Seq.empty
    }

    addressMap ++ extraTerms ++ nlEdgeCasesExtraTerm
  }

  def setAliases(addressMap: AddressMap): AddressMap = {
    components.foldLeft(addressMap) { (accAddress, component) =>
      {
        val aliasInAddress = component.aliases.getOrElse(Seq.empty) intersect addressMap.keys.toSeq
        if (!addressMap.keys.toSeq.contains(component.name)) {
          accAddress ++ aliasInAddress.headOption.map(component.name -> addressMap(_))
        } else accAddress
      }
    }
  }

  def sanityCleaning(addressMap: AddressMap): AddressMap = {
    val postcode = (add: AddressMap) => {
      val removePostcode = addressMap.get("postcode").exists { postcode =>
        postcode.length > 20 || postcode.matches("""\d+;\d+""")
      }
      val updatedPostcode =
        addressMap.get("postcode").flatMap(_.split(",").headOption)

      if (removePostcode) add - "postcode"
      else add ++ updatedPostcode.map("postcode" -> _)
    }

    val numericCountry = (add: AddressMap) => {
      if (addressMap.get("country").exists(_.matches("""\d+""")))
        add ++ addressMap.get("state").map("country" -> _) - "state"
      else add
    }

    val urlRemoval = (add: AddressMap) => {
      add.filter(_._2.matches("https?://.*")).keys.foldLeft(add) { (acc, component) =>
        acc - component
      }
    }

    postcode.compose(numericCountry).compose(urlRemoval)(addressMap)
  }

  def applyReplacements(addressMap: AddressMap): AddressMap = {
    val replacements = addressMap
      .get("country_code")
      .flatMap(templates.get)
      .flatMap(_.replace)
      .getOrElse(Seq.empty)
    addressMap.map(component =>
      (component._1 -> replacements.foldLeft(component._2) {
        case (acc, (regex, replacement)) => {
          if (regex.contains('=')) {
            val (comp, regex2) =
              regex.split("=", 2).toSeq match { case Seq(a, b) => (a, b) }
            if (component._1 == comp) acc.replaceAll(regex2, replacement)
            else acc
          } else {
            acc.replaceAll(regex, replacement)
          }
        }
      }))
  }

  def addCodes(addressMap: AddressMap): AddressMap = {
    val countryCode = addressMap.get("country_code").map(_.toUpperCase)

    val (state, stateCode, city) = addressMap.get("state") match {
      case Some(x) if x matches """(?i)^washington,? d\.?c\.?""" =>
        (Some("District of Columbia"), Some("DC"), Some("Washington"))
      case Some(x) => {
        (Some(x),
         countryCode
           .flatMap(states.get)
           .flatMap(_.find(_.name == x))
           .map(_.code)
           .filter(_.length < 4),
         None)
      }
      case _ => (None, None, None)
    }

    val countyCode = addressMap.get("county").flatMap { county =>
      for {
        countryCode <- addressMap.get("country_code")
        countiesMap <- counties.get(countryCode.toUpperCase)
        county      <- countiesMap.find(_.name == county)
      } yield county.code
    }

    addressMap ++
      stateCode.map("state_code"   -> _) ++
      state.map("state"            -> _) ++
      city.map("city"              -> _) ++
      countyCode.map("county_code" -> _)
  }

  def addAttention(addressMap: AddressMap): AddressMap = {
    val unknownComponents =
      addressMap.keys.filterNot(components.flatMap(_.possibleKeys).contains)
    val attention = unknownComponents.flatMap(addressMap.get).mkString(", ")
    if (attention.nonEmpty) addressMap ++ Map("attention" -> attention)
    else addressMap
  }

  def renderTemplate(addressMap: AddressMap): String = {
    val template = addressMap
      .get("country_code")
      .flatMap(templates.get)
      .getOrElse(templates("default"))
    val templateText =
      if (!hasMinimumAddressComponents(addressMap))
        template.fallback_template orElse templates
          .get("default")
          .flatMap(_.fallback_template)
      else template.address_template
    val mustache = new Mustache(templateText.getOrElse(""))
    cleanAddress(mustache.render(addressMap + ("first" -> firstHelper _)))
  }

  def hasMinimumAddressComponents(addressMap: Map[String, String]): Boolean =
    (addressMap.keys.toSet intersect Set("road", "postcode")).nonEmpty

  def firstHelper(str: String, render: String => String) =
    render(str)
      .split(Pattern.quote("||"))
      .map(_.trim)
      .find(_.nonEmpty)
      .getOrElse("")

  def postformatReplace(replacements: Seq[(String, String)])(addressString: String): String =
    replacements.foldLeft(addressString) {
      case (acc, (regex, replacement)) => acc.replaceAll(regex, replacement)
    }

  def cleanAddress(addressString: String): String = {
    val singleLineReplacements = Seq(
      """[\},\s]+$"""               -> "",
      """^[,\s]+"""                 -> "",
      """^- """                     -> "", // line starting with dash due to a parameter missing
      """,\s*,"""                   -> ", ", //multiple commas to one
      """[\t\p{Zs}]+,[\t\p{Zs}]+""" -> ", ", //one horiz whitespace behind comma
      """[\t\p{Zs}][\t\p{Zs}]+"""   -> " " //multiple horiz whitespace to one
    )

    val multiLineReplacements = Seq(
      """[\t\p{Zs}]\n"""  -> "\n", //horiz whitespace, newline to newline
      "\n,"               -> "\n", //newline comma to just newline
      """,,+"""           -> ",", //multiple commas to one
      ",\n"               -> "\n", //comma newline to just newline
      """\n[\t\p{Zs}]+""" -> "\n", //newline plus space to newline
      "\n\n+"             -> "\n", //multiple newline to one
      """^\s+"""          -> "", //remove whitespace from start
      """\s+$"""          -> "" //remove whitespace from end
    )

    val addressWithReplacements = multiLineReplacements
      .foldLeft(addressString) {
        case (acc, (regex, replacement)) => acc.replaceAll(regex, replacement)
      }
      .split("\n")
      .map(singleLineReplacements.foldLeft(_) {
        case (acc, (regex, replacement)) => acc.replaceAll(regex, replacement)
      })
      .mkString("\n")

    removeDuplicates(addressWithReplacements) + "\n"
  }

  def removeDuplicates(address: String): String = {
    address
      .split("\n")
      .map { line =>
        {
          val (dedupedLine, _) =
            line.split(",").foldLeft((Seq.empty[String], "")) {
              case ((acc, lastTerm), term) => {
                val cleanTerm = term.replaceAll("""^\s+""", "")
                if (lastTerm == cleanTerm && lastTerm.toLowerCase != "new york") (acc, lastTerm)
                else (acc :+ term, cleanTerm)
              }
            }
          dedupedLine.mkString(",")
        }
      }
      .distinct
      .filter(_.nonEmpty)
      .mkString("\n")
  }

}
