package io.github.benwillis.addressformatter

case class TestCase(
    description: Option[String],
    components: Map[String, String],
    expected: String
)
