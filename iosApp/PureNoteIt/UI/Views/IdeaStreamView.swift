import PhotosUI
import SwiftUI

struct IdeaStreamView: View {
    @ObservedObject var store: PureNoteStore
    let onOpenNote: (IosNoteRecord) -> Void

    @State private var draftText: String = ""
    @State private var selectedIds: Set<String> = []
    @State private var photoPickerItem: PhotosPickerItem? = nil

    var body: some View {
        ZStack(alignment: .bottom) {
            VStack(spacing: 12) {
                // Quick-capture glass dock
                VStack(spacing: 8) {
                    HStack(spacing: 10) {
                        Image(systemName: "bolt.fill")
                            .foregroundStyle(PureColors.vibrantOrange)
                        TextField(
                            store.isTurkish ? "Hızlı bir fikir, görev veya alıntı yakala..." : "Capture a quick idea or thought...",
                            text: $draftText,
                            axis: .vertical
                        )
                        .lineLimit(1...3)

                        Button {
                            store.speechManager.toggleListening(isTurkish: store.isTurkish) { spoken in
                                if !spoken.isEmpty {
                                    store.addStreamItem(content: spoken, type: "VOICE")
                                }
                            }
                        } label: {
                            Image(systemName: store.speechManager.isListening ? "mic.circle.fill" : "mic.fill")
                                .font(.title3)
                                .foregroundStyle(store.speechManager.isListening ? .red : .purple)
                        }

                        PhotosPicker(selection: $photoPickerItem, matching: .images) {
                            Image(systemName: "photo.fill")
                                .font(.title3)
                                .foregroundStyle(PureColors.accentBlue)
                        }

                        Button {
                            guard !draftText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
                            store.addStreamItem(content: draftText, type: "TEXT")
                            draftText = ""
                        } label: {
                            Image(systemName: "arrow.up.circle.fill")
                                .font(.title2)
                                .foregroundStyle(PureColors.vibrantOrange)
                        }
                    }

                    if store.speechManager.isListening {
                        VStack(alignment: .leading, spacing: 6) {
                            LiquidAudioWaveformView(
                                isActive: store.speechManager.isListening,
                                level: store.speechManager.audioLevel,
                                tint: .purple,
                                height: 18
                            )
                            Text(store.speechManager.partialText.isEmpty ? (store.isTurkish ? "Konuşun, kelimeler burada belirecek..." : "Listening...") : store.speechManager.partialText)
                                .font(.subheadline.weight(.medium))
                                .foregroundStyle(.purple)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }
                }
                .padding(12)
                .pureGlassCard(cornerRadius: 18)
                .padding(.horizontal, 16)
                .padding(.top, 8)

                if store.streamItems.isEmpty {
                    Spacer()
                    ContentUnavailableView(
                        store.isTurkish ? "Fikir Akışı Boş" : "Thought Stream Empty",
                        systemImage: "bolt.circle",
                        description: Text(
                            store.isTurkish
                                ? "Aklınıza gelen anlık fikirleri hızla kaydedip tek dokunuşla nota dönüştürün."
                                : "Capture quick thoughts and convert them into structured notes in one tap."
                        )
                    )
                    Spacer()
                } else {
                    List {
                        ForEach(store.streamItems) { item in
                            streamCard(item)
                                .listRowInsets(EdgeInsets(top: 5, leading: 16, bottom: 5, trailing: 16))
                                .listRowSeparator(.hidden)
                                .listRowBackground(Color.clear)
                        }
                    }
                    .listStyle(.plain)
                    .padding(.bottom, 70)
                }
            }

            let pendingCount = selectedIds.isEmpty
                ? store.streamItems.filter { !$0.isProcessed }.count
                : selectedIds.count
            if pendingCount > 0 {
                Button {
                    if let created = store.convertStreamItemsToNote(selectedIds) {
                        selectedIds.removeAll()
                        onOpenNote(created)
                    }
                } label: {
                    Label(
                        store.isTurkish ? "\(pendingCount) Fikri Nota Dönüştür" : "Convert \(pendingCount) Ideas to Note",
                        systemImage: "sparkles"
                    )
                    .font(.subheadline.bold())
                    .padding(.horizontal, 18)
                    .padding(.vertical, 12)
                    .background(PureColors.vibrantOrange)
                    .foregroundStyle(.white)
                    .clipShape(Capsule())
                    .shadow(color: PureColors.vibrantOrange.opacity(0.4), radius: 10, y: 4)
                }
                .padding(.bottom, 92)
            }
        }
        .onChange(of: photoPickerItem) { _, newItem in
            guard let newItem else { return }
            Task {
                if let data = try? await newItem.loadTransferable(type: Data.self),
                   let path = await store.storeMediaData(data, subFolder: "stream", extension: "jpg") {
                    store.addStreamItem(content: draftText.isEmpty ? (store.isTurkish ? "Fotoğraf Fikri" : "Photo Idea") : draftText, type: "PHOTO", imagePath: path)
                    draftText = ""
                }
                photoPickerItem = nil
            }
        }
    }

    private func streamCard(_ item: IosStreamItemRecord) -> some View {
        let isSelected = selectedIds.contains(item.syncUuid)
        return HStack(alignment: .top, spacing: 10) {
            Button {
                if isSelected { selectedIds.remove(item.syncUuid) }
                else { selectedIds.insert(item.syncUuid) }
            } label: {
                Image(systemName: isSelected ? "checkmark.circle.fill" : (item.isProcessed ? "checkmark.seal.fill" : "circle"))
                    .font(.title3)
                    .foregroundStyle(isSelected ? PureColors.vibrantOrange : (item.isProcessed ? PureColors.emeraldGreen : .secondary))
            }

            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Image(systemName: item.type == "PHOTO" ? "photo.fill" : (item.type == "VOICE" ? "mic.fill" : "bolt.fill"))
                        .font(.caption)
                        .foregroundStyle(PureColors.vibrantOrange)
                    Text(formatTime(item.timestamp))
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                    if item.isProcessed {
                        Text(store.isTurkish ? "Nota Eklendi" : "Merged")
                            .font(.caption2.bold())
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(PureColors.emeraldGreen.opacity(0.16))
                            .foregroundStyle(PureColors.emeraldGreen)
                            .clipShape(Capsule())
                    }
                    Spacer()
                }
                Text(item.content)
                    .font(.subheadline)

                if let imgPath = item.imagePath,
                   let url = store.resolveMediaURL(imgPath),
                   let uiImg = UIImage(contentsOfFile: url.path) {
                    Image(uiImage: uiImg)
                        .resizable()
                        .scaledToFill()
                        .frame(maxHeight: 130)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                }
            }
        }
        .padding(12)
        .pureGlassCard(cornerRadius: 16)
    }

    private func formatTime(_ millis: Int64) -> String {
        let f = DateFormatter()
        f.dateStyle = .short
        f.timeStyle = .short
        return f.string(from: Date(timeIntervalSince1970: TimeInterval(millis) / 1000.0))
    }
}
