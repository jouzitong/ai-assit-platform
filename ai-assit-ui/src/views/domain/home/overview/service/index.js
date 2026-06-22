import { activityFeed, calendarItems, focusPanels, heroSummary, promptSuggestions, quickEntries } from '../data'

export function useHomeOverview() {
  return {
    heroSummary,
    promptSuggestions,
    quickEntries,
    focusPanels,
    activityFeed,
    calendarItems
  }
}
