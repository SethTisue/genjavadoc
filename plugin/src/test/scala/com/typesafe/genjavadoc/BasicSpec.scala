package com.typesafe.genjavadoc

import org.scalatest.Matchers
import org.scalatest.WordSpec

import util._

import scala.sys.process._

object BasicSpec {
  def sources: Seq[String] = Seq(
    "src/test/resources/input/basic/test.scala",
    "src/test/resources/input/basic/root.scala",
    "src/test/resources/input/basic/akka/Main.scala"
  )
}

/** Test basic behaviour of genjavadoc with standard settings */
class BasicSpec extends WordSpec with Matchers with CompilerSpec {

  override def sources = BasicSpec.sources
  override def expectedPath: String = {
    import scala.util.Properties.versionNumberString
    val patchVersion =
      if (versionNumberString.startsWith("2.12.0"))
        "2.12.0"
      else if (versionNumberString.startsWith("2.12."))
        "2.12.1"
      else
        versionNumberString
    val patchFile = s"src/test/resources/patches/$patchVersion.patch"
    if (new java.io.File(patchFile).exists) { // we have a patch to apply to expected output for this scala version
      "rm -rf target/expected_output".! // cleanup from previous runs
      "cp -r src/test/resources/expected_output target/".! // copy expected output to a place which is going to be patched
      s"patch -p0 -i $patchFile".! // path expected output
      "target/expected_output/basic"
    } else {
      "src/test/resources/expected_output/basic"
    }
  }

}
