package org.sugr.gearshift.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.annotation.LayoutRes
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.AppCompatActivity
import android.support.v7.graphics.drawable.DrawerArrowDrawable
import android.view.View
import android.view.ViewGroup
import org.sugr.gearshift.R
import org.sugr.gearshift.databinding.MainNavigationActivityBinding
import org.sugr.gearshift.ui.view.*
import org.sugr.gearshift.viewmodel.MainNavigationViewModel
import org.sugr.gearshift.viewmodel.viewModelFrom
import rx.internal.operators.OperatorOnBackpressureBuffer

class MainNavigationActivity : AppCompatActivity(), MainNavigationViewModel.Consumer, View.OnClickListener, ViewNavigator {
    lateinit private var binding : MainNavigationActivityBinding
    lateinit private var viewModel : MainNavigationViewModel
    lateinit private var toolbarToggle : DrawerArrowDrawable

    @LayoutRes override val defaultContent = R.layout.torrent_list_content

    override lateinit var viewContainer : ViewGroup
    override val contentIndex = 1
    override val contentHierarchy = mutableListOf(defaultContent)

    private var toolbarToggleAnimatorReversed = false
    private val toolbarToggleAnimator = lazy {
        ValueAnimator.ofFloat(0f, 1f).apply {
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    super.onAnimationEnd(animation)
                    if (toolbarToggleAnimatorReversed) {
                        toolbarToggle.progress = 0f
                        toolbarToggle.setVerticalMirror(true);
                    } else {
                        toolbarToggle.progress = 1f
                        toolbarToggle.setVerticalMirror(true);
                    }
                }
            })
            addUpdateListener { a ->
                toolbarToggle.progress = a.animatedValue as? Float ?: 1f
            }

            duration = resources.getInteger(android.R.integer.config_longAnimTime).toLong()
            startDelay = 100
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = viewModelFrom(fragmentManager) { tag, prefs ->
            MainNavigationViewModel(tag, prefs)
        }
        binding = DataBindingUtil.setContentView<MainNavigationActivityBinding>(this, R.layout.main_navigation_activity)
        binding.viewModel = viewModel

        binding.appBar.toolbar.inflateMenu(R.menu.torrent)

        viewContainer = binding.appBar.viewContainer

        toolbarToggle = DrawerArrowDrawable(binding.appBar.toolbar.getContext())
        binding.appBar.toolbar.navigationIcon = toolbarToggle
        binding.appBar.toolbar.setNavigationOnClickListener(this)

        viewModel.bind(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.unbind()
    }

    override fun onBackPressed() {
        navigateUp(true)
    }

    override fun closeDrawer() {
        binding.drawer.closeDrawer(GravityCompat.START)
    }

    override fun createProfile() {
        setContent(R.layout.first_time_profile_editor)
    }

    override fun onClick(v: View?) {
        navigateUp()
    }

    override fun getContent() : View? {
        val content = binding.appBar.viewContainer.getChildAt(contentIndex)
        return if (content?.tag == getString(R.string.container_chrome)) null else content
    }

    override fun onSetContent(oldDepth: Int, newDepth: Int) {
        binding.drawer.setDrawerLockMode(
                if (newDepth > Depth.TOP_LEVEL) DrawerLayout.LOCK_MODE_LOCKED_CLOSED
                else DrawerLayout.LOCK_MODE_UNLOCKED,
                GravityCompat.START
        )


        toggleDrawable(newDepth > Depth.TOP_LEVEL)
    }

    override fun onNavigateUp(didNavigate: Boolean, fromBackButton: Boolean) {
        if (didNavigate) {
            if (contentHierarchy.size == 1) {
                toggleDrawable(false)
            }
        } else {
            val drawer = binding.drawer
            if (drawer.isDrawerVisible(GravityCompat.START) || !fromBackButton) {
                toggleDrawer()
            } else {
                super.onBackPressed()
            }
        }
    }

    private fun toggleDrawer() {
        val lock = binding.drawer.getDrawerLockMode(GravityCompat.START)
        if (binding.drawer.isDrawerVisible(GravityCompat.START) && lock != DrawerLayout.LOCK_MODE_LOCKED_OPEN) {
            binding.drawer.closeDrawer(GravityCompat.START)
        } else if (lock != DrawerLayout.LOCK_MODE_LOCKED_CLOSED) {
            binding.drawer.openDrawer(GravityCompat.START)
        }
    }

    private fun toggleDrawable(toArrow : Boolean) {
        toolbarToggleAnimator.value.cancel()
        if (toolbarToggle.progress != (if (toArrow) 1f else 0f)) {
            toolbarToggleAnimatorReversed = !toArrow
            if (toArrow) {
                toolbarToggleAnimator.value.start()
            } else {
                toolbarToggleAnimator.value.reverse()
            }
        }
    }

}
