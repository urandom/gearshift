package org.sugr.gearshift.viewmodel

data class Filtering(val query: String, val status: FilterStatus)

enum class FilterStatus {
	ALL, DOWNLOADING, SEEDING, PAUSED, COMPLETE, INCOMPLETE, ACTIVE, CHECKING, ERRORS;

	fun asFilter() = Filter.Status(this)
}

sealed class Filter {
	class Header(val value: String) : Filter()
	class Status(val value: FilterStatus) : Filter()
	class Directory(val value: String): Filter()
	class Tracker(val value: String): Filter()
}

