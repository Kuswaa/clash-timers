package com.kuswa.clashtimer.ui

import com.kuswa.clashtimer.core.RowKind
import com.kuswa.clashtimer.core.WidgetModel
import com.kuswa.clashtimer.core.WidgetRow

enum class TimerFilter(val label: String) {
    ALL("All"), BUILDERS("Builders"), LAB("Lab"), PETS("Pets"),
}

data class DashboardTimer(val category: TimerFilter, val row: WidgetRow)

/** Presentation only: predicted times and completion state still come from WidgetModelBuilder. */
fun WidgetModel.timers(filter: TimerFilter): List<DashboardTimer> {
    if (!hasData) return emptyList()
    return (builderRows.map { DashboardTimer(TimerFilter.BUILDERS, it) } +
        DashboardTimer(TimerFilter.LAB, labRow) + DashboardTimer(TimerFilter.PETS, petRow))
        .filter { filter == TimerFilter.ALL || it.category == filter }
        .sortedWith(compareBy<DashboardTimer> { it.row.kind.ordinal }.thenBy { it.row.finishAt ?: Long.MAX_VALUE })
}

fun WidgetModel.nextRunningTimer(): DashboardTimer? =
    timers(TimerFilter.ALL).firstOrNull { it.row.kind == RowKind.RUNNING }
