package com.shai.capitall.ui.spaces

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.shai.capitall.R
import com.shai.capitall.data.model.Space
import com.shai.capitall.data.repository.CreateResult
import com.shai.capitall.data.repository.JoinResult
import com.shai.capitall.data.repository.SpaceRepository
import kotlinx.coroutines.launch

sealed interface SpacesUiState {
    data object Loading : SpacesUiState
    data class Ready(val spaces: List<Space>) : SpacesUiState
    data object Empty : SpacesUiState
    data class Error(val messageRes: Int) : SpacesUiState
}

/** הודעה חד-פעמית למסך (הצלחה/כשל של יצירה או הצטרפות). */
data class SpaceMessage(val messageRes: Int, val argument: String? = null)

class SpacesViewModel(
    private val spaceRepository: SpaceRepository = com.shai.capitall.di.ServiceLocator.spaceRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _state = MutableLiveData<SpacesUiState>(SpacesUiState.Loading)
    val state: LiveData<SpacesUiState> = _state

    private val _message = MutableLiveData<SpaceMessage?>()
    val message: LiveData<SpaceMessage?> = _message

    private val _busy = MutableLiveData(false)
    val busy: LiveData<Boolean> = _busy

    /** התיק שזה עתה נוצר — המסך מציג עליו את דיאלוג קוד ההזמנה. */
    private val _createdSpace = MutableLiveData<Space?>()
    val createdSpace: LiveData<Space?> = _createdSpace

    init {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _state.value = SpacesUiState.Error(R.string.add_entry_error_no_user)
        } else {
            viewModelScope.launch {
                spaceRepository.observeSpaces(userId).collect { spaces ->
                    _state.value =
                        if (spaces.isEmpty()) SpacesUiState.Empty else SpacesUiState.Ready(spaces)
                }
            }
        }
    }

    fun createSpace(name: String) {
        val user = auth.currentUser ?: return
        if (name.isBlank()) {
            _message.value = SpaceMessage(R.string.spaces_error_name_required)
            return
        }
        _busy.value = true
        viewModelScope.launch {
            // try/finally כדי שהספינר לא ייתקע גם אם משהו בלתי צפוי נזרק בדרך
            try {
                when (val result = spaceRepository.createSpace(name, user.uid, displayName())) {
                    // בהצלחה לא מוצגת הודעה חולפת — המסך פותח את דיאלוג הקוד, שנשאר
                    // עד שהמשתמש סוגר אותו ומאפשר להעתיק או לשתף
                    is CreateResult.Success -> _createdSpace.value = result.space
                    CreateResult.PermissionDenied ->
                        _message.value = SpaceMessage(R.string.spaces_error_permission)
                    CreateResult.Failed ->
                        _message.value = SpaceMessage(R.string.spaces_error_create_failed)
                }
            } finally {
                _busy.value = false
            }
        }
    }

    fun joinSpace(code: String) {
        val user = auth.currentUser ?: return
        _busy.value = true
        viewModelScope.launch {
            try {
                _message.value = when (val result = spaceRepository.joinSpace(code, user.uid, displayName())) {
                    is JoinResult.Success -> SpaceMessage(R.string.spaces_joined, result.space.name)
                    is JoinResult.AlreadyMember -> SpaceMessage(R.string.spaces_already_member, result.space.name)
                    JoinResult.NotFound -> SpaceMessage(R.string.spaces_error_code_not_found)
                    JoinResult.PermissionDenied -> SpaceMessage(R.string.spaces_error_permission)
                    JoinResult.Failed -> SpaceMessage(R.string.spaces_error_join_failed)
                }
            } finally {
                _busy.value = false
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    /** נקרא אחרי שהמסך הציג את דיאלוג הקוד, כדי שסיבוב מסך לא יפתח אותו שוב. */
    fun consumeCreatedSpace() {
        _createdSpace.value = null
    }

    /** השם שיוצג לחברי התיק. נופל לאימייל ואז לטקסט גנרי, כדי שלא יוצג מזהה גולמי. */
    private fun displayName(): String {
        val user = auth.currentUser ?: return ""
        return user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore('@')?.takeIf { it.isNotBlank() }
            ?: "—"
    }
}
