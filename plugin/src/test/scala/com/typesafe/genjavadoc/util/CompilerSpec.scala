package com.typesafe.genjavadoc
package util

import java.io.InputStreamReader
import java.io.BufferedReader
import java.io.File
import org.junit.Test

/** Utility trait for testing compiler bahaviour. */
trait CompilerSpec {

  /** Sources to compile. */
  def sources: Seq[String]

  /** Root directory that contains expected Java output. */
  def expectedPath: String

  /** Extra plugin arguments. */
  def extraSettings: Seq[String] = Seq.empty

  val doc = IO.tempDir("java")
  val docPath = doc.getAbsolutePath
  val defaultSettings = Seq(s"out=$docPath", "suppressSynthetic=false")
  val scalac = new GenJavaDocCompiler((defaultSettings ++ extraSettings).map{ kv =>
    s"genjavadoc:$kv"
  })

  @Test
  def test(): Unit = {
    scalac.compile(sources)
    assert(!scalac.reporter.hasErrors, "Scala compiler reported errors.")
    lines(run(".", "diff", "-wurN",
      "-I", "^ *//", // comment lines
      "-I", "^ *private  java\\.lang\\.Object readResolve", // since Scala 2.12.0-M3, these methods are emitted in a later compiler phase
      expectedPath, docPath)) foreach println
  }

  private def run(dir: String, cmd: String*): Process = {
    new ProcessBuilder(cmd: _*).directory(new File(dir)).redirectErrorStream(true).start()
  }

  private def lines(proc: Process) = {
    val b = new BufferedReader(new InputStreamReader(proc.getInputStream()))
    Iterator.continually(b.readLine).takeWhile(_ != null || { proc.waitFor(); assert(proc.exitValue == 0); false })
  }


}
