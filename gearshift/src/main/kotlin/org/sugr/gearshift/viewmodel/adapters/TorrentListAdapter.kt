package org.sugr.gearshift.viewmodel.adapters

import android.support.v7.util.BatchingListUpdateCallback
import android.support.v7.util.ListUpdateCallback
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import io.reactivex.Observable
import io.reactivex.functions.Consumer
import org.sugr.gearshift.Logger
import org.sugr.gearshift.databinding.TorrentListItemBinding
import org.sugr.gearshift.model.Session
import org.sugr.gearshift.model.Torrent
import org.sugr.gearshift.viewmodel.TorrentViewModel
import java.util.*

class TorrentListAdapter(torrentsObservable: Observable<List<Torrent>>,
                         sessionObservable: Observable<Session>,
                         log : Logger,
                         private val viewModelManager: TorrentViewModelManager,
                         private val inflater : LayoutInflater,
                         private val clickListener: Consumer<Torrent>?):
        RecyclerView.Adapter<TorrentListViewHolder>() {

    val torrents : MutableList<Torrent> = ArrayList()
    val batch = BatchingListUpdateCallback(object: ListUpdateCallback {
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

    init {
        setHasStableIds(true)

        torrentsObservable
                .subscribe({ torrentList ->
                    if (torrentList.isEmpty()) {
                        val size = torrents.size
                        torrents.clear()
                        notifyItemRangeRemoved(0, size)
                    } else {
                        val now = Date().time
                        val newHashes = torrentList.map { it.hash }.toSet()

                        torrents.mapIndexed { i, torrent ->
                            if (torrent.hash in newHashes) -1 else i
                        }.filter { it != -1 }.sortedDescending().forEach { index ->
                            torrents.removeAt(index)
                            batch.onRemoved(index, 1)
                        }

                        val currentHashes = torrents.map { it.hash }.toSet()

                        torrentList.forEachIndexed { i, torrent ->
                            if (torrent.hash in currentHashes) {
                                if (torrents[i].hash == torrent.hash) {
                                    torrents[i] = torrent
                                    viewModelManager.getViewModel(torrent.hash).updateTorrent(torrent)
                                } else {
                                    torrents[i] = torrent
                                    batch.onChanged(i, 1, null)
                                }
                            } else { //if (torrentChanged(current, torrent)) {
                                torrents.add(i, torrent)
                                batch.onInserted(i, 1)
                            }
                        }

                        batch.dispatchLastEvent()
                        log.D("Time to merge old and new torrent list: ${Date().time - now}")
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
        return torrents.size
    }

    override fun getItemId(position: Int): Long {
        return torrents[position].hash.hashCode().toLong()
    }

    private fun torrentChanged(oldItem: Torrent, newItem: Torrent): Boolean {
        return oldItem.isDirectory == newItem.isDirectory &&
                oldItem.downloadProgress == newItem.downloadProgress &&
                oldItem.uploadProgress == newItem.uploadProgress &&
                oldItem.name == newItem.name &&
                oldItem.trafficText == newItem.trafficText &&
                oldItem.statusText == newItem.statusText &&
                oldItem.error == newItem.error
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
