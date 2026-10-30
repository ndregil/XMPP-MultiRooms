package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.UserPresence
import com.example.data.repository.XmppRepository
import com.example.ui.XmppViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UserPresenceStatusBroadcastTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var repository: XmppRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = XmppRepository(database, testScope)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testInitialPresenceState() {
        assertEquals(UserPresence.ONLINE, repository.userPresence.value)
        assertTrue(repository.userStatusMessage.value.isNotBlank())
    }

    @Test
    fun testUpdateUserPresenceToAway() = runTest {
        val newPresence = UserPresence.AWAY
        val newStatus = "Sedang istirahat makan siang 🥪"

        repository.updateUserPresence(newPresence, newStatus)

        assertEquals(newPresence, repository.userPresence.value)
        assertEquals(newStatus, repository.userStatusMessage.value)

        // Verify that occupants across all simulated MUC rooms are updated with the user's new presence
        val roomOccupants = repository.roomOccupants.value
        assertTrue("Room occupants map should not be empty", roomOccupants.isNotEmpty())

        for ((_, occupants) in roomOccupants) {
            val me = occupants.find { it.isMe }
            if (me != null) {
                assertEquals(newPresence, me.presence)
                assertEquals(newStatus, me.statusText)
            }
        }
    }

    @Test
    fun testUpdateUserPresenceToDoNotDisturb() = runTest {
        val newPresence = UserPresence.DND
        val newStatus = "Fokus koding Jetpack Compose ⛔"

        repository.updateUserPresence(newPresence, newStatus)

        assertEquals(UserPresence.DND, repository.userPresence.value)
        assertEquals(newStatus, repository.userStatusMessage.value)

        val roomOccupants = repository.roomOccupants.value
        for ((_, occupants) in roomOccupants) {
            val me = occupants.find { it.isMe }
            if (me != null) {
                assertEquals(UserPresence.DND, me.presence)
                assertEquals(newStatus, me.statusText)
            }
        }
    }

    @Test
    fun testUpdateUserPresenceToFreeToChat() = runTest {
        val newPresence = UserPresence.CHAT
        val newStatus = "Siap berdiskusi dengan semua kontak!"

        repository.updateUserPresence(newPresence, newStatus)

        assertEquals(UserPresence.CHAT, repository.userPresence.value)
        assertEquals(newStatus, repository.userStatusMessage.value)
    }

    @Test
    fun testUpdateUserPresenceToOffline() = runTest {
        val newPresence = UserPresence.OFFLINE
        val newStatus = "Terputus sementara"

        repository.updateUserPresence(newPresence, newStatus)

        assertEquals(UserPresence.OFFLINE, repository.userPresence.value)
        assertEquals(newStatus, repository.userStatusMessage.value)

        val roomOccupants = repository.roomOccupants.value
        for ((_, occupants) in roomOccupants) {
            val me = occupants.find { it.isMe }
            if (me != null) {
                assertEquals(UserPresence.OFFLINE, me.presence)
            }
        }
    }
}
