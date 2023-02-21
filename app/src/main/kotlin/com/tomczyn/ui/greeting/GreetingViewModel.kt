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
    private val ellipse = Ellipse(
        initialValue = GreetingState(),
        launched = Launched.WhileSubscribed(stopTimeout = 1_000),
        { fooUseCase().onEachToState { foo, state -> state.copy(foo = foo) } }, // Single extension function example
        { barUseCase().onEach { bar -> update { state -> state.copy(bar = bar) } } }, // More standard onEach manual update example
    )

    val state: StateFlow<GreetingState> = ellipse.state

    fun updateFoo(foo: String) {
        ellipse.update { state -> state.copy(foo = foo) }
    }
}

@Suppress("FunctionName")
inline fun <reified R> ViewModel.Ellipse(
    initialValue: R,
    launched: Launched = Launched.Eagerly,
    vararg flow: Ellipse<R>.() -> Flow<*> = emptyArray(),
): Ellipse<R> = Ellipse(
    scope = viewModelScope,
    initialValue = initialValue,
    launched = launched,
    flow = flow,
)

class Ellipse<R>(
    scope: CoroutineScope,
    initialValue: R,
    launched: Launched = Launched.Eagerly,
    vararg flow: Ellipse<R>.() -> Flow<*> = emptyArray(),
) {

    private val _state: MutableStateFlow<R> = MutableStateFlow(initialValue)
    val state: StateFlow<R> get() = _state

    init {
        when (launched) {
            Launched.Eagerly -> flow.map { produceFlow -> produceFlow(this@Ellipse) }
                .merge()
                .launchIn(scope)
            Launched.Lazily -> {
                scope.launch {
                    _state.subscriptionCount.first { it > 0 }
                    flow.map { produceFlow -> produceFlow(this@Ellipse) }
                        .merge()
                        .collect()
                }
            }
            is Launched.WhileSubscribed -> _state.subscriptionCount
                .map { it > 0 }
                .distinctUntilChanged()
                .onEach { subscribed ->
                    if (!subscribed) delay(launched.stopTimeout)
                }
                .flatMapLatest { subscribed ->
                    if (subscribed) {
                        flow.map { produceFlow -> produceFlow(this@Ellipse) }.merge()
                    } else {
                        emptyFlow()
                    }
                }
                .launchIn(scope)
        }
    }

    fun <T> Flow<T>.onEachToState(mapper: (T, R) -> R): Flow<T> =
        onEach { value -> update { state -> mapper(value, state) } }

    fun update(transform: (R) -> R) {
        _state.update { state -> transform(state) }
    }
}


sealed interface Launched {
    object Eagerly : Launched
    data class WhileSubscribed(val stopTimeout: Long = 0L) : Launched
    object Lazily : Launched
}