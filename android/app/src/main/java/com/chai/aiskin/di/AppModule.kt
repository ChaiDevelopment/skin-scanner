package com.chai.aiskin.di

import com.chai.aiskin.core.common.DefaultDispatcherProvider
import com.chai.aiskin.core.common.DispatcherProvider
import com.chai.aiskin.core.network.TokenProvider
import com.chai.aiskin.data.local.PreferencesRepositoryImpl
import com.chai.aiskin.data.repository.AnalysisRepositoryImpl
import com.chai.aiskin.data.repository.AuthRepositoryImpl
import com.chai.aiskin.domain.repository.AnalysisRepository
import com.chai.aiskin.domain.repository.AuthRepository
import com.chai.aiskin.domain.repository.PreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun dispatchers(impl: DefaultDispatcherProvider): DispatcherProvider
    @Binds abstract fun preferences(impl: PreferencesRepositoryImpl): PreferencesRepository
    @Binds abstract fun auth(impl: AuthRepositoryImpl): AuthRepository
    @Binds abstract fun tokens(impl: AuthRepositoryImpl): TokenProvider
    @Binds abstract fun analysis(impl: AnalysisRepositoryImpl): AnalysisRepository
}

@Module
@InstallIn(SingletonComponent::class)
object ScopeModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
