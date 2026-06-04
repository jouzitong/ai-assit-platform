import { alertList, metricCards, todoList } from '../data'

export function useHomeOverview() {
  return {
    metricCards,
    todoList,
    alertList
  }
}
