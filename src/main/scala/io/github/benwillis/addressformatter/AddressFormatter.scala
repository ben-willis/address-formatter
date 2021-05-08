package io.github.benwillis.addressformatter

import io.circe.Decoder
import io.circe.generic.auto._
import io.circe.yaml.parser
import io.github.benwillis.addressformatter.Models.{Component, State, Template}

import java.io.InputStreamReader
import java.util.regex.Pattern

class AddressFormatter {

  import Decoders._

  private lazy val templates: Map[String, Template]  = decodeYaml[Map[String, Template]]("/countries/worldwide.yaml")
  private lazy val components: Seq[Component]        = decodeYamlDocuments[Component]("/components.yaml")
  private lazy val states: Map[String, Seq[State]]   = decodeYaml[Map[String, Seq[State]]]("/state_codes.yaml")
  private lazy val counties: Map[String, Seq[State]] = decodeYaml[Map[String, Seq[State]]]("/county_codes.yaml")

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

  /**
    * Composes fields from an address map as a formatted address
    *
    * @param address A map from address component name to value
    * @param countryCode An optional country code to enforce a particulat format
    * @return A formatted address string
    */
  def format(address: Map[String, String], countryCode: Option[String] = None): String = {
    val augmentedAddress = Seq[Map[String, String] => Map[String, String]](
      _ ++ countryCode.map("country_code" -> _),
      determineCountryCode,
      setAliases,
      sanityCleaning,
      applyReplacements,
      addCodes,
      addAttention
    ).reduce(_.andThen(_))(address)

    val rawAddressString = renderTemplate(augmentedAddress)

    val postformatReplacements = augmentedAddress
      .get("country_code")
      .flatMap(templates.get)
      .flatMap(_.postformat_replace)
      .getOrElse(Seq.empty)

    val addressWithReplacements = postFormatReplace(rawAddressString, postformatReplacements)

    cleanAddress(addressWithReplacements)
  }

  private def determineCountryCode(addressMap: Map[String, String]): Map[String, String] = {
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

    val nlEdgeCasesExtraTerm = addressMap
      .get("state")
      .collect {
        case country if country matches """Curaçao""" =>
          Seq("country_code" -> "CW", "country" -> country)
        case country if country matches """(?i)^sint maarten""" =>
          Seq("country_code" -> "SX", "country" -> "Sint Maarten")
        case country if country matches """(?i)^Aruba""" =>
          Seq("country_code" -> "AR", "country" -> "Aruba")
      }
      .getOrElse(Seq.empty)

    addressMap ++ extraTerms ++ nlEdgeCasesExtraTerm
  }

  private def setAliases(addressMap: Map[String, String]): Map[String, String] =
    components.foldLeft(addressMap) { (accAddress, component) =>
      {
        val aliasInAddress = component.aliases.getOrElse(Seq.empty) intersect addressMap.keys.toSeq
        if (!addressMap.keys.toSeq.contains(component.name)) {
          accAddress ++ aliasInAddress.headOption.map(component.name -> addressMap(_))
        } else accAddress
      }
    }

  private def sanityCleaning(addressMap: Map[String, String]): Map[String, String] = {
    val postcode = (addr: Map[String, String]) => {
      val removePostcode = addressMap.get("postcode").exists { postcode =>
        postcode.length > 20 || postcode.matches("""\d+;\d+""")
      }
      val updatedPostcode =
        addressMap.get("postcode").flatMap(_.split(",").headOption)

      if (removePostcode) addr - "postcode"
      else addr ++ updatedPostcode.map("postcode" -> _)
    }

    val numericCountry = (addr: Map[String, String]) => {
      if (addressMap.get("country").exists(_.matches("""\d+""")))
        addr ++ addressMap.get("state").map("country" -> _) - "state"
      else addr
    }

    val urlRemoval = (addr: Map[String, String]) => {
      addr.filter(_._2.matches("https?://.*")).keys.foldLeft(addr) { (acc, component) =>
        acc - component
      }
    }

    postcode.compose(numericCountry).compose(urlRemoval)(addressMap)
  }

  private def applyReplacements(addressMap: Map[String, String]): Map[String, String] = {
    val replacements = addressMap.get("country_code").flatMap(templates.get).flatMap(_.replace).getOrElse(Seq.empty)

    addressMap.map { component =>
      component._1 -> replacements.foldLeft(component._2) {
        case (acc, (regex, replacement)) if regex.contains('=') => {
          val (comp, regex2) = regex.split("=", 2).toSeq match { case Seq(a, b) => (a, b) }
          if (component._1 == comp) acc.replaceAll(regex2, replacement) else acc
        }
        case (acc, (regex, replacement)) => acc.replaceAll(regex, replacement)
      }
    }
  }

  private def addCodes(addressMap: Map[String, String]): Map[String, String] = {
    val countryCode = addressMap.get("country_code").map(_.toUpperCase)

    val stateAndCityCodes = addressMap
      .get("state")
      .collect {
        case x if x matches """(?i)^washington,? d\.?c\.?""" =>
          Seq("state" -> "District of Columbia", "state_code" -> "DC", "city" -> "Washington")
        case x =>
          Seq("state" -> x) ++
            countryCode
              .flatMap(states.get)
              .flatMap(_.find(_.name == x))
              .map(_.code)
              .filter(_.length < 4)
              .map("state_code" -> _)

      }
      .getOrElse(Seq.empty)

    val countyCode = for {
      county      <- addressMap.get("county")
      countryCode <- addressMap.get("country_code")
      countiesMap <- counties.get(countryCode.toUpperCase)
      countyCode  <- countiesMap.find(_.name == county)
    } yield ("county_code" -> countyCode.code)

    addressMap ++ stateAndCityCodes ++ countyCode
  }

  private def addAttention(addressMap: Map[String, String]): Map[String, String] = {
    val unknownComponents = addressMap.keys.filterNot(components.flatMap(_.possibleKeys).contains)
    val attention         = unknownComponents.flatMap(addressMap.get).mkString(", ")

    if (attention.nonEmpty) addressMap ++ Map("attention" -> attention) else addressMap
  }

  private def renderTemplate(addressMap: Map[String, String]): String = {
    val template = addressMap.get("country_code").collect(templates).getOrElse(templates("default"))

    val templateText =
      if (!hasMinimumAddressComponents(addressMap))
        template.fallback_template orElse templates.get("default").flatMap(_.fallback_template)
      else template.address_template

    val mustache = new Mustache(templateText.getOrElse(""))

    cleanAddress(mustache.render(addressMap + ("first" -> firstHelper _)))
  }

  private def hasMinimumAddressComponents(addressMap: Map[String, String]): Boolean =
    (addressMap.keySet intersect Set("road", "postcode")).nonEmpty

  private def firstHelper(str: String, render: String => String): String =
    render(str).split(Pattern.quote("||")).map(_.trim).find(_.nonEmpty).getOrElse("")

  private def postFormatReplace(address: String, replacements: Seq[(String, String)]): String =
    replacements.foldLeft(address) {
      case (acc, (regex, replacement)) => acc.replaceAll(regex, replacement)
    }

  private def cleanAddress(addressString: String): String = {
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

  private def removeDuplicates(address: String): String = {
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
