package com.local.smsllm.di

import com.local.smsllm.repo.SettingsAccess
import com.local.smsllm.repo.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepoModule {

    @Binds
    @Singleton
    abstract fun bindSettingsAccess(impl: SettingsRepository): SettingsAccess
}
