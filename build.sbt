enablePlugins(GatlingPlugin)

name := "api-testing"
version := "1.1.0"
scalaVersion := "2.12.4"


libraryDependencies += "io.gatling.highcharts" % "gatling-charts-highcharts" % "2.3.0" % "test"
libraryDependencies += "io.gatling" % "gatling-test-framework" % "2.3.0" % "test"
libraryDependencies += "io.spray" %% "spray-json" % "1.3.4"

fork in run := true

