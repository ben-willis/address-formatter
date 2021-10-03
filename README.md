# Scala Address Formatter
[![Build](https://github.com/ben-willis/address-formatter/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/ben-willis/address-formatter/actions/workflows/build.yml) 
[![Maven Central](https://img.shields.io/maven-central/v/io.github.ben-willis/address-formatter_2.12?label=Maven%20Central)](https://maven-badges.herokuapp.com/maven-central/io.github.ben-willis/address-formatter_2.12)

This library can compose structured address data in to a format that users expect using the amazing work of [OpenCage Data](https://github.com/OpenCageData/address-formatting/) who collected so many international formats of postal addresses.

This library can format almost anything that comes out of Open Street Maps' [Nominatim API](https://wiki.openstreetmap.org/wiki/Nominatim) in the address field. Other compatible sources of data might be used as well.

It can automatically detect the country's formatting customs, but allows you to pick a specific country format.

The formatting specification for the whole world is part of the distribution package, there is currently no plan to prepare smaller builds with limited area coverage.

## Installation and Usage

```sbt
libraryDependencies += "io.github.ben-willis" %% "address-formatter" % "1.0.0"
```

```scala
val addressFormatter = new AddressFormatter()

val address = Map(
  "house_number" -> "221b",
  "road" -> "Baker Street",
  "city" -> "London",
  "postal_code" -> "NW1 6XE",
  "country_code" -> "gb"
)

/*
 * Expected output is:
 * 
 * 221b Baker Street
 * London NW1 6XE
*/
addressFormatter.format(address)
```

## Development and Tests

Test cases are from the [OpenCage address-formatting repo](https://github.com/opencagedata/address-formatting).

```
sbt test
```

## Acknowledgements

Big thanks to the [PHP](https://github.com/predicthq/address-formatter-php), [Perl](https://metacpan.org/release/Geo-Address-Formatter) and [Javascript](https://github.com/fragaria/address-formatter) implementations of the address formatter.
