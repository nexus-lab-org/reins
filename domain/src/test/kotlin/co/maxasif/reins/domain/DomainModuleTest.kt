package co.maxasif.reins.domain

import org.junit.Assert.assertNotNull
import org.junit.Test

class DomainModuleTest {
    @Test
    fun `domain module loads with no framework dependencies`() {
        assertNotNull(DomainModule)
    }
}
