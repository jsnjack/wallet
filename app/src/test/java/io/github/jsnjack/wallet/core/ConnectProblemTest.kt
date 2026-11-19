package io.github.jsnjack.wallet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectProblemTest {
    @Test
    fun cancellingIsNotAProblem() {
        assertNull(ConnectProblem.fromAuthStatus(16))
    }

    @Test
    fun developerAndInternalErrorsMeanTheAppIsNotRegistered() {
        assertEquals(ConnectProblem.NOT_REGISTERED, ConnectProblem.fromAuthStatus(10))
        assertEquals(ConnectProblem.NOT_REGISTERED, ConnectProblem.fromAuthStatus(8))
    }

    @Test
    fun networkAndUnknownAuthStatuses() {
        assertEquals(ConnectProblem.NETWORK, ConnectProblem.fromAuthStatus(7))
        assertEquals(ConnectProblem.OTHER, ConnectProblem.fromAuthStatus(13))
    }

    @Test
    fun driveHttpStatuses() {
        assertEquals(ConnectProblem.NOT_REGISTERED, ConnectProblem.fromHttpStatus(401))
        assertEquals(ConnectProblem.API_DISABLED, ConnectProblem.fromHttpStatus(403))
        assertEquals(ConnectProblem.OTHER, ConnectProblem.fromHttpStatus(500))
    }
}
