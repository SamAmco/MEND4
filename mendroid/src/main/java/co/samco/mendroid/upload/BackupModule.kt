package co.samco.mendroid.upload

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {
    @Binds abstract fun existingFiles(implementation: DeviceBackupFiles): ExistingBackupSource
}
