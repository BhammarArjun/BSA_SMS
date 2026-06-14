package com.local.smsllm.di

import android.content.Context
import androidx.room.Room
import com.local.smsllm.data.AppDatabase
import com.local.smsllm.data.SmsDao
import com.local.smsllm.data.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "smsllm.db").build()

    @Provides
    @Singleton
    fun provideSmsDao(db: AppDatabase): SmsDao = db.smsDao()

    @Provides
    @Singleton
    fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()
}
