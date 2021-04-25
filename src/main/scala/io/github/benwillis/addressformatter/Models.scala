package io.github.benwillis.addressformatter

object Models {
  case class Component(
      name: String,
      aliases: Option[Seq[String]]
  ) {
    def possibleKeys: Seq[String] =
      Seq(this.name) ++ this.aliases.getOrElse(Seq.empty)
  }

  case class Template(
      address_template: Option[String] = None,
      fallback_template: Option[String] = None,
      use_country: Option[String] = None,
      change_country: Option[String] = None,
      add_component: Option[String] = None,
      replace: Option[Seq[(String, String)]] = None,
      postformat_replace: Option[Seq[(String, String)]] = None
  )

  case class State(code: String, name: String, translations: Map[String, String])
}
