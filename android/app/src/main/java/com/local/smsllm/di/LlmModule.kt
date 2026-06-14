package com.local.smsllm.di

import com.local.smsllm.llm.LiteRtLmService
import com.local.smsllm.llm.LlmService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt bindings for the LLM service layer. */
@Module
@InstallIn(SingletonComponent::class)
abstract class LlmModule {

    @Binds
    @Singleton
    abstract fun bindLlmService(impl: LiteRtLmService): LlmService
}
