package org.sugr.gearshift.ui.path

import org.sugr.gearshift.R
import org.sugr.gearshift.ui.NavComponent
import org.sugr.gearshift.viewmodel.ProfileEditorViewModel
import org.sugr.gearshift.viewmodel.viewModelFrom

class FirstTimeProfileEditorPath(navComponent: NavComponent,
                                 val component: FirstTimeProfileEditorComponent = FirstTimeProfileEditorComponentImpl(navComponent)) :
        Path<ProfileEditorViewModel> {
    override val viewModel: ProfileEditorViewModel
        get() = component.viewModel

    override val layout = R.layout.first_time_profile_editor
    override val depth = 1
    override val title = R.string.new_profile
    override val menu = R.menu.first_time_profile_editor
}

interface FirstTimeProfileEditorComponent : NavComponent {
    val viewModel : ProfileEditorViewModel
}

class FirstTimeProfileEditorComponentImpl(b : NavComponent) : FirstTimeProfileEditorComponent, NavComponent by b {
    private val tag = ProfileEditorViewModel::class.java.toString()

    override val viewModel : ProfileEditorViewModel by lazy {
        viewModelFrom(fragmentManager, tag) {
            ProfileEditorViewModel(tag, log, context, prefs, gson, fragmentManager)
        }
    }
}
