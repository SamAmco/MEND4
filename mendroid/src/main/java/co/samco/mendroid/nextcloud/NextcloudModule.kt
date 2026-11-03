package co.samco.mendroid.nextcloud

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NextcloudModule {
    @Binds abstract fun credentials(implementation: EncryptedCredentialStore): CredentialStore
    @Binds abstract fun login(implementation: NextcloudLoginClient): NextcloudLogin
    @Binds abstract fun folders(implementation: WebDavClient): RemoteFolderBrowser
}
