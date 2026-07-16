package com.example.calories.ui.onboarding

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.calories.R
import com.example.calories.databinding.FragmentOnboardingBasicBinding
import com.example.calories.model.enums.Gender
import com.example.calories.ui.common.collectLatestStarted
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OnboardingBasicFragment : Fragment() {

    private var _binding: FragmentOnboardingBasicBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OnboardingViewModel by activityViewModels()

    private var didPrefill = false
    private var suppressWatcher = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentOnboardingBasicBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tgGender.check(R.id.btnMale)
        binding.tgGender.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || suppressWatcher) return@addOnButtonCheckedListener
            viewModel.updateGender(
                if (checkedId == R.id.btnFemale) Gender.FEMALE else Gender.MALE,
            )
        }
        binding.etAge.addTextChangedListener(simpleWatcher {
            if (suppressWatcher) return@simpleWatcher
            viewModel.clearFieldError(OnboardingField.AGE)
            viewModel.updateAge(binding.etAge.text?.toString()?.toIntOrNull())
        })
        viewLifecycleOwner.collectLatestStarted(viewModel.formState) { state ->
            if (state.isPrefillReady && !didPrefill) {
                didPrefill = true
                suppressWatcher = true
                binding.tgGender.check(
                    if (state.gender == Gender.FEMALE) R.id.btnFemale else R.id.btnMale,
                )
                binding.etAge.setText(state.age?.toString().orEmpty())
                suppressWatcher = false
            }
            binding.tilAge.error = state.ageErrorRes?.let { getString(it) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

internal fun simpleWatcher(onChanged: () -> Unit) = object : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
    override fun afterTextChanged(s: Editable?) = onChanged()
}
