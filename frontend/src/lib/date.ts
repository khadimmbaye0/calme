export function startOfDay(date: Date): Date {
  const result = new Date(date)
  result.setHours(0, 0, 0, 0)
  return result
}

export function addDays(date: Date, days: number): Date {
  const result = new Date(date)
  result.setDate(result.getDate() + days)
  return result
}

export function formatLongDay(date: Date): string {
  return new Intl.DateTimeFormat('fr-FR', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  }).format(date)
}

export function formatTime(iso: string): string {
  return new Intl.DateTimeFormat('fr-FR', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(iso))
}

export function toTimeValue(iso: string): string {
  const date = new Date(iso)
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

export function combineDayAndTime(day: Date, time: string): Date {
  const [hours, minutes] = time.split(':').map(Number)
  const result = new Date(day)
  result.setHours(hours ?? 0, minutes ?? 0, 0, 0)
  return result
}

export function minutesFromStartOfDay(date: Date, day: Date): number {
  return (date.getTime() - startOfDay(day).getTime()) / 60_000
}
