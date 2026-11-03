package co.samco.mendroid.upload

import co.samco.mendroid.nextcloud.WebDavClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class UploadModule {
    @Binds abstract fun queue(implementation: SqliteUploadQueue): UploadQueue
    @Binds abstract fun files(implementation: AndroidBackupFiles): LocalBackupFiles
    @Binds abstract fun network(implementation: AndroidNetworkStatus): NetworkStatus
    @Binds abstract fun transport(implementation: WebDavClient): UploadTransport
    @Binds abstract fun wakeup(implementation: UploadScheduler): UploadWakeup
}
