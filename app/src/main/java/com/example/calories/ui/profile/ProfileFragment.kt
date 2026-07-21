package com.example.calories.ui.profile

import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.calories.R
import com.example.calories.ads.RewardedInterstitialAdHelper
import com.example.calories.data.preferences.AppLanguage
import com.example.calories.data.preferences.ThemeMode
import com.example.calories.data.preferences.UnitSystem
import com.example.calories.databinding.BottomSheetTermsBinding
import com.example.calories.databinding.FragmentProfileBinding
import com.example.calories.databinding.ItemProfileSettingRowBinding
import com.example.calories.ui.auth.LoginActivity
import com.example.calories.ui.common.collectLatestStarted
import com.example.calories.ui.notifications.NotificationSettingsActivity
import com.example.calories.ui.onboarding.OnboardingActivity
import com.example.calories.util.PdfReportGenerator
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()

    private var rewardedInterstitialAdHelper: RewardedInterstitialAdHelper? = null

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let(viewModel::updateAvatar)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rewardedInterstitialAdHelper = RewardedInterstitialAdHelper(requireActivity()).also { it.preload() }
        setupSettingRows()
        binding.btnEditGoals.setOnClickListener {
            startActivity(Intent(requireContext(), OnboardingActivity::class.java))
        }
        binding.btnEditProfile.setOnClickListener { showEditProfileOptions() }
        binding.ivAvatar.setOnClickListener { showEditProfileOptions() }
        binding.btnExportPdf.setOnClickListener { showReportRangeDialog() }
        observeViewModel()
    }

    private fun setupSettingRows() {
        bindSettingRow(
            binding.rowNotifications,
            iconRes = R.drawable.ic_notifications_24,
            titleRes = R.string.notification_settings_row,
        ) {
            startActivity(Intent(requireContext(), NotificationSettingsActivity::class.java))
        }
        bindSettingRow(
            binding.rowUnitSystem,
            iconRes = R.drawable.ic_straighten_24,
            titleRes = R.string.unit_system,
        ) { showUnitSystemDialog() }
        bindSettingRow(
            binding.rowThemeMode,
            iconRes = R.drawable.ic_palette_24,
            titleRes = R.string.theme_mode,
        ) { showThemeModeDialog() }
        bindSettingRow(
            binding.rowLanguage,
            iconRes = R.drawable.ic_language_24,
            titleRes = R.string.language,
        ) { showLanguageDialog() }
        bindSettingRow(
            binding.rowTerms,
            iconRes = R.drawable.ic_description_24,
            titleRes = R.string.terms_of_service,
        ) { showTermsBottomSheet() }
        bindSettingRow(
            binding.rowDeleteData,
            iconRes = R.drawable.ic_delete_24,
            titleRes = R.string.delete_your_data,
            titleColorRes = R.color.error,
            iconTintRes = R.color.error,
            showChevron = true,
        ) { showDeleteDataDialog() }
        bindSettingRow(
            binding.rowSignOut,
            iconRes = R.drawable.ic_logout_24,
            titleRes = R.string.sign_out,
            titleColorRes = R.color.error,
            iconTintRes = R.color.error,
            showChevron = true,
        ) { showSignOutDialog() }
    }

    private fun bindSettingRow(
        row: ItemProfileSettingRowBinding,
        iconRes: Int,
        titleRes: Int,
        titleColorRes: Int = R.color.text_primary,
        iconTintRes: Int = R.color.primary,
        showChevron: Boolean = true,
        onClick: () -> Unit,
    ) {
        row.ivSettingIcon.setImageResource(iconRes)
        row.ivSettingIcon.setColorFilter(ContextCompat.getColor(requireContext(), iconTintRes))
        row.tvSettingTitle.setText(titleRes)
        row.tvSettingTitle.setTextColor(ContextCompat.getColor(requireContext(), titleColorRes))
        row.ivSettingChevron.isVisible = showChevron
        row.root.setOnClickListener { onClick() }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.collectLatestStarted(viewModel.uiState) { state ->
            binding.tvUserName.text = state.userName
            binding.tvUserEmail.text = state.userEmail
            bindAvatar(state.avatarUrl)

            binding.tvHeight.text = state.heightText
            binding.tvWeight.text = state.weightText
            binding.tvAge.text = state.ageText
            binding.tvGender.text = state.genderTextRes?.let { getString(it) } ?: getString(R.string.em_dash)
            binding.tvBmi.text = if (state.bmiValue != null && state.bmiCategoryRes != null) {
                getString(R.string.bmi_format, state.bmiValue, getString(state.bmiCategoryRes))
            } else {
                getString(R.string.bmi_unavailable)
            }

            val goal = state.goal
            if (goal == null) {
                binding.tvDailyCalories.text = getString(R.string.em_dash)
                binding.tvGoalType.text = getString(R.string.em_dash)
                binding.tvActivityLevel.text = getString(R.string.em_dash)
            } else {
                binding.tvDailyCalories.text = goal.dailyCalories.toString()
                binding.tvGoalType.text = getString(viewModel.goalTypeLabelRes(goal.goalType))
                binding.tvActivityLevel.text =
                    getString(viewModel.activityLevelLabelRes(goal.activityLevel))
            }

            binding.rowUnitSystem.tvSettingSubtitle.isVisible = true
            binding.rowUnitSystem.tvSettingSubtitle.text =
                getString(viewModel.unitSystemLabelRes(state.unitSystem))
            binding.rowThemeMode.tvSettingSubtitle.isVisible = true
            binding.rowThemeMode.tvSettingSubtitle.text =
                getString(viewModel.themeModeLabelRes(state.themeMode))
            binding.rowLanguage.tvSettingSubtitle.isVisible = true
            binding.rowLanguage.tvSettingSubtitle.text =
                getString(viewModel.languageLabelRes(state.language))
        }

        viewLifecycleOwner.collectLatestStarted(viewModel.isUpdatingAvatar) { updating ->
            binding.progressAvatar.isVisible = updating
            binding.btnEditProfile.isEnabled = !updating
            binding.ivAvatar.isEnabled = !updating
        }

        viewLifecycleOwner.collectLatestStarted(viewModel.messages) { message ->
            val textRes = when (message) {
                ProfileMessage.AvatarUpdated -> R.string.avatar_updated
                ProfileMessage.AvatarRemoved -> R.string.avatar_removed
                ProfileMessage.AvatarUpdateFailed -> R.string.avatar_update_failed
            }
            Toast.makeText(requireContext(), textRes, Toast.LENGTH_SHORT).show()
        }

        viewLifecycleOwner.collectLatestStarted(viewModel.navEvents) { event ->
            when (event) {
                ProfileNavEvent.SignedOut,
                ProfileNavEvent.DataWiped,
                -> restartToLogin()
            }
        }
    }

    private fun bindAvatar(avatarUrl: String?) {
        if (avatarUrl.isNullOrBlank()) {
            binding.ivAvatar.setImageResource(R.drawable.ic_nav_profile)
            binding.ivAvatar.setColorFilter(ContextCompat.getColor(requireContext(), R.color.primary))
            binding.ivAvatar.setPadding(dp(12), dp(12), dp(12), dp(12))
            return
        }

        binding.ivAvatar.clearColorFilter()
        binding.ivAvatar.setPadding(0, 0, 0, 0)
        val data: Any = when {
            avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://") -> avatarUrl
            else -> File(avatarUrl)
        }
        binding.ivAvatar.load(data) {
            crossfade(true)
            placeholder(R.drawable.ic_nav_profile)
            error(R.drawable.ic_nav_profile)
        }
    }

    private fun showEditProfileOptions() {
        val hasPhoto = !viewModel.uiState.value.avatarUrl.isNullOrBlank()
        val options = buildList {
            add(getString(R.string.change_photo))
            add(getString(R.string.edit_display_name))
            if (hasPhoto) add(getString(R.string.remove_photo))
        }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit_profile)
            .setItems(options) { _, which ->
                when (options[which]) {
                    getString(R.string.change_photo) -> openPhotoPicker()
                    getString(R.string.edit_display_name) -> showEditNameDialog()
                    getString(R.string.remove_photo) -> viewModel.removeAvatar()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openPhotoPicker() {
        pickImage.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    private fun showEditNameDialog() {
        val input = EditText(requireContext()).apply {
            setText(binding.tvUserName.text)
            setSelection(text?.length ?: 0)
            hint = getString(R.string.display_name_hint)
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        val container = FrameLayout(requireContext()).apply {
            addView(input)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit_display_name)
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text?.toString().orEmpty()
                if (name.isNotBlank()) viewModel.updateDisplayName(name)
            }
            .show()
    }

    private fun showUnitSystemDialog() {
        val options = arrayOf(
            getString(R.string.unit_metric),
            getString(R.string.unit_imperial),
        )
        val current = when (viewModel.uiState.value.unitSystem) {
            UnitSystem.METRIC -> 0
            UnitSystem.IMPERIAL -> 1
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.unit_system)
            .setSingleChoiceItems(options, current) { dialog, which ->
                viewModel.setUnitSystem(
                    if (which == 0) UnitSystem.METRIC else UnitSystem.IMPERIAL,
                )
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showThemeModeDialog() {
        val options = arrayOf(
            getString(R.string.theme_light),
            getString(R.string.theme_dark),
            getString(R.string.theme_system),
        )
        val current = when (viewModel.uiState.value.themeMode) {
            ThemeMode.LIGHT -> 0
            ThemeMode.DARK -> 1
            ThemeMode.SYSTEM -> 2
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.theme_mode)
            .setSingleChoiceItems(options, current) { dialog, which ->
                viewModel.setThemeMode(
                    when (which) {
                        0 -> ThemeMode.LIGHT
                        1 -> ThemeMode.DARK
                        else -> ThemeMode.SYSTEM
                    },
                )
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showLanguageDialog() {
        val options = arrayOf(
            getString(R.string.language_english),
            getString(R.string.language_vietnamese),
        )
        val current = when (viewModel.uiState.value.language) {
            AppLanguage.ENGLISH -> 0
            AppLanguage.VIETNAMESE -> 1
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.language)
            .setSingleChoiceItems(options, current) { dialog, which ->
                val selected =
                    if (which == 0) AppLanguage.ENGLISH else AppLanguage.VIETNAMESE
                if (selected != viewModel.uiState.value.language) {
                    viewModel.setLanguage(selected)
                    // Recreate so layout @string resources and getString() pick up the new locale.
                    requireActivity().recreate()
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showTermsBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetBinding = BottomSheetTermsBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)
        sheetBinding.btnCloseTerms.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showDeleteDataDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_data_title)
            .setMessage(R.string.delete_data_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete_data_confirm) { _, _ ->
                viewModel.deleteAllData()
            }
            .show()
            .getButton(DialogInterface.BUTTON_POSITIVE)
            ?.setTextColor(ContextCompat.getColor(requireContext(), R.color.error))
    }

    private fun showSignOutDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sign_out_title)
            .setMessage(R.string.sign_out_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.sign_out) { _, _ -> viewModel.signOut() }
            .show()
            .getButton(DialogInterface.BUTTON_POSITIVE)
            ?.setTextColor(ContextCompat.getColor(requireContext(), R.color.error))
    }

    private fun showReportRangeDialog() {
        val options = arrayOf(
            getString(R.string.report_range_7_days),
            getString(R.string.report_range_30_days),
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.export_pdf_select_range)
            .setItems(options) { _, which ->
                val dayCount = if (which == 0) 7 else 30
                showAdThenExport(dayCount)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAdThenExport(dayCount: Int) {
        val helper = rewardedInterstitialAdHelper
        if (helper == null) {
            generateAndSharePdf(dayCount)
            return
        }
        helper.showAd { generateAndSharePdf(dayCount) }
    }

    private fun generateAndSharePdf(dayCount: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            val report = runCatching { viewModel.buildNutritionReport(dayCount) }
                .getOrNull()
            if (report == null) {
                Toast.makeText(requireContext(), R.string.pdf_export_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }

            val file = runCatching { PdfReportGenerator.generate(requireContext(), report) }
                .getOrElse {
                    Toast.makeText(requireContext(), R.string.pdf_export_failed, Toast.LENGTH_SHORT).show()
                    return@launch
                }

            Toast.makeText(requireContext(), R.string.pdf_export_success, Toast.LENGTH_SHORT).show()
            openPdf(file)
        }
    }

    private fun openPdf(file: File) {
        val context = requireContext()
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.open_pdf)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.pdf_viewer_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun restartToLogin() {
        startActivity(
            Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
        )
        requireActivity().finish()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    override fun onDestroyView() {
        rewardedInterstitialAdHelper?.destroy()
        rewardedInterstitialAdHelper = null
        super.onDestroyView()
        _binding = null
    }
}
