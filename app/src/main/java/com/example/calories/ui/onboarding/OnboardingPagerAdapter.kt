package com.example.calories.ui.onboarding

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class OnboardingPagerAdapter(
    activity: FragmentActivity,
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = PAGE_COUNT

    override fun createFragment(position: Int): Fragment = when (position) {
        PAGE_BASIC -> OnboardingBasicFragment()
        PAGE_BODY -> OnboardingBodyFragment()
        PAGE_GOALS -> OnboardingGoalsFragment()
        PAGE_PLAN -> OnboardingPlanFragment()
        else -> error("Unknown onboarding page: $position")
    }

    companion object {
        const val PAGE_COUNT = 4
        const val PAGE_BASIC = 0
        const val PAGE_BODY = 1
        const val PAGE_GOALS = 2
        const val PAGE_PLAN = 3
    }
}
