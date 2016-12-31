package org.sugr.gearshift.viewmodel.databinding

import android.databinding.ObservableInt
import android.view.View
import android.widget.AdapterView

class SelectionListener(val position: ObservableInt = ObservableInt(-1)) : AdapterView.OnItemSelectedListener {
    override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
        position.set(pos)
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        position.set(-1)
    }

    fun observe() = position.observe()
}

