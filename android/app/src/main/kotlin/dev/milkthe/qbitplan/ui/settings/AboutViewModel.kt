package dev.milkthe.qbitplan.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milkthe.qbitplan.data.UpdateRepository
import dev.milkthe.qbitplan.data.UpdateStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val updates: UpdateRepository,
) : ViewModel() {

    val update: StateFlow<UpdateStatus> = updates.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpdateStatus.NeverChecked)

    fun checkNow() = updates.checkNow()
}
