package helper

import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mend4.core.crypto.UnlockResult
import co.samco.mend4.desktop.dao.OSDao
import co.samco.mend4.desktop.helper.impl.KeyHelperImpl
import com.nhaarman.mockitokotlin2.*
import org.junit.Assert.*
import org.junit.Test
import java.security.PrivateKey

class OneShotKeyHelperTest {
    @Test fun authenticatesOnceAndClearsPasswordWithoutTouchingKeyFiles() {
        val os = mock<OSDao>()
        val crypto = mock<CryptoProvider>()
        val key = mock<PrivateKey>()
        val password = "test-password".toCharArray()
        whenever(os.readPassword(any())).thenReturn(password)
        whenever(crypto.unlock(any())).thenReturn(UnlockResult.Success(key))
        val helper = KeyHelperImpl(crypto, os)
        assertSame(key, helper.privateKey)
        assertSame(key, helper.privateKey)
        verify(crypto, times(1)).unlock(any())
        verify(os, never()).readAllBytes(any())
        verify(os, never()).fileOutputSteam(any(), any())
        assertTrue(password.all { it == '\u0000' })
    }
}
