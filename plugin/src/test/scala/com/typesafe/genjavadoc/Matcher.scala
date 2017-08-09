package com.typesafe.genjavadoc

import org.junit.Assert.assertTrue

case class MatchResult(
  matches: Boolean,
  rawFailureMessage: String,
  rawNegatedFailureMessage: String
)

object Matcher {
  def apply[T](fun: T => MatchResult): Matcher[T] =
    new Matcher[T] {
      override def apply(left: T): MatchResult =
        fun(left)
    }
  def should[T](left: T, matcher: Matcher[T]): Unit =
    assertTrue(matcher(left).matches)
}

trait Matcher[-T] {
  def apply(left: T): MatchResult
}

trait Matchers {
  implicit class Shouldable[T](left: T) {
    def should(matcher: Matcher[T]): Unit =
      Matcher.should(left, matcher)
  }
}
