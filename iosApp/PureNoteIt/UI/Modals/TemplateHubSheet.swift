import SwiftUI

struct TemplateHubSheet: View {
    @ObservedObject var store: PureNoteStore
    let onSelectTemplate: (IosNoteTemplate) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var selectedCategory: IosTemplateCategory = .all

    private var filteredTemplates: [IosNoteTemplate] {
        NoteTemplatesCatalog.all.filter { tpl in
            selectedCategory == .all || tpl.category == selectedCategory
        }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(IosTemplateCategory.allCases) { cat in
                            let selected = selectedCategory == cat
                            Button {
                                selectedCategory = cat
                            } label: {
                                Text(cat.label(isTr: store.isTurkish))
                                    .font(.caption.weight(.semibold))
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 7)
                                    .background(selected ? PureColors.vibrantOrange : Color.secondary.opacity(0.14))
                                    .foregroundStyle(selected ? .white : .primary)
                                    .clipShape(Capsule())
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                }
                .padding(.top, 8)

                List {
                    ForEach(filteredTemplates) { tpl in
                        Button {
                            onSelectTemplate(tpl)
                            dismiss()
                        } label: {
                            HStack(alignment: .top, spacing: 12) {
                                ModernNoteIconBadge(iconKey: tpl.iconKey, size: 40, iconSize: 19, cornerRadius: 12)
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(tpl.title(isTr: store.isTurkish))
                                        .font(.subheadline.bold())
                                        .foregroundStyle(.primary)
                                    Text(tpl.description(isTr: store.isTurkish))
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                        .lineLimit(2)
                                    HStack(spacing: 6) {
                                        ForEach(tpl.tags, id: \.self) { tag in
                                            Text(tag)
                                                .font(.caption2.weight(.medium))
                                                .padding(.horizontal, 7)
                                                .padding(.vertical, 2)
                                                .background(tpl.accentColor.opacity(0.14))
                                                .foregroundStyle(tpl.accentColor)
                                                .clipShape(Capsule())
                                        }
                                    }
                                    .padding(.top, 2)
                                }
                                Spacer()
                                Image(systemName: "plus.circle.fill")
                                    .font(.title3)
                                    .foregroundStyle(PureColors.vibrantOrange)
                            }
                            .padding(12)
                            .pureGlassCard(cornerRadius: 16)
                        }
                        .listRowInsets(EdgeInsets(top: 5, leading: 16, bottom: 5, trailing: 16))
                        .listRowSeparator(.hidden)
                        .listRowBackground(Color.clear)
                    }
                }
                .listStyle(.plain)
            }
            .navigationTitle(store.isTurkish ? "Şablon Galerisi (14)" : "Templates Hub (14)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(store.isTurkish ? "Kapat" : "Close") { dismiss() }
                }
            }
        }
    }
}
