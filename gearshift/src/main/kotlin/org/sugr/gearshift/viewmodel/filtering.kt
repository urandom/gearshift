package org.sugr.gearshift.viewmodel

data class Filtering(val query: String, val status: FilterStatus, val directory: String, val tracker: String)

enum class FilterStatus {
	ALL, DOWNLOADING, SEEDING, PAUSED, COMPLETE, INCOMPLETE, ACTIVE, CHECKING, ERRORS;

	fun asFilter() = Filter.Status(this)
}

enum class FilterHeaderType {
	STATUS, DIRECTORIES, TRACKERS
}

sealed class Filter {
	class Header(val value: String, val forType: FilterHeaderType) : Filter()
	class Status(val value: FilterStatus, var active: Boolean = false) : Filter()
	class Directory(val value: String, var active: Boolean = false): Filter()
	class Tracker(val value: String, var active: Boolean = false): Filter()
}
