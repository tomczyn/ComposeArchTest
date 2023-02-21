package com.tomczyn.ui.greeting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GreetingViewModel : ViewModel() {

    private val fooUseCase = FooUseCase()
    private val barUseCase = BarUseCase()

    private val _state: MutableStateFlow<GreetingState> = MutableStateFlow(GreetingState())
        .combineToStateIn(
            scope = viewModelScope,
            launched = Launched.WhileSubscribed(stopTimeout = 1_000),
            { fooUseCase().onEachToState { foo, state -> state.copy(foo = foo) } }, // Single extension function example
            { barUseCase().onEach { bar -> state.update { state -> state.copy(bar = bar) } } }, // More standard onEach manual update example
        )

    val state: StateFlow<GreetingState> get() = _state

    fun updateFoo(foo: String) {
        _state.update { state -> state.copy(foo = foo) }
    }
}

fun <R> MutableStateFlow<R>.combineToStateIn(
    scope: CoroutineScope,
    launched: Launched = Launched.Eagerly,
    vararg flow: StateInCombineContext<R>.() -> Flow<*> = emptyArray(),
): MutableStateFlow<R> = StateFlowWithStateInCombine(
    scope = scope,
    initialValue = value,
    launched = launched,
    flow = flow,
)

interface StateInCombineContext<R> {
    val state: MutableStateFlow<R>
    fun <T> Flow<T>.onEachToState(mapper: (T, R) -> R): Flow<T>
}

class StateFlowWithStateInCombine<ST>(
    scope: CoroutineScope,
    initialValue: ST,
    launched: Launched = Launched.Eagerly,
    private val state: MutableStateFlow<ST> = MutableStateFlow(initialValue),
    vararg flow: StateInCombineContext<ST>.() -> Flow<*> = emptyArray(),
) : MutableStateFlow<ST> by state {

    private val context: StateInCombineContext<ST> = object : StateInCombineContext<ST> {
        override val state: MutableStateFlow<ST>
            get() = this@StateFlowWithStateInCombine

        override fun <T> Flow<T>.onEachToState(mapper: (T, ST) -> ST): Flow<T> =
            onEach { value -> state.update { state -> mapper(value, state) } }
    }

    init {
        when (launched) {
            Launched.Eagerly -> flow.map { produceFlow -> produceFlow(context) }
                .merge()
                .launchIn(scope)
            Launched.Lazily -> scope.launch {
                waitForFirstSubscriber()
                flow.map { produceFlow -> produceFlow(context) }
                    .merge()
                    .collect()
            }
            is Launched.WhileSubscribed -> scope.launch {
                waitForFirstSubscriber()
                state.subscriptionCount
                    .map { it > 0 }
                    .distinctUntilChanged()
                    .onEach { subscribed ->
                        if (!subscribed) delay(launched.stopTimeout)
                    }
                    .flatMapLatest { subscribed ->
                        if (subscribed) {
                            flow.map { produceFlow -> produceFlow(context) }.merge()
                        } else {
                            emptyFlow()
                        }
                    }
                    .collect()
            }
        }
    }

    private suspend fun waitForFirstSubscriber() {
        state.subscriptionCount.first { it > 0 }
    }
}

sealed interface Launched {
    object Eagerly : Launched
    data class WhileSubscribed(val stopTimeout: Long = 0L) : Launched
    object Lazily : Launched
}