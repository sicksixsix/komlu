package com.komiklu.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komiklu.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthEvent {
    object Success : AuthEvent()
    data class Error(val message: String) : AuthEvent()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepo: AuthRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _event = MutableSharedFlow<AuthEvent>()
    val event: SharedFlow<AuthEvent> = _event.asSharedFlow()

    fun login(email: String, password: String) {
        if (!validateLogin(email, password)) return
        viewModelScope.launch {
            _isLoading.value = true
            authRepo.login(email, password).fold(
                onSuccess = { _event.emit(AuthEvent.Success) },
                onFailure = { _event.emit(AuthEvent.Error(it.message ?: "Login gagal")) }
            )
            _isLoading.value = false
        }
    }

    fun register(username: String, email: String, password: String) {
        if (!validateRegister(username, email, password)) return
        viewModelScope.launch {
            _isLoading.value = true
            authRepo.register(username, email, password).fold(
                onSuccess = { _event.emit(AuthEvent.Success) },
                onFailure = { _event.emit(AuthEvent.Error(it.message ?: "Registrasi gagal")) }
            )
            _isLoading.value = false
        }
    }

    private fun validateLogin(email: String, password: String): Boolean {
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            viewModelScope.launch { _event.emit(AuthEvent.Error("Email tidak valid")) }
            return false
        }
        if (password.length < 6) {
            viewModelScope.launch { _event.emit(AuthEvent.Error("Password minimal 6 karakter")) }
            return false
        }
        return true
    }

    private fun validateRegister(username: String, email: String, password: String): Boolean {
        if (username.length < 3) {
            viewModelScope.launch { _event.emit(AuthEvent.Error("Username minimal 3 karakter")) }
            return false
        }
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            viewModelScope.launch { _event.emit(AuthEvent.Error("Email tidak valid")) }
            return false
        }
        if (password.length < 6) {
            viewModelScope.launch { _event.emit(AuthEvent.Error("Password minimal 6 karakter")) }
            return false
        }
        return true
    }

    fun isLoggedIn() = authRepo.isLoggedIn()
    fun logout() = authRepo.logout()
    fun getCurrentUser() = authRepo.getCurrentUser()
}

// ─── LOGIN FRAGMENT ───────────────────────────────────────────────────────────

package com.komiklu.app.ui.auth

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import com.komiklu.app.R
import com.komiklu.app.databinding.FragmentLoginBinding
import com.komiklu.app.ui.util.gone
import com.komiklu.app.ui.util.visible
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val vm: AuthViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Jika sudah login, langsung ke home
        if (vm.isLoggedIn()) {
            findNavController().navigate(R.id.action_login_success)
            return
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val pass  = binding.etPassword.text.toString()
            vm.login(email, pass)
        }

        binding.tvGoRegister.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }

        binding.btnGoogleLogin.setOnClickListener {
            // Google Sign-In — implementasi di sini
            showSnackbar("Google login segera hadir!")
        }

        observeEvents()
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    vm.isLoading.collect { loading ->
                        binding.btnLogin.isEnabled = !loading
                        binding.progressLogin.visibility = if (loading) View.VISIBLE else View.GONE
                        binding.btnLogin.text = if (loading) "" else "Masuk"
                    }
                }

                launch {
                    vm.event.collect { event ->
                        when (event) {
                            is AuthEvent.Success -> {
                                findNavController().navigate(R.id.action_login_success)
                            }
                            is AuthEvent.Error -> showSnackbar(event.message)
                        }
                    }
                }
            }
        }
    }

    private fun showSnackbar(msg: String) {
        com.google.android.material.snackbar.Snackbar.make(binding.root, msg, 3000).show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ─── REGISTER FRAGMENT ────────────────────────────────────────────────────────

@AndroidEntryPoint
class RegisterFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null // reuse layout dengan tampilan berbeda
    private val binding get() = _binding!!
    private val vm: AuthViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Tampilkan field username
        binding.tilUsername.visible()
        binding.tvTitle.text = "Buat Akun"
        binding.tvSubtitle.text = "Bergabung dengan komunitas Komiklu"
        binding.btnLogin.text = "Daftar"
        binding.tvGoRegister.text = "Sudah punya akun? Masuk"

        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val email    = binding.etEmail.text.toString().trim()
            val pass     = binding.etPassword.text.toString()
            vm.register(username, email, pass)
        }

        binding.tvGoRegister.setOnClickListener { findNavController().navigateUp() }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.isLoading.collect { loading ->
                        binding.btnLogin.isEnabled = !loading
                    }
                }
                launch {
                    vm.event.collect { event ->
                        when (event) {
                            is AuthEvent.Success ->
                                findNavController().navigate(R.id.action_register_success)
                            is AuthEvent.Error ->
                                com.google.android.material.snackbar.Snackbar
                                    .make(binding.root, event.message, 3000).show()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
