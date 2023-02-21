package com.tomczyn.ui.greeting

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber

class BarUseCase : () -> Flow<Int> {

    override fun invoke(): Flow<Int> = flow {
        repeat(100) {
            Timber.tag("Maciek").d("Producing Bar $it")
            emit(it)
            delay(100)
        }
    }
}
