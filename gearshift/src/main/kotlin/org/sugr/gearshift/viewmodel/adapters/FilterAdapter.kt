package org.sugr.gearshift.viewmodel.adapters

import android.support.v7.util.DiffUtil
import android.support.v7.util.ListUpdateCallback
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
import java.util.*

class FilterAdapter(filtersObservable: Observable<List<Filter>>, log: Logger,
					private val inflater: LayoutInflater,
					private val clickListener: Consumer<Filter>?):
		RecyclerView.Adapter<FilterViewHolder>() {

	private val filters : MutableList<Filter> = ArrayList()

	init {
		setHasStableIds(true)

		filtersObservable.observeOn(Schedulers.computation()).map { newList ->
			val res = DiffUtil.calculateDiff(object: DiffUtil.Callback() {
				override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
					return filters[oldItemPosition] == newList[newItemPosition]
				}

				override fun getOldListSize(): Int {
					return filters.size
				}

				override fun getNewListSize(): Int {
					return newList.size
				}

				override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
					return true
				}
			})

			filters.clear()
			filters.addAll(newList)

			res
		}.observeOn(AndroidSchedulers.mainThread()).subscribe({ res ->
			val now = Date().time

			res.dispatchUpdatesTo(object: ListUpdateCallback {
				override fun onChanged(position: Int, count: Int, payload: Any?) {
					notifyItemRangeChanged(position, count, payload)
				}

				override fun onMoved(fromPosition: Int, toPosition: Int) {
					notifyItemMoved(fromPosition, toPosition)
				}

				override fun onInserted(position: Int, count: Int) {
					notifyItemRangeInserted(position, count)
				}

				override fun onRemoved(position: Int, count: Int) {
					notifyItemRangeRemoved(position, count)
				}

			})

			log.D("List update took ${Date().time - now} milliseconds")
		}, { err ->
			log.E("updating filter adapter", err)
		})
	}

	override fun onBindViewHolder(holder: FilterViewHolder?, position: Int) {
		val filter = filters[position]

		holder?.name?.text = when(filter) {
			is Filter.Header -> filter.value
			is Filter.Status -> filter.value.toString()
			is Filter.Directory -> filter.value
			is Filter.Tracker -> filter.value
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
}

