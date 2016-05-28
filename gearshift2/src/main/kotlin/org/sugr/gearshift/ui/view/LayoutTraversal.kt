package org.sugr.gearshift.ui.view

import android.app.Activity
import android.content.ContextWrapper
import android.view.View

fun <V : View> V.getActivity() : Activity {
    var context = getContext()
    while (context is ContextWrapper) {
        // Non-compat context wrappers can be activities
        if (context is Activity) {
            break
        }

        context = context.baseContext
    }

    if (context is Activity) {
        return context
    }

    throw RuntimeException("An attached view will always have an activity context")
}

// Various checks to see if the view is actually part of the regular lifecycle, and not just part of a transition
fun <V: View> V.inLayout() : Boolean {
        // Do nothing if the view is initialized by android studio for its preview feature.
        // The tag is needed by the binding, and may be null when the view is attached
        // for animation purposes, such as when leaving the activity.
        return !isInEditMode() && getTag() != null;
}

