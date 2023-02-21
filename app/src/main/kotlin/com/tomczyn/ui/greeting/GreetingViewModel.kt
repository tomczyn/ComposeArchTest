package com.tomczyn.ui.greeting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

class GreetingViewModel : ViewModel() {

    private val fooUseCase = FooUseCase()
    private val barUseCase = BarUseCase()
    private val ellipse = Ellipse(
        initialValue = GreetingState(),
        delaySharing = 1_000,
        { fooUseCase().onEachToState { foo, state -> state.copy(foo = foo) } }, // Single extension function example
        { barUseCase().onEach { update { state -> state.copy(bar = it) } } }, // More standard onEach manual update example
    )

    val state: StateFlow<GreetingState> = ellipse.state

    fun updateFoo(foo: String) {
        ellipse.update { state -> state.copy(foo = foo) }
    }
}

@Suppress("FunctionName")
inline fun <reified R> ViewModel.Ellipse(
    initialValue: R,
    delaySharing: Long = 0L,
    vararg flow: Ellipse<R>.() -> Flow<*> = emptyArray(),
): Ellipse<R> = Ellipse(
    scope = viewModelScope,
    initialValue = initialValue,
    delaySharing = delaySharing,
    flow = flow,
)

class Ellipse<R>(
    scope: CoroutineScope,
    initialValue: R,
    delaySharing: Long = 0.toLong(),
    vararg flow: Ellipse<R>.() -> Flow<*> = emptyArray(),
) {

    private val _state: MutableStateFlow<R> = MutableStateFlow(initialValue)
    val state: StateFlow<R> get() = _state

    init {
        _state.subscriptionCount
            .map { it > 0 }
            .distinctUntilChanged()
            .onEach { subscribed -> if (!subscribed && delaySharing > 0L) delay(delaySharing) }
            .flatMapLatest { subscribed ->
                if (subscribed) {
                    flow.map { produceFlow -> produceFlow(this) }.merge()
                } else {
                    emptyFlow()
                }
            }
            .launchIn(scope)
    }

    fun <T> Flow<T>.onEachToState(mapper: (T, R) -> R): Flow<T> =
        onEach { value -> update { state -> mapper(value, state) } }

    fun update(transform: (R) -> R) {
        _state.update { state -> transform(state) }
    }
}
