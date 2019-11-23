package io.github.andrebeat.pool

import java.util.concurrent.atomic.LongAdder

final private[pool] class Jdk8Adder extends LongAdder with Adder {
  def count(): Long = this.sum
}
