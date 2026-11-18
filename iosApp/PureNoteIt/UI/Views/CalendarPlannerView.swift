import SwiftUI

enum IosCalendarMode: String, CaseIterable, Identifiable {
    case month
    case fiveDays
    case threeDays
    case oneDay

    var id: String { rawValue }

    func label(isTr: Bool) -> String {
        switch self {
        case .month: return isTr ? "Ay" : "Month"
        case .fiveDays: return isTr ? "5 Gün" : "5 Days"
        case .threeDays: return isTr ? "3 Gün" : "3 Days"
        case .oneDay: return isTr ? "1 Gün" : "1 Day"
        }
    }
}

struct CalendarPlannerView: View {
    @ObservedObject var store: PureNoteStore
    let onOpenNote: (IosNoteRecord) -> Void

    @State private var mode: IosCalendarMode = .month
    @State private var selectedDate: Date = Date()
    @State private var currentMonthAnchor: Date = Date()

    private let cal: Calendar = {
        var c = Calendar.current
        c.firstWeekday = 2 // Monday
        return c
    }()

    var body: some View {
        VStack(spacing: 10) {
            // Mode & Daily Agenda header bar
            HStack(spacing: 8) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(IosCalendarMode.allCases) { m in
                            Button {
                                mode = m
                            } label: {
                                Text(m.label(isTr: store.isTurkish))
                                    .font(.caption.weight(.semibold))
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 6)
                                    .background(mode == m ? PureColors.vibrantOrange : Color.secondary.opacity(0.14))
                                    .foregroundStyle(mode == m ? .white : .primary)
                                    .clipShape(Capsule())
                            }
                        }
                    }
                }

                Button {
                    let agenda = store.createDailyAgendaNote()
                    onOpenNote(agenda)
                } label: {
                    Label(store.isTurkish ? "Günün Notu" : "Daily Note", systemImage: "sun.max.fill")
                        .font(.caption.weight(.bold))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(PureColors.vibrantOrange.opacity(0.18))
                        .foregroundStyle(PureColors.vibrantOrange)
                        .clipShape(Capsule())
                }
            }
            .padding(.horizontal, 16)

            if mode == .month {
                monthGridView
            } else {
                multiDayColumnPlanner
            }
        }
    }

    private var monthGridView: some View {
        ScrollView {
            VStack(spacing: 12) {
                HStack {
                    Button {
                        currentMonthAnchor = cal.date(byAdding: .month, value: -1, to: currentMonthAnchor) ?? currentMonthAnchor
                    } label: {
                        Image(systemName: "chevron.left.circle.fill")
                            .font(.title3)
                    }
                    Spacer()
                    Text(monthTitle(currentMonthAnchor))
                        .font(.headline)
                    Spacer()
                    Button {
                        currentMonthAnchor = cal.date(byAdding: .month, value: 1, to: currentMonthAnchor) ?? currentMonthAnchor
                    } label: {
                        Image(systemName: "chevron.right.circle.fill")
                            .font(.title3)
                    }
                }
                .padding(.horizontal, 16)

                let weekdaySymbols = store.isTurkish
                    ? ["Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz"]
                    : ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
                LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 7), spacing: 8) {
                    ForEach(weekdaySymbols, id: \.self) { sym in
                        Text(sym)
                            .font(.caption2.bold())
                            .foregroundStyle(.secondary)
                    }

                    ForEach(Array(daysInMonthGrid().enumerated()), id: \.offset) { _, day in
                        if let day {
                            let dayNotes = notesForDay(day)
                            let isSelected = cal.isDate(day, inSameDayAs: selectedDate)
                            let isToday = cal.isDateInToday(day)
                            Button {
                                selectedDate = day
                            } label: {
                                VStack(spacing: 4) {
                                    Text("\(cal.component(.day, from: day))")
                                        .font(.subheadline.weight(isToday || isSelected ? .bold : .regular))
                                        .foregroundStyle(isSelected ? .white : (isToday ? PureColors.vibrantOrange : .primary))
                                    HStack(spacing: 2) {
                                        ForEach(Array(dayNotes.prefix(3).enumerated()), id: \.offset) { idx, n in
                                            Circle()
                                                .fill(colorForNote(n, fallbackIndex: idx))
                                                .frame(width: 5, height: 5)
                                        }
                                    }
                                    .frame(height: 6)
                                }
                                .frame(maxWidth: .infinity, minHeight: 42)
                                .background(
                                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                                        .fill(isSelected ? PureColors.vibrantOrange : (isToday ? PureColors.vibrantOrange.opacity(0.14) : Color.clear))
                                )
                            }
                        } else {
                            Color.clear.frame(height: 42)
                        }
                    }
                }
                .padding(12)
                .pureGlassCard(cornerRadius: 18)
                .padding(.horizontal, 16)

                // Selected day events + add note CTA
                let dayNotes = notesForDay(selectedDate)
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Text(dayHeader(selectedDate))
                            .font(.subheadline.bold())
                        Spacer()
                        Button {
                            let ms = Int64(selectedDate.timeIntervalSince1970 * 1000)
                            let created = store.createNote(title: "", icon: "📅", dateMillis: ms)
                            onOpenNote(created)
                        } label: {
                            Label(store.isTurkish ? "Bu Güne Not Ekle" : "Add Note", systemImage: "plus.circle.fill")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(PureColors.vibrantOrange)
                        }
                    }

                    if dayNotes.isEmpty {
                        Text(store.isTurkish ? "Bu gün için planlanmış not yok." : "No notes scheduled for this day.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .padding(.vertical, 8)
                    } else {
                        ForEach(dayNotes) { note in
                            calendarEventRow(note)
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 100)
            }
        }
    }

    private var multiDayColumnPlanner: some View {
        let dayCount = mode == .fiveDays ? 5 : (mode == .threeDays ? 3 : 1)
        let days = (0..<dayCount).compactMap { cal.date(byAdding: .day, value: $0, to: cal.startOfDay(for: selectedDate)) }
        let hours = Array(9...21)

        return ScrollView([.horizontal, .vertical], showsIndicators: true) {
            HStack(alignment: .top, spacing: 10) {
                ForEach(days, id: \.self) { day in
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text(shortDayTitle(day))
                                .font(.subheadline.bold())
                                .foregroundStyle(cal.isDateInToday(day) ? PureColors.vibrantOrange : .primary)
                            Spacer()
                            Button {
                                let ms = Int64(day.timeIntervalSince1970 * 1000)
                                let created = store.createNote(title: "", icon: "📅", dateMillis: ms)
                                onOpenNote(created)
                            } label: {
                                Image(systemName: "plus.circle.fill")
                                    .foregroundStyle(PureColors.vibrantOrange)
                            }
                        }

                        ForEach(hours, id: \.self) { hour in
                            let slotNotes = notesForDayAndHour(day: day, hour: hour)
                            VStack(alignment: .leading, spacing: 4) {
                                HStack {
                                    Text(String(format: "%02d:00", hour))
                                        .font(.caption2.monospacedDigit())
                                        .foregroundStyle(.secondary)
                                    Spacer()
                                    Button {
                                        var comps = cal.dateComponents([.year, .month, .day], from: day)
                                        comps.hour = hour
                                        comps.minute = 0
                                        let slotDate = cal.date(from: comps) ?? day
                                        let created = store.createNote(
                                            title: "",
                                            icon: "📅",
                                            dateMillis: Int64(slotDate.timeIntervalSince1970 * 1000)
                                        )
                                        onOpenNote(created)
                                    } label: {
                                        Image(systemName: "plus")
                                            .font(.caption2)
                                            .foregroundStyle(.tertiary)
                                    }
                                }

                                ForEach(slotNotes) { n in
                                    calendarEventRow(n)
                                }
                            }
                            .padding(8)
                            .background(Color.secondary.opacity(0.08))
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                        }
                    }
                    .frame(width: dayCount == 1 ? UIScreen.main.bounds.width - 32 : 220)
                    .padding(10)
                    .pureGlassCard(cornerRadius: 16)
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 100)
        }
    }

    private func calendarEventRow(_ note: IosNoteRecord) -> some View {
        let isProtected = store.isNoteProtected(note)
        return Button {
            onOpenNote(note)
        } label: {
            HStack(spacing: 10) {
                RoundedRectangle(cornerRadius: 2)
                    .fill(colorForNote(note, fallbackIndex: note.rowId))
                    .frame(width: 4, height: 34)
                ModernNoteIconBadge(iconKey: note.icon, isProtected: isProtected, size: 26, iconSize: 13)
                VStack(alignment: .leading, spacing: 2) {
                    Text(isProtected && !store.securityManager.isVaultUnlocked ? "******" : (note.title.isEmpty ? (store.isTurkish ? "Başlıksız" : "Untitled") : note.title))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                    HStack(spacing: 8) {
                        Text(timeString(note.reminderTime ?? note.date))
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                        if note.reminderTime != nil {
                            Image(systemName: "alarm.fill")
                                .font(.caption2)
                                .foregroundStyle(PureColors.vibrantOrange)
                        }
                        if note.totalTasks > 0 {
                            Text("✓ \(note.completedTasks)/\(note.totalTasks)")
                                .font(.caption2)
                                .foregroundStyle(PureColors.emeraldGreen)
                        }
                    }
                }
                Spacer()
            }
            .padding(8)
            .background(Color(UIColor.secondarySystemGroupedBackground))
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }

    private func notesForDay(_ day: Date) -> [IosNoteRecord] {
        store.activeNotes.filter { note in
            let ts = note.reminderTime ?? note.date
            let noteDate = Date(timeIntervalSince1970: TimeInterval(ts) / 1000.0)
            return cal.isDate(noteDate, inSameDayAs: day)
        }
    }

    private func notesForDayAndHour(day: Date, hour: Int) -> [IosNoteRecord] {
        notesForDay(day).filter { note in
            let ts = note.reminderTime ?? note.date
            let d = Date(timeIntervalSince1970: TimeInterval(ts) / 1000.0)
            return cal.component(.hour, from: d) == hour
        }
    }

    private func daysInMonthGrid() -> [Date?] {
        guard let monthInterval = cal.dateInterval(of: .month, for: currentMonthAnchor),
              let firstWeekday = cal.dateComponents([.weekday], from: monthInterval.start).weekday else {
            return []
        }
        // Monday = 2 -> offset 0; Sunday = 1 -> offset 6
        let leading = (firstWeekday + 5) % 7
        let daysCount = cal.range(of: .day, in: .month, for: currentMonthAnchor)?.count ?? 30
        var cells: [Date?] = Array(repeating: nil, count: leading)
        for d in 0..<daysCount {
            cells.append(cal.date(byAdding: .day, value: d, to: monthInterval.start))
        }
        return cells
    }

    private func colorForNote(_ note: IosNoteRecord, fallbackIndex: Int) -> Color {
        if let catUuid = note.categorySyncUuid,
           let idx = store.categories.firstIndex(where: { $0.syncUuid == catUuid }) {
            return PureColors.categoryColor(index: idx)
        }
        return PureColors.categoryColor(index: fallbackIndex)
    }

    private func monthTitle(_ date: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: store.isTurkish ? "tr_TR" : "en_US")
        f.dateFormat = "LLLL yyyy"
        return f.string(from: date).capitalized
    }

    private func dayHeader(_ date: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: store.isTurkish ? "tr_TR" : "en_US")
        f.dateStyle = .full
        return f.string(from: date)
    }

    private func shortDayTitle(_ date: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: store.isTurkish ? "tr_TR" : "en_US")
        f.dateFormat = "EEE d MMM"
        return f.string(from: date)
    }

    private func timeString(_ millis: Int64) -> String {
        let f = DateFormatter()
        f.dateFormat = "HH:mm"
        return f.string(from: Date(timeIntervalSince1970: TimeInterval(millis) / 1000.0))
    }
}
