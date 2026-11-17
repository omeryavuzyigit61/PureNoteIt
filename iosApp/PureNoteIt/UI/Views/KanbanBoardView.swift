import SwiftUI

struct KanbanBoardView: View {
    @ObservedObject var store: PureNoteStore
    let onOpenNote: (IosNoteRecord) -> Void

    private let columns: [(status: String, tr: String, en: String, color: Color)] = [
        ("TODO", "Yapılacak", "To Do", PureColors.vibrantOrange),
        ("IN_PROGRESS", "Devam Ediyor", "In Progress", PureColors.accentBlue),
        ("IN_REVIEW", "İncelemede", "In Review", Color.purple),
        ("DONE", "Tamamlandı", "Done", PureColors.emeraldGreen)
    ]

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(alignment: .top, spacing: 14) {
                ForEach(Array(columns.enumerated()), id: \.element.status) { colIndex, col in
                    let colNotes = store.filteredNotes.filter { $0.status == col.status }
                    VStack(alignment: .leading, spacing: 10) {
                        HStack(spacing: 8) {
                            Circle()
                                .fill(col.color)
                                .frame(width: 9, height: 9)
                            Text(store.isTurkish ? col.tr : col.en)
                                .font(.subheadline.weight(.bold))
                            Spacer()
                            Text("\(colNotes.count)")
                                .font(.caption.bold())
                                .padding(.horizontal, 8)
                                .padding(.vertical, 3)
                                .background(col.color.opacity(0.16))
                                .foregroundStyle(col.color)
                                .clipShape(Capsule())
                        }
                        .padding(.horizontal, 4)

                        ScrollView(.vertical, showsIndicators: false) {
                            LazyVStack(spacing: 10) {
                                ForEach(colNotes) { note in
                                    kanbanCard(note, colIndex: colIndex, accent: col.color)
                                }

                                Button {
                                    let created = store.createNote(status: col.status)
                                    onOpenNote(created)
                                } label: {
                                    Label(store.isTurkish ? "Yeni Kart Ekle" : "Create Card", systemImage: "plus.circle.fill")
                                        .font(.caption.weight(.semibold))
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 10)
                                        .background(col.color.opacity(0.12))
                                        .foregroundStyle(col.color)
                                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                                }
                            }
                            .padding(.bottom, 100)
                        }
                    }
                    .frame(width: 280)
                    .padding(12)
                    .pureGlassCard(cornerRadius: 20)
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
        }
    }

    private func kanbanCard(_ note: IosNoteRecord, colIndex: Int, accent: Color) -> some View {
        let isProtected = store.isNoteProtected(note)
        return VStack(alignment: .leading, spacing: 8) {
            Button {
                onOpenNote(note)
            } label: {
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 8) {
                        ModernNoteIconBadge(iconKey: note.icon, isProtected: isProtected, size: 26, iconSize: 13)
                        Text(note.title.isEmpty ? (store.isTurkish ? "Başlıksız Not" : "Untitled") : note.title)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.primary)
                            .lineLimit(2)
                        Spacer()
                        if note.isPinned {
                            Image(systemName: "pin.fill")
                                .font(.caption2)
                                .foregroundStyle(PureColors.vibrantOrange)
                        }
                    }
                    Text(isProtected && !store.securityManager.isVaultUnlocked ? "••••••" : store.plainSummary(for: note))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
            }

            HStack {
                if note.totalTasks > 0 {
                    Label("\(note.completedTasks)/\(note.totalTasks)", systemImage: "checkmark.circle.fill")
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(note.completedTasks == note.totalTasks ? PureColors.emeraldGreen : .secondary)
                }
                Spacer()
                HStack(spacing: 6) {
                    if colIndex > 0 {
                        Button {
                            store.updateNoteStatus(note, status: columns[colIndex - 1].status)
                        } label: {
                            Image(systemName: "chevron.left.circle.fill")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }
                    if colIndex < columns.count - 1 {
                        Button {
                            store.updateNoteStatus(note, status: columns[colIndex + 1].status)
                        } label: {
                            Image(systemName: "chevron.right.circle.fill")
                                .font(.subheadline)
                                .foregroundStyle(accent)
                        }
                    }
                }
            }
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color(UIColor.secondarySystemGroupedBackground))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(Color.primary.opacity(0.08), lineWidth: 1)
        )
    }
}
