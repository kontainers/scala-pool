package io.github.andrebeat.pool

import java.util.concurrent.BlockingQueue
import org.specs2.mutable.Specification
import scala.compat.Platform
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.ClassTag

abstract class PoolSpec[P[_ <: AnyRef] <: Pool[_]](implicit ct: ClassTag[P[_]])
    extends Specification
    with TestHelper {
  def Pool[A <: AnyRef](
    capacity: Int,
    factory: () => A,
    referenceType: ReferenceType = ReferenceType.Strong,
    reset: A => Unit = { _: A => () },
    dispose: A => Unit = { _: A => () }
  ): P[A]

  s"A ${ct.runtimeClass.getSimpleName}" should {
    "have a capacity" >> {
      val p = Pool(3, () => new Object)
      p.capacity() === 3
    }

    "return the number of pooled, live and leased objects" >> {
      val p = Pool(3, () => new Object)
      p.acquire()

      p.size() === 0
      p.leased() === 1
      p.live() === 1
    }

    "create objects lazily" >> {
      var i = 0

      val p = Pool(2, () => { i += 1; new Object })
      p.live() === 0
      p.size() === 0
      i === 0

      p.acquire()

      p.live() === 1
      p.size() === 0
      i === 1

      p.acquire()

      p.live() === 2
      p.size() === 0
      i === 2
    }

    "never create objects if there are any available on the pool (regression)" >> {
      var i = 0

      val p = Pool(2, () => { i += 1; new Object })
      p.live() === 0
      p.size() === 0
      i === 0

      p.acquire().release()

      p.live() === 1
      p.size() === 1
      i === 1

      p.acquire()

      p.live() === 1
      p.size() === 0
      i === 1
    }

    "allow filling the pool" >> {
      val p = Pool(3, () => new Object)
      p.live() === 0
      p.size() === 0

      p.fill()

      p.live() === 3
      p.size() === 3
    }

    "fill the pool taking into account live objects outside the pool" >> {
      val p = Pool(3, () => new Object)
      p.live() === 0
      p.size() === 0

      p.acquire()
      p.fill()

      p.live() === 3
      p.size() === 2
    }

    "allow draining the pool" >> {
      val p = Pool(3, () => new Object)
      p.fill()
      p.size() === 3

      p.drain()

      p.size() === 0
      p.live() === 0
    }

    "block when no objects available and all objects have been created" >> {
      val p = Pool(3, () => new Object)
      p.fill()

      p.acquire()
      p.acquire()
      val l1 = p.acquire()

      val f = Future {
        Some(p.acquire().get())
      }

      Future {
        sleep(100.millis)
        l1.release()
      }

      await(f, 300.millis) must beSome
    }

    "only block until a given duration when trying to acquire an object" >> {
      val p = Pool[Object](3, () => new Object)
      p.fill()

      p.acquire()
      p.acquire()
      p.acquire()

      val f = Future {
        p.tryAcquire(100.millis): Option[Lease[_]]
      }

      await(f, 300.millis) must beNone
    }

    "call the reset method when adding/releasing an object to the pool" >> {
      "when filling" >> {
        var i = 0

        val p = Pool(3, () => new Object, reset = { _: Object => i += 1 })
        p.fill()

        p.size() === 3
        i === 3
      }

      "when releasing" >> {
        var i = 0

        val p = Pool(3, () => new Object, reset = { _: Object => i += 1 })
        p.acquire().release()

        i === 1
      }
    }

    "call the dispose method whenever an object is evicted from the pool" >> {
      "when invalidated" >> {
        var i = 0

        val p = Pool(3, () => new Object, dispose = { _: Object => i += 1 })
        p.acquire().invalidate()

        i === 1
      }

      "when draining" >> {
        var i = 0

        val p = Pool(3, () => new Object, dispose = { _: Object => i += 1 })
        p.fill()
        p.drain()

        i === 3
      }

      "when added to an already full pool" >> {
        // This situtation should never happen in a normal usage of the pool
        var i = 0
        val p = Pool(3, () => new Object, dispose = { _: Object => i += 1 })

        p.fill()
        val l = p.acquire()

        val itemsField =
          p.getClass
            .getSuperclass
            .getDeclaredField("items")
        itemsField.setAccessible(true)

        val items = itemsField.get(p).asInstanceOf[BlockingQueue[Object]]

        items.offer(new Object)

        // We're cheating the pool
        p.size() === 3

        l.release()

        // The object is disposed
        i === 1
      }
    }

    "handle GC-based eviction" >> {
      var i = 0
      val p = Pool(3, () => { i += 1; new Object }, referenceType = ReferenceType.Weak)

      p.fill()

      p.size() === 3
      i === 3

      Platform.collectGarbage

      p.acquire()

      // A new object add to be created since the existing ones were invalidated by the GC
      i === 4
    }

    s"A ${ct.runtimeClass.getSimpleName} Lease" should {
      "allow being invalidated and removed from the pool" >> {
        val p = Pool(3, () => new Object)

        p.fill()

        p.size() === 3
        p.live() === 3

        val l1 = p.acquire()
        l1.invalidate()

        p.live() === 2
        p.size() === 2
      }

      "allow being released back to the pool" >> {
        val p = Pool(3, () => new Object)

        p.fill()

        p.size() === 3
        p.live() === 3

        val l1 = p.acquire()
        p.size() === 2

        l1.release()

        p.live() === 3
        p.size() === 3
      }

      "throw an exception when reading from an invalidated or released lease" >> {
        val p = Pool(3, () => new Object)

        val l1 = p.acquire()
        l1.invalidate()

        l1.get() must throwAn[IllegalStateException]

        val l2 = p.acquire()
        l2.release()

        l2.get() must throwAn[IllegalStateException]
      }
    }
  }
}

class PoolObjectSpec extends Specification {
  "The Pool companion object" should {
    "create specific Pool instances based on the given criteria" >> {
      Pool(1, () => new Object) must haveClass[SimplePool[Object]]
      Pool(1, () => new Object, maxIdleTime = 10.seconds) must haveClass[ExpiringPool[Object]]

      Pool(1, () => new Object, referenceType = ReferenceType.Weak).referenceType === ReferenceType.Weak
      Pool(
        1, () => new Object, referenceType = ReferenceType.Soft, maxIdleTime = 10.seconds
      ).referenceType === ReferenceType.Soft
    }
  }
}