package co.samco.mendroid

import android.util.Base64
import co.samco.mend4.core.IBase64EncodingProvider
import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mend4.core.crypto.impl.DefaultJCECryptoProvider
import co.samco.mendroid.model.ErrorToastManager
import co.samco.mendroid.model.ErrorToastManagerImpl
import co.samco.mendroid.model.LockEventManager
import co.samco.mendroid.model.LockEventManagerImpl
import co.samco.mendroid.model.PropertyManager
import co.samco.mendroid.model.PropertyManagerImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class AppModule {
    @Provides
    @Singleton
    fun providePropertyManager(impl: PropertyManagerImpl): PropertyManager = impl

    @Provides
    @Singleton
    fun provideCryptoProvider(propertyManager: PropertyManager): CryptoProvider {
        return DefaultJCECryptoProvider(
            propertyManager,
            object : IBase64EncodingProvider {
                override fun decodeBase64(base64String: String): ByteArray {
                    return Base64.decode(base64String, Base64.URL_SAFE)
                }

                override fun encodeBase64URLSafeString(data: ByteArray): String {
                    return Base64.encodeToString(data, Base64.URL_SAFE)
                }
            }
        )
    }

    @Provides
    @Singleton
    fun provideErrorToastManager(impl: ErrorToastManagerImpl): ErrorToastManager = impl

    @Provides
    @Singleton
    fun provideLockEventManager(impl: LockEventManagerImpl): LockEventManager = impl
}