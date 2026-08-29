package icu.nd4y.dosette.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import icu.nd4y.dosette.data.repository.ProfileRepository
import icu.nd4y.dosette.domain.model.Profile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MoreViewModel
    @Inject
    constructor(
        profileRepository: ProfileRepository,
    ) : ViewModel() {
        val profiles: StateFlow<List<Profile>> =
            profileRepository
                .observeAll()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }
