package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.data.local.dao.AccountDao
import com.example.data.local.dao.ChatMessageDao
import com.example.data.local.dao.MessageDao
import com.example.data.local.dao.RoomConfigDao
import com.example.data.local.dao.RoomDao
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.local.entity.RoomConfigEntity
import com.example.data.local.entity.RoomEntity
import com.example.data.model.DeliveryStatus
import com.example.data.model.MessageType
import com.example.data.model.OccupantAffiliation
import com.example.data.model.OccupantRole

class Converters {
    @TypeConverter
    fun fromMessageType(value: MessageType): String = value.name

    @TypeConverter
    fun toMessageType(value: String): MessageType = try {
        MessageType.valueOf(value)
    } catch (e: Exception) {
        MessageType.GROUPCHAT
    }

    @TypeConverter
    fun fromDeliveryStatus(value: DeliveryStatus): String = value.name

    @TypeConverter
    fun toDeliveryStatus(value: String): DeliveryStatus = try {
        DeliveryStatus.valueOf(value)
    } catch (e: Exception) {
        DeliveryStatus.SENT
    }

    @TypeConverter
    fun fromOccupantRole(value: OccupantRole): String = value.name

    @TypeConverter
    fun toOccupantRole(value: String): OccupantRole = try {
        OccupantRole.valueOf(value)
    } catch (e: Exception) {
        OccupantRole.MEMBER
    }

    @TypeConverter
    fun fromOccupantAffiliation(value: OccupantAffiliation): String = value.name

    @TypeConverter
    fun toOccupantAffiliation(value: String): OccupantAffiliation = try {
        OccupantAffiliation.valueOf(value)
    } catch (e: Exception) {
        OccupantAffiliation.MEMBER
    }
}

@Database(
    entities = [
        AccountEntity::class,
        RoomEntity::class,
        MessageEntity::class,
        RoomConfigEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun roomDao(): RoomDao
    abstract fun messageDao(): MessageDao
    fun chatMessageDao(): ChatMessageDao = messageDao()
    abstract fun roomConfigDao(): RoomConfigDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "xmpp_multiroom_database"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

