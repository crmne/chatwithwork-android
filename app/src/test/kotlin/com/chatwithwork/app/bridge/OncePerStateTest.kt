package com.chatwithwork.app.bridge

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Files that come back with the page's answer go again at once only once per composer state. */
class OncePerStateTest {
    @Test
    fun `an answer straight after the files went under the same state hands nothing again`() {
        val gate = OncePerState()
        gate.stateArrived()
        gate.handed()
        assertThat(gate.mayHandAgain()).isFalse()
    }

    @Test
    fun `the page's next state lets them go again, once`() {
        val gate = OncePerState()
        gate.stateArrived()
        gate.handed()
        gate.stateArrived()
        assertThat(gate.mayHandAgain()).isTrue()
        gate.handed()
        assertThat(gate.mayHandAgain()).isFalse()
    }

    @Test
    fun `a state that comes while files are in flight lets the answer hand them back`() {
        val gate = OncePerState()
        gate.stateArrived()
        gate.handed()
        // The composer locks, then unlocks, while the upload runs: nothing is handed then.
        gate.stateArrived()
        gate.stateArrived()
        assertThat(gate.mayHandAgain()).isTrue()
    }

    @Test
    fun `before any state, nothing goes`() {
        assertThat(OncePerState().mayHandAgain()).isFalse()
    }
}
