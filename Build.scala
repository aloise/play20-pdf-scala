import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "test"
  val appVersion      = "0.7-SNAPSHOT"
  val scalaVersion    = "2.10.3"
    
  val appDependencies = Seq(
    // Add your project dependencies here,

    "org.xhtmlrenderer" % "core-renderer" % "R8",
    "net.sf.jtidy" % "jtidy" % "r938",
    
  )


  val main = play.Project(appName, appVersion, appDependencies)

}
