package com.vasu.assistant.core.automation

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AutomationModule {

    @Binds
    @Singleton
    abstract fun bindStepExecutor(impl: TaskExecutor): StepExecutor
}
