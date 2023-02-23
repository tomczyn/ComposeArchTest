package com.tomczyn.ui.greeting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomczyn.common.Launched
import com.tomczyn.common.stateInMerge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

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
