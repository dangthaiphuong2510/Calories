package com.example.calories.ui.food

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.calories.R
import com.example.calories.databinding.ItemFoodDictionaryBinding
import com.example.calories.databinding.ItemNativeAdBinding
import com.example.calories.model.FoodDictionaryItem
import com.google.android.gms.ads.nativead.NativeAd

sealed class DisplayItem {
    data class Food(val food: FoodDictionaryItem) : DisplayItem()

    class Ad(val nativeAd: NativeAd) : DisplayItem() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Ad) return false
            return nativeAd.headline == other.nativeAd.headline
        }

        override fun hashCode(): Int {
            return nativeAd.headline?.hashCode() ?: 0
        }
    }
}

class FoodDictionaryAdapter(
    private val onItemClick: (FoodDictionaryItem) -> Unit,
) : ListAdapter<DisplayItem, RecyclerView.ViewHolder>(DiffCallback) {
    companion object {
        private const val VIEW_TYPE_FOOD = 0
        private const val VIEW_TYPE_NATIVE_AD = 1

        private val DiffCallback = object : DiffUtil.ItemCallback<DisplayItem>() {
            override fun areItemsTheSame(
                oldItem: DisplayItem,
                newItem: DisplayItem,
            ): Boolean {
                return when {
                    oldItem is DisplayItem.Food && newItem is DisplayItem.Food ->
                        oldItem.food.id == newItem.food.id
                    oldItem is DisplayItem.Ad && newItem is DisplayItem.Ad ->
                        oldItem.nativeAd === newItem.nativeAd
                    else -> false
                }
            }

            override fun areContentsTheSame(
                oldItem: DisplayItem,
                newItem: DisplayItem,
            ): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is DisplayItem.Food -> VIEW_TYPE_FOOD
            is DisplayItem.Ad -> VIEW_TYPE_NATIVE_AD
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_NATIVE_AD) {
            val binding = ItemNativeAdBinding.inflate(inflater, parent, false)
            NativeAdViewHolder(binding)
        } else {
            val binding = ItemFoodDictionaryBinding.inflate(inflater, parent, false)
            FoodViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is DisplayItem.Food -> (holder as FoodViewHolder).bind(item.food)
            is DisplayItem.Ad -> (holder as NativeAdViewHolder).bind(item.nativeAd)
        }
    }

    inner class FoodViewHolder(
        private val binding: ItemFoodDictionaryBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FoodDictionaryItem) {
            val context = binding.root.context
            binding.tvFoodName.text = item.name
            binding.tvFoodMeta.text = context.getString(
                R.string.search_food_meta_format,
                context.getString(R.string.default_portion_100g),
                item.caloriesInt,
            )
            binding.root.setOnClickListener { onItemClick(item) }
            binding.btnAddFood.setOnClickListener { onItemClick(item) }
        }
    }

    inner class NativeAdViewHolder(
        private val binding: ItemNativeAdBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(nativeAd: NativeAd) {
            val adView = binding.nativeAdView

            adView.headlineView = binding.adHeadline
            adView.bodyView = binding.adBody
            adView.callToActionView = binding.adCallToAction
            adView.iconView = binding.adAppIcon

            binding.adHeadline.text = nativeAd.headline

            if (nativeAd.body != null) {
                binding.adBody.text = nativeAd.body
                binding.adBody.visibility = View.VISIBLE
            } else {
                binding.adBody.visibility = View.GONE
            }

            if (nativeAd.callToAction != null) {
                binding.adCallToAction.text = nativeAd.callToAction
                binding.adCallToAction.visibility = View.VISIBLE
            } else {
                binding.adCallToAction.visibility = View.GONE
            }

            if (nativeAd.icon != null) {
                binding.adAppIcon.setImageDrawable(nativeAd.icon?.drawable)
                binding.adAppIcon.visibility = View.VISIBLE
            } else {
                binding.adAppIcon.visibility = View.GONE
            }

            adView.setNativeAd(nativeAd)
        }
    }
}