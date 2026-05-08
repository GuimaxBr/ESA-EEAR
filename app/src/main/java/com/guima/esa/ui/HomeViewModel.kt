package com.guima.esa.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guima.esa.data.ProgressRepository
import com.guima.esa.data.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val dailyGoal: Int = 20,
    val dailyStudyGoalMinutes: Int = 20,
    val todaysCorrectAnswers: Int = 0,
    val todaysStudyTimeMs: Long = 0L,
    val totalCorrectAnswers: Int = 0,
    val bestCorrectAnswerStreak: Int = 0
)

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refreshData()
    }

    fun refreshData() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    dailyGoal = UserRepository.getDailyGoal(),
                    dailyStudyGoalMinutes = UserRepository.getDailyStudyGoalMinutes(),
                    todaysCorrectAnswers = ProgressRepository.getTodaysCorrectAnswers(),
                    todaysStudyTimeMs = UserRepository.getTodaysStudyTimeMs(),
                    totalCorrectAnswers = ProgressRepository.getTotalCorrectAnswers(),
                    bestCorrectAnswerStreak = ProgressRepository.getBestCorrectAnswerStreak()
                )
            }
        }
    }
}
