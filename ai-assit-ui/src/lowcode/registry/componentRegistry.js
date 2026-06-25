import ActionBar from '../../components/commons/list/ActionBar.vue'
import DataListFooter from '../../components/commons/list/DataListFooter.vue'
import DataTable from '../../components/commons/list/DataTable.vue'
import FilterBar from '../../components/commons/list/FilterBar.vue'
import FilterSummary from '../../components/commons/list/FilterSummary.vue'
import HeaderBar from '../../components/commons/list/HeaderBar.vue'
import ListCommonLayout from '../../components/commons/list/ListCommonLayout.vue'
import StatsBar from '../../components/commons/list/StatsBar.vue'

const componentRegistry = {
  ActionBar,
  DataListFooter,
  DataTable,
  FilterBar,
  FilterSummary,
  HeaderBar,
  ListCommonLayout,
  StatsBar
}

export function resolveLowcodeComponent(name) {
  return componentRegistry[name] || null
}
