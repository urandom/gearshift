package org.sugr.gearshift.viewmodel.adapters

import android.support.v7.util.SortedList
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.util.SortedListAdapterCallback
import android.view.LayoutInflater
import android.view.ViewGroup
import io.reactivex.Observable
import io.reactivex.functions.Consumer
import org.sugr.gearshift.Logger
import org.sugr.gearshift.databinding.TorrentListItemBinding
import org.sugr.gearshift.model.Session
import org.sugr.gearshift.model.Torrent
import org.sugr.gearshift.ui.view.util.asSequence
import org.sugr.gearshift.viewmodel.SortBy
import org.sugr.gearshift.viewmodel.SortDirection
import org.sugr.gearshift.viewmodel.Sorting
import org.sugr.gearshift.viewmodel.TorrentViewModel
import org.sugr.gearshift.viewmodel.rxutil.combineLatestWith

class TorrentListAdapter(torrentsObservable: Observable<Set<Torrent>>,
                         sessionObservable: Observable<Session>,
                         sortingObservable: Observable<Sorting>,
                         log : Logger,
                         private val viewModelManager: TorrentViewModelManager,
                         private val inflater : LayoutInflater,
                         private val clickListener: Consumer<Torrent>?):
        RecyclerView.Adapter<TorrentListViewHolder>() {

    var sorting = Sorting(SortBy.AGE, SortDirection.DESCENDING)
    var globalLimit = 0f

    val sortedListCallback = object : SortedListAdapterCallback<Torrent>(this) {
        override fun compare(t1: Torrent, t2: Torrent): Int {
            val ret = compareWith(t1, t2, sorting.by, sorting.direction)

            return if (ret == 0) {
                compareWith(t1, t2, sorting.baseBy, sorting.baseDirection)
            } else {
                ret
            }
        }

        override fun areItemsTheSame(item1: Torrent, item2: Torrent): Boolean {
            return item1.hash == item2.hash
        }

        override fun areContentsTheSame(oldItem: Torrent, newItem: Torrent): Boolean {
            return oldItem.isDirectory == newItem.isDirectory &&
                    oldItem.downloadProgress == newItem.downloadProgress &&
                    oldItem.uploadProgress == newItem.uploadProgress &&
                    oldItem.name == newItem.name &&
                    oldItem.trafficText == newItem.trafficText &&
                    oldItem.statusText == newItem.statusText &&
                    oldItem.error == newItem.error
        }

        override fun onChanged(position: Int, count: Int) {
            log.D("Changes from ${position} to ${position + count - 1}")
            for (i in position .. position + count - 1) {
                onChanged(i)
            }
        }

        private fun compareWith(t1: Torrent, t2: Torrent, by: SortBy, direction: SortDirection) : Int {
            val ret = when (by) {
                SortBy.NAME -> t1.name.compareTo(t2.name, true)
                SortBy.SIZE -> t1.totalSize.compareTo(t2.totalSize)
                SortBy.STATUS -> t1.statusSortWeight().compareTo(t2.statusSortWeight())
                SortBy.RATE_DOWNLOAD -> t2.downloadRate.compareTo(t1.downloadRate)
                SortBy.RATE_UPLOAD -> t2.uploadRate.compareTo(t1.uploadRate)
                SortBy.AGE -> t1.addedTime.compareTo(t2.addedTime)
                SortBy.PROGRESS -> t1.downloadProgress.compareTo(t2.downloadProgress)
                SortBy.RATIO -> t1.uploadRatio.compareTo(t2.uploadRatio)
                SortBy.ACTIVITY -> (t2.downloadRate + t2.uploadRate).compareTo(t1.downloadRate + t1.uploadRate)
                SortBy.LOCATION -> t1.downloadDir.compareTo(t2.downloadDir, true)
                SortBy.PEERS -> t1.connectedPeers.compareTo(t2.connectedPeers)
                SortBy.QUEUE -> t1.queuePosition.compareTo(t2.queuePosition)
            }

            return when (direction) {
                SortDirection.DESCENDING -> -ret
                SortDirection.ASCENDING -> ret

            }
        }
    }

    val torrents : SortedList<Torrent> = SortedList(Torrent::class.java, sortedListCallback)

    init {
        setHasStableIds(true)

        sortingObservable.combineLatestWith(sessionObservable) { sorting, session ->
            Pair(sorting, session)
        }.subscribe({
            if (it.first != sorting || globalLimit != it.second.seedRatioLimit) {
                sorting = it.first
                globalLimit = it.second.seedRatioLimit

                if (torrents.size() > 0) {
                    torrents.beginBatchedUpdates()
                    torrents.asSequence().forEach { torrent ->
                        torrents.updateItemAt(torrents.indexOf(torrent), torrent)
                    }
                    torrents.endBatchedUpdates()
                }
            }
        }, { err -> log.E("torrent list adapter sorting and session", err) })

        sortingObservable.take(1).flatMap { torrentsObservable }
                .subscribe({ torrentSet ->
                    if (torrentSet.isEmpty()) {
                        torrents.clear()
                    } else {
                        torrents.beginBatchedUpdates()

                        val newHashes = torrentSet.map { it.hash }.toSet()

                        torrents.asSequence().filterNot { torrent ->
                            torrent.hash in newHashes
                        }.mapIndexed { i, torrent -> i }.sortedDescending().forEach { i ->
                            torrents.removeItemAt(i)
                        }

                        torrents.addAll(torrentSet)

                        torrents.endBatchedUpdates()
                    }
                }, { err ->
                    log.E("updating torrent list adapter", err)
                })
    }

    override fun onBindViewHolder(holder: TorrentListViewHolder?, position: Int) {
        val torrent = torrents[position]
        val vm = viewModelManager.getViewModel(torrent.hash)
        vm.updateTorrent(torrent)
        holder?.bindTo(vm)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TorrentListViewHolder {
        return holderOf(inflater, parent, Consumer {
            clickListener?.accept(torrents[it])
        })
    }

    override fun getItemCount(): Int {
        return torrents.size()
    }

    override fun getItemId(position: Int): Long {
        return torrents[position].hash.hashCode().toLong()
    }

    private fun onChanged(i: Int) {
        val torrent = torrents[i]
        viewModelManager.getViewModel(torrent.hash).updateTorrent(torrent)
    }

}

private fun holderOf(inflater: LayoutInflater, root: ViewGroup, listener: Consumer<Int>) : TorrentListViewHolder {
    val binding = TorrentListItemBinding.inflate(inflater, root, false)

    return TorrentListViewHolder(binding, listener)
}

class TorrentListViewHolder(private val binding: TorrentListItemBinding, private val listener: Consumer<Int>): RecyclerView.ViewHolder(binding.root) {
    init {
        binding.root.setOnClickListener {
            listener.accept(adapterPosition)
        }
    }

    fun bindTo(viewModel: TorrentViewModel) {
        binding.viewModel = viewModel
        binding.executePendingBindings()
    }
}


interface TorrentViewModelManager {
    fun getViewModel(hash: String) : TorrentViewModel
    fun removeViewModel(hash: String)
    fun removeAllViewModels()
}
