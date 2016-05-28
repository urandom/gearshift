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
import org.sugr.gearshift.App
import org.sugr.gearshift.R
import org.sugr.gearshift.databinding.MainNavigationActivityBinding
import org.sugr.gearshift.logD
import org.sugr.gearshift.ui.view.*
import org.sugr.gearshift.viewmodel.MainNavigationViewModel
import org.sugr.gearshift.viewmodel.viewModelFrom

class MainNavigationActivity : AppCompatActivity(), MainNavigationViewModel.Consumer, View.OnClickListener {
    lateinit private var binding : MainNavigationActivityBinding
    lateinit private var viewModel : MainNavigationViewModel
    lateinit private var toolbarToggle : DrawerArrowDrawable

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

    private @LayoutRes val defaultContent = R.layout.torrent_list_content
    private val contentIndex = 1

    private val contentHierarchy = mutableListOf(defaultContent)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = viewModelFrom(fragmentManager) { tag, prefs ->
            MainNavigationViewModel(tag, prefs)
        }
        binding = DataBindingUtil.setContentView<MainNavigationActivityBinding>(this, R.layout.main_navigation_activity)
        binding.viewModel = viewModel

        binding.appBar.toolbar.inflateMenu(R.menu.torrent)

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

    override fun createProfile() {
        setContent(R.layout.first_time_profile_editor)
    }

    override fun onClick(v: View?) {
        if (contentHierarchy.size > 1) {
            val existing = getContent()
            if (existing is DetachBlocker && !existing.canDetach()) {
                return
            }

            contentHierarchy.removeAt(contentHierarchy.size - 1)
            setContent(contentHierarchy[contentHierarchy.size - 1])

            if (contentHierarchy.size == 1) {
                toggleDrawable(false)
            }
        } else {
            toggleDrawer()
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

    private fun setContent(@LayoutRes layout : Int) {
        val existing = getContent()
        val existingDepth = if (existing == null) Depth.TOP_LEVEL else viewDepth(existing)

        val content = layoutInflater.inflate(layout, binding.appBar.viewContainer, false)
        val contentDepth = viewDepth(content)

        if (existingDepth >= contentDepth && existing is ViewDestructor) {
            existing.onDestroy()
        }

        binding.drawer.setDrawerLockMode(
                if (contentDepth > Depth.TOP_LEVEL) DrawerLayout.LOCK_MODE_LOCKED_CLOSED
                else DrawerLayout.LOCK_MODE_UNLOCKED,
                GravityCompat.START
        )


        if (existing != null) {
            binding.appBar.viewContainer.removeViewAt(contentIndex)
        }
        binding.appBar.viewContainer.addView(content, contentIndex)

        if (existingDepth > contentDepth) {
            contentHierarchy.removeAt(contentHierarchy.size - 1)
        } else if (existingDepth == contentDepth) {
            contentHierarchy[contentHierarchy.size - 1] = layout
        } else {
            contentHierarchy.add(layout)
        }

        toggleDrawable(contentDepth > Depth.TOP_LEVEL)
    }

    private fun getContent() : View? {
        val content = binding.appBar.viewContainer.getChildAt(contentIndex)
        return if (content?.tag == getString(R.string.container_chrome)) null else content
    }
}
