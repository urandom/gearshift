package org.sugr.gearshift.ui

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.NavigationView
import android.support.design.widget.Snackbar
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import org.sugr.gearshift.R
import org.sugr.gearshift.databinding.TorrentActivityBinding
import org.sugr.gearshift.viewmodel.TorrentViewModel
import org.sugr.gearshift.viewmodel.viewModelFrom

class TorrentActivity : AppCompatActivity(), TorrentViewModel.Consumer {
    lateinit private var binding : TorrentActivityBinding
    lateinit private var viewModel : TorrentViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = viewModelFrom(fragmentManager) {
            prefs -> TorrentViewModel(prefs)
        }
        viewModel.bind(this)
        binding = DataBindingUtil.setContentView<TorrentActivityBinding>(this, R.layout.torrent_activity)
        binding.viewModel = viewModel

        binding.appBar.toolbar.inflateMenu(R.menu.torrent)
        val fab = findViewById(R.id.fab) as FloatingActionButton?
        fab!!.setOnClickListener { view -> Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG).setAction("Action", null).show() }

        val toggle = ActionBarDrawerToggle(
                this, binding.drawer, binding.appBar.toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        binding.drawer.setDrawerListener(toggle)
        toggle.syncState()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.unbind()
    }

    override fun onBackPressed() {
        val drawer = binding.drawer
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun closeDrawer() {
        binding.drawer.closeDrawer(GravityCompat.START)
    }

}
