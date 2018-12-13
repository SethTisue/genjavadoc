package com.typesafe.genjavadoc

import java.net.URLClassLoader
import java.lang.reflect.Modifier

import org.junit.Test
import org.junit.Assert._

import util._

object SignatureSpec {
  // this should match up against the definition in GenJavadocPlugin
  val javaKeywords = Set("abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
    "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
    "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
    "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
    "throw", "throws", "transient", "try", "void", "volatile", "while")

  // this should match up against the definition in GenJavadocPlugin
  // with the addition of "$lzycompute", which is special
  val defaultFilteredStrings = Set("$$", "$lzycompute",
    // and this one's a known issue, https://github.com/lightbend/genjavadoc/issues/143
    "productElementNames"
  )

  // they can't start with numbers either
  val startsWithNumber = "^\\d".r

  val expectedClasses = Seq(
    "AtTheRoot",
    "akka.Main",
    "akka.rk.buh.is.it.A",
    "akka.rk.buh.is.it.A$",
    "akka.rk.buh.is.it.Blarb",
    "akka.rk.buh.is.it.Blarb$",
    "akka.rk.buh.is.it.X",
    "akka.rk.buh.is.it.Y",
    "akka.rk.buh.is.it.Z",
    "akka.rk.buh.is.it.PPrivate",
    "akka.rk.buh.is.it.PPrivate$",
    "akka.rk.buh.is.it.Private",
    "akka.rk.buh.is.it.Private$",
    "akka.rk.buh.is.it.PProtected",
    "akka.rk.buh.is.it.PProtected$",
    "akka.rk.buh.is.it.PTrait",
    "akka.rk.buh.is.it.AnAbstractTypeRef"
  )

}

class SignatureSpec {

  import SignatureSpec._

  @Test def `the generated java files contain the same methods and classes as the original Scala files`(): Unit = {
    val doc = IO.tempDir("java")
    val docPath = doc.getAbsolutePath

    val scalac = new GenJavadocCompiler(Seq(
      s"genjavadoc:out=$docPath",
      "genjavadoc:suppressSynthetic=false"
    ))

    val javaSources = expectedClasses.map{cls =>
      docPath + "/" + cls.replace(".", "/") + ".java"
    }
    val javac = new JavaCompiler

    scalac.compile(BasicSpec.sources)
    javac.compile(javaSources)

    val scalaCL = new URLClassLoader(Array(scalac.target.getAbsoluteFile.toURI.toURL), classOf[List[_]].getClassLoader)
    val javaCL = new URLClassLoader(Array(javac.target.getAbsoluteFile.toURI.toURL), classOf[List[_]].getClassLoader)

    val accProtLvl = Map(1 -> 1, 2 -> 3, 4 -> 2)

    /*
     * This translation is necessary for the evil hack that allows things
     * nested in nested objects to be accepted by Javadoc: while the emitted
     * Java code compiles, the name mangling of javac and scalac differs for
     * such nestings, which means that it is impossible to express the Scalac
     * generated byte-code in valid Java source. To make things compile
     * nonetheless we just accept that the types here are nonsense, but they
     * are not usable from Java anyway.
     */
    val exception = "(akka.rk.buh.is.it.A\\$[C1D]+\\$)\\$"
    val replacement = "$1"

    def check(jn: String): Unit = {
      val jc: Class[_] = javaCL.loadClass(jn)
      val sc: Class[_] = scalaCL.loadClass(jn.replaceAll(exception, replacement))

      def matchJava(s: Set[String], j: Set[String]) = {
        val msg = s"Scala: \n" +
            s"  ${s.mkString("\n  ")} \n" +
            s"did not match Java: \n" +
            s"  ${j.mkString("\n  ")}\n" +
            s"(in $jc)"
        assertEquals(msg, s, j)
      }

      val jm = getMethods(jc, filter = false)
      val sm = getMethods(sc, filter = true)
      printIfNotEmpty(sm -- jm, "missing methods:")
      printIfNotEmpty(jm -- sm, "extraneous methods:")
      matchJava(sm, jm)

      // akka.rk.buh.is.it.A$C1$C1$ is a special case, generated because Java
      // doesn't allow an inner class with the same name as its enclosing
      // class, so we exclude it here:
      val jsub = getClasses(jc, filter = false) - "akka.rk.buh.is.it.A$C1$C1$"
      val ssub = getClasses(sc, filter = true) - "akka.rk.buh.is.it.A$C1$C1$"
      printIfNotEmpty(ssub.keySet -- jsub.keySet, "missing classes:")
      printIfNotEmpty(jsub.keySet -- ssub.keySet, "extraneous classes:")
      matchJava(ssub.keySet, jsub.keySet)

      for (n ← ssub.keys) {
        val js = jsub(n)
        val ss = ssub(n)

        def beEqual[T](u: T, t: T) = assertEquals(s"$u was not equal $t (in $n)", t, u)
        def beAtLeast(u: Int, t: Int) = assertTrue(s"$u was < $t (in $n)", u >= t)

        beEqual(js.getModifiers & ~15, ss.getModifiers & ~15)
        beAtLeast(ss.getModifiers & 8, js.getModifiers & 8) // older Scala versions (2.10) were more STATIC ...
        beAtLeast(accProtLvl(js.getModifiers & 7), accProtLvl(ss.getModifiers & 7))
        beEqual(js.getInterfaces.toList.map(_.getName), ss.getInterfaces.toList.map(_.getName))
        beEqual(js.isInterface, ss.isInterface)
        if (!js.isInterface())
          beEqual(js.getSuperclass.getName, ss.getSuperclass.getName)
        check(js.getName)
      }
    }

    def printIfNotEmpty(s: Set[String], msg: String): Unit = if (s.nonEmpty) {
      println(msg)
      s.toList.sorted foreach println
    }

    def getMethods(c: Class[_], filter: Boolean): Set[String] = {
      c.getDeclaredMethods.filterNot(x ⇒ filter && (defaultFilteredStrings.exists { s => x.getName.contains(s) }
        || javaKeywords.contains(x.getName)
        || x.getName == "$init$" // These synthetic methods show up in 2.12.0-M4+ even though they are not in the generated Java sources
        || (filter && Modifier.isStatic(x.getModifiers) && x.getName.endsWith("$")) // These synthetic static methods appear since 2.12.0-M5+ as companions to default methods
        || startsWithNumber.findFirstIn(x.getName).isDefined))
        .map(_.toGenericString)
        .map(_.replace("default ", "abstract ")) // Scala 2.12.0-M4+ creates default methods for trait method implementations. We treat them as abstract for now.
        .map(_.replaceAll(exception, replacement))
        .toSet
    }

    def getClasses(c: Class[_], filter: Boolean): Map[String, Class[_]] = {
      c.getDeclaredClasses.collect {
        case x if (!filter || !(x.getName contains "anon")) => x.getName.replaceAll(exception, replacement) -> x
      }.toMap
    }


    for (className <- expectedClasses) {
      check(className)
    }
  }

}
