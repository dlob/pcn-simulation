package au.csiro.data61.pcnsimulation.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicLong

/**
 * Coroutine synchronization with a counter.
 * The [wait] function suspends until the [triggerValue] is reached.
 */
class CountingLatch(
        private val initial: Long,
        private val triggerValue: Long,
        private val parent: Job? = null
) {
    private var trigger = Trigger(initial, triggerValue, parent)

    fun reset() {
        trigger = Trigger(initial, triggerValue, parent)
    }

    fun get() = trigger.value.get()

    fun increment(): Long = trigger.increment()

    fun decrement(): Long = trigger.decrement()

    suspend fun wait() = trigger.await()

    private class Trigger(
            initial: Long,
            private val triggerValue: Long,
            parent: Job? = null
    ) : CompletableDeferred<Unit> by CompletableDeferred(parent) {
        val value = AtomicLong(initial)

        fun increment(): Long = test { value.incrementAndGet() }

        fun decrement(): Long = test { value.decrementAndGet() }

        fun test(block: () -> Long): Long {
            val v = block()
            if (!isCompleted && v == triggerValue)
                complete(Unit)
            return v
        }
    }
}
