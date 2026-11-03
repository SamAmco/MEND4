package co.samco.mendroid.upload

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import co.samco.mendroid.R
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class UploadFailureNotificationsTest {
    private lateinit var context: Context
    private lateinit var manager: NotificationManager
    private lateinit var queue: SqliteUploadQueue
    private lateinit var notifications: UploadFailureNotifications
    private val file = LocalBackupFile("content://log", "phone.mend", UploadKind.LOG)
    private val target = UploadTarget(AccountId("account"), RemotePath.of("/phone.mend"))

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        context.deleteDatabase("nextcloud-uploads.db")
        queue = SqliteUploadQueue(context)
        manager = context.getSystemService(NotificationManager::class.java)
        notifications = UploadFailureNotifications(context, queue)
    }
    @After fun teardown() { queue.close(); manager.cancelAll() }

    @Test fun repeatedFailuresUpdateOneOngoingNotificationWithRetryAction() {
        notifications.showFailure()
        val first = manager.activeNotifications.single()
        notifications.showFailure()
        val latest = manager.activeNotifications.single()
        assertEquals(first.id, latest.id)
        val notification = latest.notification
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(context.getString(R.string.upload_retry), notification.actions.single().title)
        assertEquals(UploadRetryReceiver::class.java.name,
            shadowOf(notification.actions.single().actionIntent).savedIntent.component?.className)
    }

    @Test fun persistedFailureRemainsThroughRetriesAndClearsOnSuccessWithoutRemovingForegroundNotification() = runBlocking {
        queue.enqueueExisting(file, target)
        val revision = queue.pending().single().revision
        queue.failUpload(requireNotNull(queue.claim(revision)), UploadFailure.CONNECTION)
        queue.close()
        queue = SqliteUploadQueue(context)
        notifications = UploadFailureNotifications(context, queue)
        manager.createNotificationChannel(NotificationChannel("foreground", "foreground", NotificationManager.IMPORTANCE_LOW))
        manager.notify(103, Notification.Builder(context, "foreground").setSmallIcon(R.drawable.ic_notification_icon).build())
        val observing = notifications.start()
        try {
            await { manager.activeNotifications.size == 2 }
            val upload = requireNotNull(queue.claim(revision))
            assertEquals(2, manager.activeNotifications.size)
            queue.completeUpload(upload)
            await { manager.activeNotifications.size == 1 }
            assertEquals(103, manager.activeNotifications.single().id)
        } finally { observing.cancelAndJoin() }
    }

    @Test fun forgettingFailedEntryClearsNotification() = runBlocking {
        queue.enqueueExisting(file, target)
        val revision = queue.pending().single().revision
        queue.failUpload(requireNotNull(queue.claim(revision)), UploadFailure.AUTHENTICATION)
        val observing = notifications.start()
        try {
            await { manager.activeNotifications.size == 1 }
            queue.forget(revision)
            await { manager.activeNotifications.isEmpty() }
        } finally { observing.cancelAndJoin() }
    }

    @Test fun noFailureDoesNotCreateNotification() = runBlocking {
        queue.enqueueExisting(file, target)
        queue.pending()
        val observing = notifications.start()
        try {
            delay(100)
            assertTrue(manager.activeNotifications.isEmpty())
        } finally { observing.cancelAndJoin() }
    }

    @Test fun deniedNotificationPermissionDoesNotPreventUploads() {
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifications.showFailure()
        assertTrue(manager.activeNotifications.isEmpty())
    }

    private suspend fun await(predicate: () -> Boolean) = withTimeout(5_000) {
        while (!predicate()) delay(10)
    }
}
