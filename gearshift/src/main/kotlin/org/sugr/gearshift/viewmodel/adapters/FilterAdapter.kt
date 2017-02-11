package org.sugr.gearshift.viewmodel.adapters

import android.content.Context
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers
import org.sugr.gearshift.Logger
import org.sugr.gearshift.R
import org.sugr.gearshift.viewmodel.Filter
import org.sugr.gearshift.viewmodel.FilterHeaderType
import java.util.*

class FilterAdapter(filtersObservable: Observable<List<Filter>>,
					log: Logger,
					private val ctx: Context,
					private val inflater: LayoutInflater,
					private val clickListener: Consumer<Filter>?):
		RecyclerView.Adapter<FilterViewHolder>() {

	private val filters : MutableList<Filter> = ArrayList()

	init {
		setHasStableIds(true)

		filtersObservable.map { list ->
			list.filterIndexed { i, filter ->
				if (filter is Filter.Header) {
					when (filter.forType) {
						FilterHeaderType.STATUS -> i + 1 < list.size && list[i+1] is Filter.Status
						FilterHeaderType.DIRECTORIES -> i + 1 < list.size && list[i+1] is Filter.Directory
						FilterHeaderType.TRACKERS -> i + 1 < list.size && list[i+1] is Filter.Tracker
					}
				} else {
					true
				}
			}
		}.observeOn(Schedulers.computation()).map { newList ->
			val res = DiffUtil.calculateDiff(object: DiffUtil.Callback() {
				override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
					val oldFilter = filters[oldItemPosition]
					val newFilter = newList[newItemPosition]

					return when (oldFilter) {
						is Filter.Header -> newFilter is Filter.Header && oldFilter.value == newFilter.value
						is Filter.Status ->  newFilter is Filter.Status && oldFilter.value == newFilter.value
						is Filter.Directory -> newFilter is Filter.Directory && oldFilter.value == newFilter.value
						is Filter.Tracker -> newFilter is Filter.Tracker && oldFilter.value == newFilter.value
					}
				}

				override fun getOldListSize(): Int {
					return filters.size
				}

				override fun getNewListSize(): Int {
					return newList.size
				}

				override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
					val oldFilter = filters[oldItemPosition]
					val newFilter = newList[newItemPosition]

					return when (oldFilter) {
						is Filter.Header -> true
						is Filter.Status ->  newFilter is Filter.Status && oldFilter.active == newFilter.active
						is Filter.Directory -> newFilter is Filter.Directory && oldFilter.active == newFilter.active
						is Filter.Tracker -> newFilter is Filter.Tracker && oldFilter.active == newFilter.active
					}
				}
			})

			Pair(newList, res)
		}.observeOn(AndroidSchedulers.mainThread()).subscribe({ pair ->
			val now = Date().time

			filters.clear()
			filters.addAll(pair.first)

			pair.second.dispatchUpdatesTo(this)

			log.D("List update took ${Date().time - now} milliseconds")
		}, { err ->
			log.E("updating filter adapter", err)
		})
	}

	override fun onBindViewHolder(holder: FilterViewHolder?, position: Int) {
		val filter = filters[position]

		holder?.name?.text = when(filter) {
			is Filter.Header -> filter.value
			is Filter.Status -> {
				val res = ctx.resources
				val packageName = ctx.packageName

				val identifier = ctx.resources.getIdentifier("filter_status_" + filter.value.name, "string", packageName)
				if (identifier > 0) {
					ctx.getString(identifier)
				} else {
					filter.value.name
				}
			}
			is Filter.Directory -> filter.value
			is Filter.Tracker -> {
				if (filter.value == "") {
					ctx.getString(R.string.filter_tracker_untracked)
				} else {
					filter.value
				}
			}
		}

		holder?.name?.isActivated = when(filter) {
			is Filter.Header -> false
			is Filter.Status -> filter.active
			is Filter.Directory -> filter.active
			is Filter.Tracker -> filter.active
		}
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterViewHolder {
		return holderOf(inflater, viewType, parent, Consumer {
			if (filters.size > it) {
				clickListener?.accept(filters[it])
			}
		})
	}

	override fun getItemCount(): Int {
		return filters.size
	}

	override fun getItemViewType(position: Int): Int {
		return when (filters[position]) {
			is Filter.Header -> R.layout.filter_list_header
			else -> R.layout.filter_list_item
		}
	}

	override fun getItemId(position: Int): Long {
		val filter = filters[position]
		return when (filter) {
			is Filter.Header -> ("header:" + filter.value).hashCode().toLong()
			is Filter.Status -> ("status:" + filter.value.name).hashCode().toLong()
			is Filter.Directory -> ("directory:" + filter.value).hashCode().toLong()
			is Filter.Tracker -> ("tracker:" + filter.value).hashCode().toLong()
		}
	}
}

private fun holderOf(inflater: LayoutInflater,
					 viewType: Int,
					 root: ViewGroup,
					 listener: Consumer<Int>) : FilterViewHolder {
	val view = inflater.inflate(viewType, root, false)

	return FilterViewHolder(view, listener)
}

class FilterViewHolder(private val root: View,
					   private val listener: Consumer<Int>): RecyclerView.ViewHolder(root) {

	val name = root.findViewById(android.R.id.text1) as TextView

	init {
		name.setOnClickListener { listener.accept(adapterPosition) }
	}
}

