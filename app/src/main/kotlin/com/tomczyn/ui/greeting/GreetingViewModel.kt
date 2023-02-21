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
        .stateInMerge(
            scope = viewModelScope,
            launched = Launched.WhileSubscribed(stopTimeoutMillis = 1_000),
            { fooUseCase().onEachToState { foo, state -> state.copy(foo = foo) } }, // Single extension function example
            { barUseCase().onEach { bar -> state.update { state -> state.copy(bar = bar) } } }, // More standard onEach manual update example
        )

    val state: StateFlow<GreetingState> get() = _state

    fun updateFoo(foo: String) {
        _state.update { state -> state.copy(foo = foo) }
    }
}

fun <T> MutableStateFlow<T>.stateInMerge(
    scope: CoroutineScope,
    launched: Launched,
    vararg flow: StateInMergeContext<T>.() -> Flow<*>,
): MutableStateFlow<T> = StateFlowWithStateInMerge(
    scope = scope,
    state = this,
    launched = launched,
    flow = flow,
)

interface StateInMergeContext<T> {
    val state: MutableStateFlow<T>
    fun <R> Flow<R>.onEachToState(mapper: (R, T) -> T): Flow<R>
}

private class StateFlowWithStateInMerge<T>(
    scope: CoroutineScope,
    launched: Launched,
    private val state: MutableStateFlow<T>,
    vararg flow: StateInMergeContext<T>.() -> Flow<*>,
) : MutableStateFlow<T> by state {

    private val context: StateInMergeContext<T> = object : StateInMergeContext<T> {
        override val state: MutableStateFlow<T>
            get() = this@StateFlowWithStateInMerge

        override fun <R> Flow<R>.onEachToState(mapper: (R, T) -> T): Flow<R> =
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
                        if (!subscribed) delay(launched.stopTimeoutMillis)
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
    data class WhileSubscribed(val stopTimeoutMillis: Long = 0L) : Launched
    object Lazily : Launched
}