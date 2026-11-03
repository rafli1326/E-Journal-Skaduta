package com.ejournal.skaduta.ui.screens.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ejournal.skaduta.data.local.AppDatabase
import com.ejournal.skaduta.data.local.entity.UserProfile
import com.ejournal.skaduta.data.repository.JournalRepository
import com.ejournal.skaduta.worker.DailyReminderWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: JournalRepository

    // Staf TU
    private val _fullName = MutableStateFlow("")
    val fullName: StateFlow<String> = _fullName

    private val _employeeId = MutableStateFlow("")
    val employeeId: StateFlow<String> = _employeeId

    private val _position = MutableStateFlow("Staf Tata Usaha")
    val position: StateFlow<String> = _position

    private val _schoolName = MutableStateFlow("SMK Negeri 2 Tanjung")
    val schoolName: StateFlow<String> = _schoolName

    private val _department = MutableStateFlow("Tata Usaha")
    val department: StateFlow<String> = _department

    private val _signatureCity = MutableStateFlow("Tabalong")
    val signatureCity: StateFlow<String> = _signatureCity

    // Kasubbag TU
    private val _subbagName = MutableStateFlow("Erliana Indarti, S.Pd., M.M.")
    val subbagName: StateFlow<String> = _subbagName

    private val _subbagId = MutableStateFlow("198012172006042010")
    val subbagId: StateFlow<String> = _subbagId

    private val _subbagPosition = MutableStateFlow("Kepala Sub Bagian Tata Usaha")
    val subbagPosition: StateFlow<String> = _subbagPosition

    // Kepala Sekolah
    private val _headmasterName = MutableStateFlow("Bainah, S.Pd., M.A.Pd.")
    val headmasterName: StateFlow<String> = _headmasterName

    private val _headmasterId = MutableStateFlow("196812061994122003")
    val headmasterId: StateFlow<String> = _headmasterId

    private val _headmasterPosition = MutableStateFlow("Kepala Sekolah")
    val headmasterPosition: StateFlow<String> = _headmasterPosition

    // Reminder
    private val _reminderEnabled = MutableStateFlow(true)
    val reminderEnabled: StateFlow<Boolean> = _reminderEnabled

    private val _reminderHour = MutableStateFlow(16)
    val reminderHour: StateFlow<Int> = _reminderHour

    private val _reminderMinute = MutableStateFlow(30)
    val reminderMinute: StateFlow<Int> = _reminderMinute

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess

    init {
        val db = AppDatabase.getInstance(application)
        repository = JournalRepository(db.journalDao(), db.profileDao())
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            val p = repository.getProfileSync()
            if (p != null) {
                _fullName.value = p.fullName
                _employeeId.value = p.employeeId
                _position.value = p.position.ifBlank { "Staf Tata Usaha" }
                _schoolName.value = p.schoolName.ifBlank { "SMK Negeri 2 Tanjung" }
                _department.value = p.department.ifBlank { "Tata Usaha" }
                _signatureCity.value = if (p.signatureCity.isBlank() || p.signatureCity == "Jakarta" || p.signatureCity == "Tanjung") "Tabalong" else p.signatureCity

                _subbagName.value = p.subbagName.ifBlank { "Erliana Indarti, S.Pd., M.M." }
                _subbagId.value = p.subbagId.ifBlank { "198012172006042010" }
                _subbagPosition.value = p.subbagPosition.ifBlank { "Kepala Sub Bagian Tata Usaha" }

                _headmasterName.value = p.headmasterName.ifBlank { "Bainah, S.Pd., M.A.Pd." }
                _headmasterId.value = p.headmasterId.ifBlank { "196812061994122003" }
                _headmasterPosition.value = p.headmasterPosition.ifBlank { "Kepala Sekolah" }

                _reminderEnabled.value = p.reminderEnabled
                _reminderHour.value = p.reminderHour
                _reminderMinute.value = p.reminderMinute
            }
        }
    }

    fun setFullName(v: String) { _fullName.value = v.take(150) }
    fun setEmployeeId(v: String) { _employeeId.value = v.take(50) }
    fun setPosition(v: String) { _position.value = v.take(150) }
    fun setSchoolName(v: String) { _schoolName.value = v.take(150) }
    fun setDepartment(v: String) { _department.value = v.take(150) }
    fun setSignatureCity(v: String) { _signatureCity.value = v.take(100) }

    fun setSubbagName(v: String) { _subbagName.value = v.take(150) }
    fun setSubbagId(v: String) { _subbagId.value = v.take(50) }
    fun setSubbagPosition(v: String) { _subbagPosition.value = v.take(150) }

    fun setHeadmasterName(v: String) { _headmasterName.value = v.take(150) }
    fun setHeadmasterId(v: String) { _headmasterId.value = v.take(50) }
    fun setHeadmasterPosition(v: String) { _headmasterPosition.value = v.take(150) }

    fun setReminderEnabled(v: Boolean) { _reminderEnabled.value = v }
    fun setReminderTime(hour: Int, minute: Int) {
        _reminderHour.value = hour.coerceIn(0, 23)
        _reminderMinute.value = minute.coerceIn(0, 59)
    }

    fun saveProfile() {
        viewModelScope.launch {
            val profile = UserProfile(
                id = 1,
                fullName = _fullName.value.trim(),
                employeeId = _employeeId.value.trim(),
                position = _position.value.trim().ifBlank { "Staf Tata Usaha" },
                schoolName = _schoolName.value.trim().ifBlank { "SMK Negeri 2 Tanjung" },
                department = _department.value.trim().ifBlank { "Tata Usaha" },
                signatureCity = _signatureCity.value.trim().ifBlank { "Tabalong" },

                subbagName = _subbagName.value.trim().ifBlank { "Erliana Indarti, S.Pd., M.M." },
                subbagId = _subbagId.value.trim().ifBlank { "198012172006042010" },
                subbagPosition = _subbagPosition.value.trim().ifBlank { "Kepala Sub Bagian Tata Usaha" },

                headmasterName = _headmasterName.value.trim().ifBlank { "Bainah, S.Pd., M.A.Pd." },
                headmasterId = _headmasterId.value.trim().ifBlank { "196812061994122003" },
                headmasterPosition = _headmasterPosition.value.trim().ifBlank { "Kepala Sekolah" },

                reminderEnabled = _reminderEnabled.value,
                reminderHour = _reminderHour.value,
                reminderMinute = _reminderMinute.value
            )
            repository.saveProfile(profile)

            val app = getApplication<Application>()
            if (profile.reminderEnabled) {
                DailyReminderWorker.scheduleReminder(app, profile.reminderHour, profile.reminderMinute)
            } else {
                DailyReminderWorker.cancelReminder(app)
            }

            _saveSuccess.value = true
        }
    }

    fun resetSaveSuccess() {
        _saveSuccess.value = false
    }
}
