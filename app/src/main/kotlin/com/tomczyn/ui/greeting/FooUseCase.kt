package com.tomczyn.ui.greeting

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber

class FooUseCase : () -> Flow<String> {
    override fun invoke(): Flow<String> = flow {
        delay(5_000)
        repeat(50) {
            Timber.tag("Maciek").d("Producing Foo $it")
            emit(it.toString())
            delay(100)
        }
    }
}
