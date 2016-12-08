package org.sugr.gearshift.ui.path

import org.sugr.gearshift.Logger
import org.sugr.gearshift.ui.NavComponent
import org.sugr.gearshift.viewmodel.LeaveBlocker
import org.sugr.gearshift.viewmodel.RetainedViewModel
import org.sugr.gearshift.viewmodel.viewModelFrom

class PathNavigator(private val navComponent: NavComponent,
                    private var consumer: PathNavigator.Consumer) {
    private val component : PathNavigatorComponent = PathNavigatorComponentImpl(navComponent)

    interface Consumer {
        val defaultPath : Lazy<Path<*>>
        fun onSetContent(newPath: Path<*>, oldPath: Path<*>)
    }

    fun restorePath() {
        setPath(component.viewModel.pop() ?: consumer.defaultPath.value)
    }

    fun setPath(path: Path<*>) {
        val current = component.viewModel.last() ?: consumer.defaultPath.value
        val currentDepth = current.depth
        val depth = path.depth

        if (currentDepth >= depth && current !== path) {
            current.destroyViewModel(navComponent.fragmentManager)
        }

        consumer.onSetContent(path, current)

        if (currentDepth > depth) {
            component.viewModel.pop()
        } else if (currentDepth == depth && !component.viewModel.isEmpty()) {
            component.viewModel.replaceLast(path)
        } else {
            component.viewModel.push(path)
        }
    }

    fun navigateUp() : Boolean {
        if (component.viewModel.size() > 1) {
            val vm = component.viewModel.last()?.viewModel
            if (vm is LeaveBlocker && !vm.canLeave()) {
                return false
            }

            setPath(component.viewModel.get(component.viewModel.size() - 2))

            return true
        }

        return false
    }
}

interface PathNavigatorComponent : NavComponent {
    val viewModel: ContentHierarchyViewModel
}

class PathNavigatorComponentImpl(b : NavComponent) : PathNavigatorComponent, NavComponent by b {
    private val tag = ContentHierarchyViewModel::class.java.toString()

    override val viewModel: ContentHierarchyViewModel get() =
        viewModelFrom(fragmentManager, tag) {
            ContentHierarchyViewModel(tag, log)
        }
}

class ContentHierarchyViewModel(tag: String, log: Logger) : RetainedViewModel<Unit>(tag, log) {
    val contentHierarchy = mutableListOf<Path<*>>()

    fun pop() = if (contentHierarchy.size > 0) contentHierarchy.removeAt(contentHierarchy.size - 1) else null
    fun push(path: Path<*>) = contentHierarchy.add(path)
    fun replaceLast(path: Path<*>) = contentHierarchy.set(contentHierarchy.size - 1, path)
    fun get(i: Int) = contentHierarchy.get(i)
    fun last() = contentHierarchy.lastOrNull()
    fun size() = contentHierarchy.size
    fun isEmpty() = contentHierarchy.size == 0
}