package co.samco.mendroid.remote

import co.samco.mendroid.nextcloud.WebDavClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RemoteReadModule {
    @Binds abstract fun client(implementation: WebDavClient): RemoteReadClient
}
