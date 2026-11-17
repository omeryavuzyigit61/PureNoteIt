import PhotosUI
import PureNoteShared
import SwiftUI
import UIKit

enum EditorInitialAction: Hashable {
    case none
    case startDictation
    case startAudioRecording
    case openDrawing
    case openCameraOcr
}

struct NoteEditorView: View {
    @ObservedObject var store: PureNoteStore
    @State var note: IosNoteRecord
    let initialDecryptedJson: String
    var initialAction: EditorInitialAction = .none
    let onNavigateToNote: (IosNoteRecord) -> Void

    @Environment(\.dismiss) private var dismiss

    @State private var title: String
    @State private var icon: String
    @State private var blocks: [EditableBlock]
    @State private var undoStack: [[EditableBlock]] = []
    @State private var redoStack: [[EditableBlock]] = []
    @State private var didTriggerInitialAction = false

    @State private var showSlashSheet = false
    @State private var showDrawingSheet = false
    @State private var editingDrawingBlockId: String? = nil
    @State private var showZenMode = false
    @State private var showPdfStudio = false
    @State private var showReminderSheet = false
    @State private var showLockSheet = false
    @State private var showSubPageAlert = false
    @State private var newSubPageTitle = ""
    @State private var showLinkNoteSheet = false
    @State private var lockPassphrase = ""
    @State private var lockError: String? = nil
    @State private var lightboxURL: URL? = nil
    @State private var showRelationsAccordion = true

    @State private var photoPickerItem: PhotosPickerItem? = nil
    @State private var ocrPickerItem: PhotosPickerItem? = nil
    @State private var showCameraSheet = false
    @State private var showWordStatsAlert = false
    @State private var pendingSubPageNav: IosNoteRecord? = nil

    private let iconOptions = ["📄", "🚀", "💡", "⚡", "♾️", "📖", "🏛️", "💎", "🥗", "💪", "🎯", "📅", "🔐"]

    init(
        store: PureNoteStore,
        note: IosNoteRecord,
        initialDecryptedJson: String,
        initialAction: EditorInitialAction = .none,
        onNavigateToNote: @escaping (IosNoteRecord) -> Void
    ) {
        self.store = store
        self.initialDecryptedJson = initialDecryptedJson
        self.initialAction = initialAction
        self.onNavigateToNote = onNavigateToNote
        _note = State(initialValue: note)
        _title = State(initialValue: note.title)
        _icon = State(initialValue: note.icon)
        _blocks = State(initialValue: store.parseBlocks(raw: initialDecryptedJson))
    }

    private var wordCount: Int {
        let joined = "\(title) " + blocks.map { "\($0.content) \($0.toggleBody)" }.joined(separator: " ")
        return joined.split { $0.isWhitespace || $0.isNewline }.count
    }

    private var readingMinutes: Int { max(1, (wordCount + 199) / 200) }

    private var outgoingLinks: [IosNoteLinkRecord] {
        store.noteLinks.filter { $0.sourceSyncUuid == note.syncUuid }
    }

    private var backlinks: [IosNoteLinkRecord] {
        store.noteLinks.filter { $0.targetSyncUuid == note.syncUuid }
    }

    private var smartLinkSuggestion: IosNoteRecord? {
        guard let lastBlock = blocks.last, !lastBlock.content.isEmpty else { return nil }
        let lower = lastBlock.content.lowercased()
        return store.activeNotes.first { candidate in
            candidate.syncUuid != note.syncUuid &&
            !candidate.title.isEmpty &&
            candidate.title.count >= 3 &&
            lower.contains(candidate.title.lowercased()) &&
            !lower.contains("[[\(candidate.title.lowercased())]]")
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            glassPropertiesBar

            if let suggested = smartLinkSuggestion {
                smartLinkBanner(suggested)
            }

            ScrollViewReader { scrollProxy in
                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        headerTitleRow

                        if !outgoingLinks.isEmpty || !backlinks.isEmpty || blocks.contains(where: { $0.type == .subPage }) {
                            pageRelationsHub
                        }

                        Divider().opacity(0.35)

                        ForEach($blocks) { $block in
                            let idx = blocks.firstIndex(where: { $0.id == block.id }) ?? 0
                            BlockRowCard(
                                block: $block,
                                allBlocks: blocks,
                                allNotes: store.activeNotes,
                                store: store,
                                index: idx,
                                totalCount: blocks.count,
                                onPersist: { persistCurrentState() },
                                onMoveUp: {
                                    guard idx > 0 else { return }
                                    pushUndo()
                                    blocks.swapAt(idx, idx - 1)
                                    persistCurrentState()
                                },
                                onMoveDown: {
                                    guard idx < blocks.count - 1 else { return }
                                    pushUndo()
                                    blocks.swapAt(idx, idx + 1)
                                    persistCurrentState()
                                },
                                onDelete: {
                                    guard blocks.count > 1 else { return }
                                    pushUndo()
                                    blocks.remove(at: idx)
                                    persistCurrentState()
                                },
                                onEditDrawing: {
                                    editingDrawingBlockId = block.id
                                    showDrawingSheet = true
                                },
                                onOpenLightbox: { url in lightboxURL = url },
                                onOpenSubPage: { uuid in
                                    if let target = store.activeNotes.first(where: { $0.syncUuid == uuid }) {
                                        persistCurrentState()
                                        onNavigateToNote(target)
                                    }
                                },
                                onScrollToBlockId: { targetId in
                                    withAnimation { scrollProxy.scrollTo(targetId, anchor: .top) }
                                },
                                onOpenWikiTitle: { linkTitle in
                                    if let target = store.activeNotes.first(where: { $0.title.caseInsensitiveCompare(linkTitle) == .orderedSame }) {
                                        persistCurrentState()
                                        onNavigateToNote(target)
                                    }
                                }
                            )
                            .id(block.id)
                        }
                    }
                    .padding(16)
                    .padding(.bottom, 30)
                }
            }

            if store.audioManager.isRecording {
                audioRecordingBanner
                    .padding(.horizontal, 12)
                    .padding(.bottom, 6)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }

            if store.speechManager.isListening {
                voiceDictationBanner
                    .padding(.horizontal, 12)
                    .padding(.bottom, 6)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }

            glassEditorBottomToolbar
        }
        .background(Color(UIColor.systemGroupedBackground))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                HStack(spacing: 10) {
                    Button {
                        guard let prev = undoStack.popLast() else { return }
                        redoStack.append(blocks)
                        blocks = prev
                    } label: {
                        Image(systemName: "arrow.uturn.backward")
                    }
                    .disabled(undoStack.isEmpty)

                    Button {
                        guard let next = redoStack.popLast() else { return }
                        undoStack.append(blocks)
                        blocks = next
                    } label: {
                        Image(systemName: "arrow.uturn.forward")
                    }
                    .disabled(redoStack.isEmpty)

                    Button { showPdfStudio = true } label: {
                        Image(systemName: "square.and.arrow.up")
                    }

                    Menu {
                        Button { showZenMode = true } label: {
                            Label(store.isTurkish ? "Zen Okuma Modu" : "Zen Reading Mode", systemImage: "book.pages.fill")
                        }
                        Button { showReminderSheet = true } label: {
                            Label(store.isTurkish ? "Hatırlatıcı Ekle" : "Set Reminder", systemImage: "alarm.fill")
                        }
                        Button {
                            if note.isEncrypted {
                                let ser = store.serializeBlocks(blocks)
                                store.removeNoteLock(note, decryptedBlocksJson: ser.json)
                                note.isEncrypted = false
                                note.isPassword = false
                            } else {
                                lockPassphrase = ""
                                lockError = nil
                                showLockSheet = true
                            }
                        } label: {
                            Label(
                                note.isEncrypted
                                    ? (store.isTurkish ? "Kilidi Kaldır" : "Remove Lock")
                                    : (store.isTurkish ? "AES-256 ile Kilitle" : "Lock with AES-256"),
                                systemImage: note.isEncrypted ? "lock.open.fill" : "lock.fill"
                            )
                        }
                        Button(role: .destructive) {
                            store.moveToTrash(note)
                            dismiss()
                        } label: {
                            Label(store.isTurkish ? "Çöp Kutusuna Taşı" : "Move to Trash", systemImage: "trash.fill")
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }

                    Button(store.isTurkish ? "Kaydet" : "Save") {
                        persistCurrentState()
                        store.showToast(store.isTurkish ? "Kaydedildi" : "Saved")
                    }
                    .fontWeight(.bold)
                    .foregroundStyle(PureColors.vibrantOrange)
                }
            }
        }
        .onChange(of: blocks) { _, newBlocks in
            if let last = newBlocks.last, last.type == .paragraph, last.content == "/" {
                showSlashSheet = true
            }
        }
        .onChange(of: photoPickerItem) { _, newItem in
            guard let newItem else { return }
            Task {
                if let data = try? await newItem.loadTransferable(type: Data.self),
                   let path = await store.storeMediaData(data, subFolder: "images", extension: "jpg") {
                    appendMediaBlock(type: .image, mediaPath: path)
                }
                photoPickerItem = nil
            }
        }
        .onChange(of: ocrPickerItem) { _, newItem in
            guard let newItem else { return }
            Task {
                if let data = try? await newItem.loadTransferable(type: Data.self),
                   let img = UIImage(data: data) {
                    let recognized = await IosOcrService.recognizeText(from: img)
                    if !recognized.isEmpty {
                        appendBlock(.paragraph, content: recognized)
                        store.showToast(store.isTurkish ? "OCR metni eklendi" : "OCR text inserted")
                    } else {
                        store.showToast(store.isTurkish ? "Metin algılanamadı" : "No text found")
                    }
                }
                ocrPickerItem = nil
            }
        }
        .onAppear {
            guard !didTriggerInitialAction, initialAction != .none else { return }
            didTriggerInitialAction = true
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                switch initialAction {
                case .startDictation:
                    store.speechManager.toggleListening(isTurkish: store.isTurkish) { spoken in
                        appendBlock(.paragraph, content: spoken)
                    }
                case .startAudioRecording:
                    let stage = store.db.mediaResolver.createStagingFilePath(prefix: "rec", extension: "m4a")
                    store.audioManager.startRecording(stagingPath: stage) { url in
                        Task {
                            if let data = try? Data(contentsOf: url),
                               let path = await store.storeMediaData(data, subFolder: "audio", extension: "m4a") {
                                appendMediaBlock(type: .audio, mediaPath: path)
                            }
                        }
                    }
                case .openDrawing:
                    editingDrawingBlockId = nil
                    showDrawingSheet = true
                case .openCameraOcr:
                    showCameraSheet = true
                case .none:
                    break
                }
            }
        }
        .onChange(of: store.audioManager.recordingError) { _, err in
            if let err, !err.isEmpty { store.showToast(err) }
        }
        .onChange(of: store.speechManager.statusError) { _, err in
            if let err, !err.isEmpty { store.showToast(err) }
        }
        .onDisappear {
            if store.audioManager.isRecording {
                store.audioManager.finishRecording(save: false)
            }
            store.speechManager.stopListening()
            persistCurrentState()
        }
        .sheet(isPresented: $showSlashSheet) {
            SlashBlockPickerSheet(isTurkish: store.isTurkish) { kind in
                if let last = blocks.last, last.type == .paragraph, last.content == "/" {
                    blocks.removeLast()
                }
                if kind == .drawing {
                    editingDrawingBlockId = nil
                    showDrawingSheet = true
                } else if kind == .subPage {
                    newSubPageTitle = ""
                    showSubPageAlert = true
                } else {
                    appendBlock(kind)
                }
            }
        }
        .sheet(isPresented: $showDrawingSheet) {
            let existingURL = editingDrawingBlockId.flatMap { bid in
                blocks.first(where: { $0.id == bid })?.mediaPath.flatMap { store.resolveMediaURL($0) }
            }
            DrawingCanvasSheet(isTurkish: store.isTurkish, existingImageURL: existingURL) { pngData in
                Task {
                    if let path = await store.storeMediaData(pngData, subFolder: "drawings", extension: "png") {
                        if let bid = editingDrawingBlockId, let idx = blocks.firstIndex(where: { $0.id == bid }) {
                            pushUndo()
                            blocks[idx].mediaPath = path
                            persistCurrentState()
                        } else {
                            appendMediaBlock(type: .drawing, mediaPath: path)
                        }
                    }
                }
            }
        }
        .fullScreenCover(isPresented: $showZenMode) {
            ZenReadingView(store: store, title: title, icon: icon, blocks: blocks, onLightbox: { lightboxURL = $0 })
        }
        .sheet(isPresented: $showPdfStudio) {
            PdfExportStudioSheet(store: store, title: title, blocks: blocks)
        }
        .sheet(isPresented: $showReminderSheet) {
            ReminderPickerSheet(store: store, note: $note) {
                persistCurrentState()
            }
        }
        .sheet(isPresented: $showLinkNoteSheet) {
            LinkExistingNoteSheet(store: store, currentUuid: note.syncUuid) { target in
                appendBlock(.paragraph, content: "[[\(target.title)]]")
            }
        }
        .sheet(isPresented: $showCameraSheet) {
            CameraOcrCaptureView { img in
                Task {
                    let recognized = await IosOcrService.recognizeText(from: img)
                    if !recognized.isEmpty {
                        appendBlock(.paragraph, content: recognized)
                    }
                }
            }
        }
        .fullScreenCover(item: $lightboxURL) { url in
            MediaLightboxView(imageURL: url)
        }
        .alert(store.isTurkish ? "Yeni Alt Sayfa" : "New Sub-Page", isPresented: $showSubPageAlert) {
            TextField(store.isTurkish ? "Alt sayfa başlığı" : "Sub-page title", text: $newSubPageTitle)
            Button(store.isTurkish ? "Oluştur" : "Create") {
                let clean = newSubPageTitle.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !clean.isEmpty else { return }
                persistCurrentState()
                let child = store.createSubPage(parent: note, title: clean)
                if let refreshedParent = store.activeNotes.first(where: { $0.syncUuid == note.syncUuid }) {
                    note = refreshedParent
                    blocks = store.parseBlocks(raw: refreshedParent.richBlocksJson)
                }
                onNavigateToNote(child)
            }
            Button(store.isTurkish ? "İptal" : "Cancel", role: .cancel) {}
        }
        .alert(store.isTurkish ? "Okuma İstatistikleri" : "Reading Stats", isPresented: $showWordStatsAlert) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(store.isTurkish
                 ? "\(wordCount) kelime · Yaklaşık \(readingMinutes) dk okuma süresi · \(blocks.count) blok"
                 : "\(wordCount) words · ~\(readingMinutes) min read · \(blocks.count) blocks")
        }
        .sheet(isPresented: $showLockSheet) {
            NavigationStack {
                Form {
                    Section(store.isTurkish ? "AES-256-GCM Not Şifreleme" : "AES-256-GCM Note Encryption") {
                        SecureField(store.isTurkish ? "En az 4 karakter parola" : "Passphrase (4+ chars)", text: $lockPassphrase)
                        if let lockError {
                            Text(lockError).font(.caption).foregroundStyle(.red)
                        }
                    }
                }
                .navigationTitle(store.isTurkish ? "Notu Kilitle" : "Lock Note")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button(store.isTurkish ? "İptal" : "Cancel") { showLockSheet = false }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button(store.isTurkish ? "Şifrele" : "Encrypt") {
                            guard lockPassphrase.count >= 4 else {
                                lockError = store.isTurkish ? "Parola en az 4 karakter olmalıdır." : "Passphrase too short."
                                return
                            }
                            persistCurrentState()
                            if store.lockNote(note, passphrase: lockPassphrase) {
                                note.isEncrypted = true
                                note.isPassword = true
                                showLockSheet = false
                            } else {
                                lockError = store.isTurkish ? "Şifreleme başarısız." : "Encryption failed."
                            }
                        }
                    }
                }
            }
            .presentationDetents([.medium])
        }
    }

    // MARK: - Top Glass Properties Bar

    private var glassPropertiesBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                Menu {
                    ForEach(["TODO", "IN_PROGRESS", "IN_REVIEW", "DONE"], id: \.self) { st in
                        Button(statusLabel(st)) {
                            note.status = st
                            persistCurrentState()
                        }
                    }
                } label: {
                    Label(statusLabel(note.status), systemImage: "flag.fill")
                        .font(.caption2.bold())
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(PureColors.vibrantOrange.opacity(0.16))
                        .foregroundStyle(PureColors.vibrantOrange)
                        .clipShape(Capsule())
                }

                Menu {
                    Button(store.isTurkish ? "Kategorisiz" : "None") {
                        note.categorySyncUuid = nil
                        note.categoryId = nil
                        persistCurrentState()
                    }
                    ForEach(store.categories) { cat in
                        Button(cat.name) {
                            note.categorySyncUuid = cat.syncUuid
                            note.categoryId = cat.rowId
                            persistCurrentState()
                        }
                    }
                } label: {
                    let catName = store.categories.first { $0.syncUuid == note.categorySyncUuid }?.name
                    Label(catName ?? (store.isTurkish ? "Kategori" : "Category"), systemImage: "folder.fill")
                        .font(.caption2.weight(.semibold))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .pureGlassCapsule()
                }

                Button { showReminderSheet = true } label: {
                    Label(
                        note.reminderTime != nil ? (store.isTurkish ? "Alarm Kuruldu" : "Reminder Set") : (store.isTurkish ? "Hatırlatıcı" : "Remind"),
                        systemImage: note.reminderTime != nil ? "alarm.fill" : "alarm"
                    )
                    .font(.caption2.weight(.semibold))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .foregroundStyle(note.reminderTime != nil ? PureColors.vibrantOrange : .primary)
                    .pureGlassCapsule()
                }

                Button { showWordStatsAlert = true } label: {
                    Label("\(wordCount)w · \(readingMinutes)m", systemImage: "doc.text.magnifyingglass")
                        .font(.caption2.weight(.semibold))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .pureGlassCapsule()
                }

                Button {
                    note.isPinned.toggle()
                    persistCurrentState()
                } label: {
                    Image(systemName: note.isPinned ? "pin.fill" : "pin")
                        .font(.caption.bold())
                        .padding(6)
                        .foregroundStyle(note.isPinned ? PureColors.vibrantOrange : .primary)
                        .pureGlassCapsule()
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
        }
        .background(.ultraThinMaterial)
    }

    private var headerTitleRow: some View {
        HStack(spacing: 12) {
            Menu {
                ForEach(iconOptions, id: \.self) { opt in
                    Button {
                        icon = opt
                        persistCurrentState()
                    } label: {
                        Text(opt)
                    }
                }
            } label: {
                ModernNoteIconBadge(iconKey: icon, isProtected: note.isEncrypted, size: 42, iconSize: 20, cornerRadius: 12)
            }

            TextField(store.isTurkish ? "Sayfa Başlığı..." : "Page Title...", text: $title)
                .font(.system(size: 24, weight: .bold))
        }
    }

    private var pageRelationsHub: some View {
        DisclosureGroup(isExpanded: $showRelationsAccordion) {
            VStack(alignment: .leading, spacing: 8) {
                if !outgoingLinks.isEmpty {
                    Text(store.isTurkish ? "Giden Bağlantılar" : "Outgoing Links")
                        .font(.caption2.bold())
                        .foregroundStyle(.secondary)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(outgoingLinks) { link in
                                Button {
                                    if let target = store.activeNotes.first(where: { $0.syncUuid == link.targetSyncUuid }) {
                                        persistCurrentState()
                                        onNavigateToNote(target)
                                    }
                                } label: {
                                    Label(link.linkText, systemImage: "arrow.up.right")
                                        .font(.caption2.weight(.semibold))
                                        .padding(.horizontal, 9)
                                        .padding(.vertical, 4)
                                        .background(PureColors.accentBlue.opacity(0.16))
                                        .foregroundStyle(PureColors.accentBlue)
                                        .clipShape(Capsule())
                                }
                            }
                        }
                    }
                }

                if !backlinks.isEmpty {
                    Text(store.isTurkish ? "Geri Bağlantılar (Backlinks)" : "Backlinks")
                        .font(.caption2.bold())
                        .foregroundStyle(.secondary)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(backlinks) { link in
                                if let src = store.activeNotes.first(where: { $0.syncUuid == link.sourceSyncUuid }) {
                                    Button {
                                        persistCurrentState()
                                        onNavigateToNote(src)
                                    } label: {
                                        Label(src.title, systemImage: "arrow.uturn.left")
                                            .font(.caption2.weight(.semibold))
                                            .padding(.horizontal, 9)
                                            .padding(.vertical, 4)
                                            .background(PureColors.vibrantOrange.opacity(0.16))
                                            .foregroundStyle(PureColors.vibrantOrange)
                                            .clipShape(Capsule())
                                    }
                                }
                            }
                        }
                    }
                }

                HStack(spacing: 8) {
                    Button {
                        newSubPageTitle = ""
                        showSubPageAlert = true
                    } label: {
                        Label(store.isTurkish ? "+ Alt Sayfa" : "+ Sub-Page", systemImage: "doc.badge.plus")
                            .font(.caption2.bold())
                    }
                    Button {
                        showLinkNoteSheet = true
                    } label: {
                        Label(store.isTurkish ? "+ Not Bağla" : "+ Link Note", systemImage: "link.badge.plus")
                            .font(.caption2.bold())
                    }
                }
            }
            .padding(.top, 6)
        } label: {
            Label(store.isTurkish ? "Sayfa İlişkileri & Bağlantılar" : "Page Relations & Backlinks", systemImage: "point.3.connected.trianglepath.dotted")
                .font(.caption.bold())
                .foregroundStyle(PureColors.vibrantOrange)
        }
        .padding(12)
        .pureGlassCard(cornerRadius: 14)
    }

    private var audioRecordingBanner: some View {
        EditorAudioRecordingGlassBanner(
            audio: store.audioManager,
            isTurkish: store.isTurkish,
            onCancel: { store.audioManager.finishRecording(save: false) },
            onSave: { store.audioManager.finishRecording(save: true) }
        )
    }

    private var voiceDictationBanner: some View {
        EditorLiveDictationGlassBanner(
            speech: store.speechManager,
            isTurkish: store.isTurkish,
            onInsert: { text in
                if !text.isEmpty { appendBlock(.paragraph, content: text) }
            },
            onCancel: {
                store.speechManager.stopListening()
                store.speechManager.partialText = ""
            }
        )
    }

    private func smartLinkBanner(_ target: IosNoteRecord) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "link")
                .foregroundStyle(PureColors.accentBlue)
            Text(store.isTurkish ? "Bağla: [[\(target.title)]]" : "Link: [[\(target.title)]]")
                .font(.caption.bold())
            Spacer()
            Button(store.isTurkish ? "Bağlantı Yap" : "Link") {
                if let lastIdx = blocks.indices.last {
                    blocks[lastIdx].content = blocks[lastIdx].content.replacingOccurrences(
                        of: target.title,
                        with: "[[\(target.title)]]",
                        options: .caseInsensitive
                    )
                    persistCurrentState()
                }
            }
            .font(.caption.bold())
            .foregroundStyle(PureColors.accentBlue)
        }
        .padding(8)
        .background(PureColors.accentBlue.opacity(0.12))
    }

    // MARK: - Glass 19-Tool Bottom Toolbar

    private var glassEditorBottomToolbar: some View {
        let isTr = store.isTurkish
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                toolbarIconBtn("plus.circle.fill", tint: PureColors.vibrantOrange, label: "+") {
                    showSlashSheet = true
                }

                toolbarIconBtn(store.speechManager.isListening ? "mic.circle.fill" : "mic.fill", tint: .purple, label: isTr ? "Dikte" : "Voice") {
                    store.speechManager.toggleListening(isTurkish: isTr) { spoken in
                        appendBlock(.paragraph, content: spoken)
                    }
                }

                Menu {
                    Button { showCameraSheet = true } label: {
                        Label(isTr ? "Kamera ile Tara" : "Camera Scan", systemImage: "camera.fill")
                    }
                    PhotosPicker(selection: $ocrPickerItem, matching: .images) {
                        Label(isTr ? "Galeriden OCR" : "Gallery OCR", systemImage: "photo")
                    }
                } label: {
                    toolbarBadge("doc.text.viewfinder", tint: PureColors.accentBlue, label: "OCR")
                }

                toolbarIconBtn("paintbrush.pointed.fill", tint: Color.purple, label: isTr ? "Çizim" : "Draw") {
                    editingDrawingBlockId = nil
                    showDrawingSheet = true
                }

                toolbarIconBtn(store.audioManager.isRecording ? "stop.circle.fill" : "waveform", tint: store.audioManager.isRecording ? .red : PureColors.emeraldGreen, label: isTr ? "Ses" : "Audio") {
                    if store.audioManager.isRecording {
                        store.audioManager.finishRecording(save: true)
                    } else {
                        let stage = store.db.mediaResolver.createStagingFilePath(prefix: "rec", extension: "m4a")
                        store.audioManager.startRecording(stagingPath: stage) { url in
                            Task {
                                if let data = try? Data(contentsOf: url),
                                   let path = await store.storeMediaData(data, subFolder: "audio", extension: "m4a") {
                                    appendMediaBlock(type: .audio, mediaPath: path)
                                }
                            }
                        }
                    }
                }

                PhotosPicker(selection: $photoPickerItem, matching: .images) {
                    toolbarBadge("photo.fill", tint: PureColors.accentBlue, label: isTr ? "Resim" : "Photo")
                }

                toolbarIconBtn("link", tint: PureColors.accentBlue, label: "[[Link]]") {
                    showLinkNoteSheet = true
                }

                toolbarIconBtn("checklist", tint: PureColors.vibrantOrange, label: "Todo") { appendBlock(.todo) }
                toolbarIconBtn("textformat.size.larger", tint: .primary, label: "H1") { appendBlock(.heading1) }
                toolbarIconBtn("textformat.size", tint: .primary, label: "H2") { appendBlock(.heading2) }
                toolbarIconBtn("list.bullet", tint: .primary, label: store.isTurkish ? "Liste" : "List") { appendBlock(.bulletList) }
                toolbarIconBtn("chevron.right.square.fill", tint: .primary, label: "Toggle") { appendBlock(.toggle) }
                toolbarIconBtn("tablecells.fill", tint: PureColors.emeraldGreen, label: store.isTurkish ? "Tablo" : "Table") { appendBlock(.table) }
                toolbarIconBtn("chevron.left.forwardslash.chevron.right", tint: PureColors.accentBlue, label: store.isTurkish ? "Kod" : "Code") { appendBlock(.code) }
                toolbarIconBtn("function", tint: PureColors.vibrantOrange, label: "Math") { appendBlock(.math) }
                toolbarIconBtn("lightbulb.fill", tint: .orange, label: "Callout") { appendBlock(.callout) }
                toolbarIconBtn("list.number", tint: .primary, label: "TOC") { appendBlock(.tableOfContents) }
                toolbarIconBtn("doc.on.doc.fill", tint: PureColors.vibrantOrange, label: store.isTurkish ? "Alt Sayfa" : "Sub") {
                    newSubPageTitle = ""
                    showSubPageAlert = true
                }
                toolbarIconBtn("minus", tint: .secondary, label: "---") { appendBlock(.divider) }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
        }
        .background(.ultraThinMaterial)
    }

    private func toolbarIconBtn(_ symbol: String, tint: Color, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            toolbarBadge(symbol, tint: tint, label: label)
        }
    }

    private func toolbarBadge(_ symbol: String, tint: Color, label: String) -> some View {
        HStack(spacing: 5) {
            Image(systemName: symbol)
                .font(.caption.bold())
                .foregroundStyle(tint)
            Text(label)
                .font(.caption2.weight(.semibold))
                .foregroundStyle(.primary)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 7)
        .background(Color.secondary.opacity(0.14))
        .clipShape(Capsule())
    }

    private func pushUndo() {
        undoStack.append(blocks)
        if undoStack.count > 25 { undoStack.removeFirst() }
        redoStack.removeAll()
    }

    private func appendBlock(_ kind: IosBlockKind, content: String = "") {
        pushUndo()
        let defaultTable = kind == .table ? [["Kalem", "Tutar", "Not"], ["", "0", ""]] : []
        let defaultCode = kind == .code ? "// Kotlin / Python / JS / SQL\n" : content
        blocks.append(
            EditableBlock(
                id: SharedIosEditorBridge.shared.doNewUuid(),
                type: kind,
                content: kind == .code && content.isEmpty ? defaultCode : content,
                isChecked: false,
                calloutIcon: "💡",
                mediaPath: nil,
                isExpanded: true,
                toggleBody: "",
                codeLanguage: "kotlin",
                tableData: defaultTable
            )
        )
        persistCurrentState()
    }

    private func appendMediaBlock(type: IosBlockKind, mediaPath: String) {
        pushUndo()
        blocks.append(
            EditableBlock(
                id: SharedIosEditorBridge.shared.doNewUuid(),
                type: type,
                content: "%100",
                isChecked: false,
                calloutIcon: "💡",
                mediaPath: mediaPath,
                isExpanded: true,
                toggleBody: "",
                codeLanguage: "kotlin",
                tableData: []
            )
        )
        persistCurrentState()
    }

    private func persistCurrentState() {
        note.title = title
        note.icon = icon
        if note.isEncrypted { return }
        store.saveNote(note, blocks: blocks)
        if let updated = store.activeNotes.first(where: { $0.syncUuid == note.syncUuid }) {
            note = updated
        }
    }

    private func statusLabel(_ st: String) -> String {
        switch st {
        case "IN_PROGRESS": return store.isTurkish ? "Devam Ediyor" : "In Progress"
        case "IN_REVIEW": return store.isTurkish ? "İncelemede" : "In Review"
        case "DONE": return store.isTurkish ? "Tamamlandı" : "Done"
        default: return store.isTurkish ? "Yapılacak" : "To Do"
        }
    }
}

// MARK: - Live Audio Recording & Dictation Glass Banners

struct EditorAudioRecordingGlassBanner: View {
    @ObservedObject var audio: IosAudioManager
    let isTurkish: Bool
    let onCancel: () -> Void
    let onSave: () -> Void

    @State private var pulse = false

    var body: some View {
        HStack(spacing: 12) {
            HStack(spacing: 6) {
                Circle()
                    .fill(Color.red)
                    .frame(width: 9, height: 9)
                    .scaleEffect(pulse ? 1.35 : 0.85)
                    .opacity(pulse ? 1.0 : 0.45)
                Text(String(format: "%02d:%02d", audio.recordingSeconds / 60, audio.recordingSeconds % 60))
                    .font(.subheadline.weight(.bold).monospacedDigit())
                    .foregroundStyle(.red)
            }

            LiquidAudioWaveformView(
                isActive: audio.isRecording,
                level: audio.currentLevel,
                tint: .red,
                height: 28
            )
            .frame(maxWidth: .infinity)

            Button(isTurkish ? "İptal" : "Cancel", action: onCancel)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)

            Button(action: onSave) {
                Label(isTurkish ? "Kaydet" : "Save", systemImage: "checkmark.circle.fill")
                    .font(.caption.weight(.bold))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(PureColors.emeraldGreen)
                    .foregroundStyle(.white)
                    .clipShape(Capsule())
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .pureGlassCard(cornerRadius: 18)
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(Color.red.opacity(0.35), lineWidth: 1)
        )
        .onAppear {
            withAnimation(.easeInOut(duration: 0.7).repeatForever(autoreverses: true)) {
                pulse = true
            }
        }
    }
}

struct EditorLiveDictationGlassBanner: View {
    @ObservedObject var speech: IosSpeechDictationManager
    let isTurkish: Bool
    let onInsert: (String) -> Void
    let onCancel: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                Image(systemName: "waveform.and.mic")
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(.purple)

                Text(isTurkish ? "Canlı Sesli Dikte" : "Live Voice Dictation")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.purple)

                LiquidAudioWaveformView(
                    isActive: speech.isListening,
                    level: speech.audioLevel,
                    tint: .purple,
                    height: 18
                )
                .frame(maxWidth: .infinity)

                Button(isTurkish ? "İptal" : "Cancel", action: onCancel)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)

                Button {
                    let spoken = speech.partialText.trimmingCharacters(in: .whitespacesAndNewlines)
                    speech.stopListening()
                    speech.partialText = ""
                    onInsert(spoken)
                } label: {
                    Text(isTurkish ? "Ekle" : "Insert")
                        .font(.caption.weight(.bold))
                        .padding(.horizontal, 11)
                        .padding(.vertical, 5)
                        .background(Color.purple)
                        .foregroundStyle(.white)
                        .clipShape(Capsule())
                }
            }

            Text(
                speech.partialText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                    ? (isTurkish ? "Konuşmaya başlayın, kelimeler burada anlık belirecek..." : "Start speaking — words appear here live...")
                    : speech.partialText
            )
            .font(.subheadline.weight(.medium))
            .foregroundStyle(speech.partialText.isEmpty ? .secondary : .primary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .pureGlassCard(cornerRadius: 18)
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(Color.purple.opacity(0.35), lineWidth: 1)
        )
    }
}

// MARK: - Supporting Editor Sheets (Slash, Zen, PDF Studio, Reminder, Link, Lightbox, Camera)

struct SlashBlockPickerSheet: View {
    let isTurkish: Bool
    let onSelect: (IosBlockKind) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var query = ""

    private var filtered: [IosBlockKind] {
        IosBlockKind.allCases.filter { query.isEmpty || $0.title(isTr: isTurkish).localizedCaseInsensitiveContains(query) }
    }

    var body: some View {
        NavigationStack {
            List(filtered) { kind in
                Button {
                    onSelect(kind)
                    dismiss()
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: kind.systemImage)
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(PureColors.vibrantOrange)
                            .frame(width: 30, height: 30)
                            .background(PureColors.vibrantOrange.opacity(0.14))
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                        Text(kind.title(isTr: isTurkish))
                            .font(.subheadline.weight(.medium))
                            .foregroundStyle(.primary)
                        Spacer()
                        Image(systemName: "plus.circle")
                            .font(.caption)
                            .foregroundStyle(.tertiary)
                    }
                }
                .listRowBackground(Color.clear)
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .searchable(text: $query)
            .navigationTitle(isTurkish ? "Blok Ekle (/)" : "Insert Block (/)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(isTurkish ? "Kapat" : "Close") { dismiss() }
                }
            }
        }
        .presentationBackground(.ultraThinMaterial)
        .presentationDetents([.medium, .large])
    }
}

struct ZenReadingView: View {
    @ObservedObject var store: PureNoteStore
    let title: String
    let icon: String
    let blocks: [EditableBlock]
    let onLightbox: (URL) -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    HStack(spacing: 12) {
                        ModernNoteIconBadge(iconKey: icon, size: 40, iconSize: 19)
                        Text(title.isEmpty ? (store.isTurkish ? "Başlıksız" : "Untitled") : title)
                            .font(.system(size: 28, weight: .heavy))
                    }

                    ForEach(blocks) { block in
                        switch block.type {
                        case .heading1: Text(block.content).font(.title2.bold())
                        case .heading2: Text(block.content).font(.title3.weight(.semibold))
                        case .todo:
                            Label(block.content, systemImage: block.isChecked ? "checkmark.square.fill" : "square")
                                .foregroundStyle(block.isChecked ? .secondary : .primary)
                        case .bulletList: Text("• \(block.content)")
                        case .callout:
                            HStack(spacing: 10) {
                                ModernCalloutBadge(key: block.calloutIcon)
                                Text(block.content)
                            }
                            .padding(12)
                            .background(PureColors.vibrantOrange.opacity(0.12))
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                        case .code:
                            Text(block.content)
                                .font(.system(.footnote, design: .monospaced))
                                .padding(12)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(Color(red: 0.12, green: 0.12, blue: 0.14))
                                .foregroundStyle(.white)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                        case .image, .drawing:
                            if let url = store.resolveMediaURL(block.mediaPath),
                               let img = UIImage(contentsOfFile: url.path) {
                                Button { onLightbox(url) } label: {
                                    Image(uiImage: img)
                                        .resizable()
                                        .scaledToFit()
                                        .clipShape(RoundedRectangle(cornerRadius: 12))
                                }
                            }
                        case .audio:
                            if let url = store.resolveMediaURL(block.mediaPath) {
                                Button {
                                    store.audioManager.togglePlayback(canonicalOrPath: block.mediaPath ?? "", resolvedPath: url.path)
                                } label: {
                                    Label(store.isTurkish ? "Ses Kaydını Oynat" : "Play Audio", systemImage: "waveform")
                                        .padding(10)
                                        .pureGlassCapsule()
                                }
                            }
                        case .divider: Divider()
                        default:
                            if !block.content.isEmpty {
                                Text(block.content).font(.body).lineSpacing(6)
                            }
                        }
                    }
                }
                .padding(24)
            }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button { dismiss() } label: {
                        Label(store.isTurkish ? "Düzenlemeye Dön" : "Exit Zen", systemImage: "xmark.circle.fill")
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .pureGlassCapsule()
                    }
                }
            }
        }
    }
}

struct PdfExportStudioSheet: View {
    @ObservedObject var store: PureNoteStore
    let title: String
    let blocks: [EditableBlock]

    @Environment(\.dismiss) private var dismiss
    @State private var options = PdfExportOptions()
    @State private var generatedPdfURL: URL? = nil
    @State private var generatedMdURL: URL? = nil

    var body: some View {
        NavigationStack {
            Form {
                Section(store.isTurkish ? "Dışa Aktarım İçeriği" : "Export Options") {
                    Toggle(store.isTurkish ? "Metin & Kod Blokları" : "Text & Code", isOn: $options.includeText)
                    Toggle(store.isTurkish ? "Görev Listeleri" : "Checklists", isOn: $options.includeTasks)
                    Toggle(store.isTurkish ? "Görseller" : "Images", isOn: $options.includeImages)
                    Toggle(store.isTurkish ? "Çizimler" : "Drawings", isOn: $options.includeDrawings)
                    Toggle(store.isTurkish ? "Ses Özeti Rozeti" : "Audio Badges", isOn: $options.includeAudioBadge)
                    Toggle(store.isTurkish ? "A4 Üst/Alt Bilgi" : "A4 Header & Footer", isOn: $options.includeHeader)
                }

                Section {
                    Button {
                        generatedPdfURL = IosExporterAndBackupService.exportPdf(
                            title: title,
                            blocks: blocks,
                            options: options,
                            mediaResolver: store.db.mediaResolver
                        )
                    } label: {
                        Label(store.isTurkish ? "A4 PDF Oluştur" : "Generate A4 PDF", systemImage: "doc.richtext.fill")
                    }

                    if let pdfUrl = generatedPdfURL {
                        ShareLink(item: pdfUrl) {
                            Label(store.isTurkish ? "PDF Paylaş" : "Share PDF", systemImage: "square.and.arrow.up")
                                .foregroundStyle(PureColors.emeraldGreen)
                        }
                    }

                    Button {
                        generatedMdURL = IosExporterAndBackupService.exportMarkdown(title: title, blocks: blocks)
                    } label: {
                        Label(store.isTurkish ? "Markdown (.md) Oluştur" : "Export Markdown (.md)", systemImage: "chevron.left.forwardslash.chevron.right")
                    }

                    if let mdUrl = generatedMdURL {
                        ShareLink(item: mdUrl) {
                            Label(store.isTurkish ? "Markdown Paylaş" : "Share Markdown", systemImage: "square.and.arrow.up")
                                .foregroundStyle(PureColors.accentBlue)
                        }
                    }
                }
            }
            .navigationTitle(store.isTurkish ? "PDF Stüdyosu" : "PDF Studio")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(store.isTurkish ? "Kapat" : "Close") { dismiss() }
                }
            }
        }
    }
}

struct ReminderPickerSheet: View {
    @ObservedObject var store: PureNoteStore
    @Binding var note: IosNoteRecord
    let onSaved: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var selectedDate = Date().addingTimeInterval(3600)
    @State private var reminderType = "NOTIFICATION"

    var body: some View {
        NavigationStack {
            Form {
                Section(store.isTurkish ? "Hızlı Seçim" : "Quick Presets") {
                    HStack(spacing: 10) {
                        Button("+1 Saat") { selectedDate = Date().addingTimeInterval(3600) }
                        Button("20:00") {
                            selectedDate = Calendar.current.date(bySettingHour: 20, minute: 0, second: 0, of: Date()) ?? Date()
                        }
                        Button(store.isTurkish ? "Yarın 09:00" : "Tom 09:00") {
                            let tom = Calendar.current.date(byAdding: .day, value: 1, to: Date()) ?? Date()
                            selectedDate = Calendar.current.date(bySettingHour: 9, minute: 0, second: 0, of: tom) ?? tom
                        }
                    }
                    .buttonStyle(.bordered)
                }

                Section {
                    DatePicker(store.isTurkish ? "Tarih & Saat" : "Date & Time", selection: $selectedDate)
                    Picker(store.isTurkish ? "Tür" : "Type", selection: $reminderType) {
                        Text(store.isTurkish ? "Bildirim" : "Notification").tag("NOTIFICATION")
                        Text("Alarm").tag("ALARM")
                    }
                    .pickerStyle(.segmented)
                }

                if note.reminderTime != nil {
                    Button(role: .destructive) {
                        note.reminderTime = nil
                        IosReminderService.cancelReminder(noteSyncUuid: note.syncUuid)
                        onSaved()
                        dismiss()
                    } label: {
                        Text(store.isTurkish ? "Hatırlatıcıyı İptal Et" : "Cancel Reminder")
                    }
                }
            }
            .navigationTitle(store.isTurkish ? "Hatırlatıcı" : "Reminder")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(store.isTurkish ? "İptal" : "Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(store.isTurkish ? "Kur" : "Set") {
                        Task {
                            note.reminderTime = Int64(selectedDate.timeIntervalSince1970 * 1000)
                            note.reminderType = reminderType
                            _ = await IosReminderService.scheduleReminder(
                                noteSyncUuid: note.syncUuid,
                                title: note.title,
                                isProtected: store.isNoteProtected(note),
                                fireDate: selectedDate,
                                type: reminderType
                            )
                            onSaved()
                            dismiss()
                        }
                    }
                }
            }
        }
        .presentationDetents([.medium])
    }
}

struct LinkExistingNoteSheet: View {
    @ObservedObject var store: PureNoteStore
    let currentUuid: String
    let onPick: (IosNoteRecord) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var query = ""

    var body: some View {
        NavigationStack {
            List(store.activeNotes.filter {
                $0.syncUuid != currentUuid &&
                (query.isEmpty || $0.title.localizedCaseInsensitiveContains(query))
            }) { n in
                Button {
                    onPick(n)
                    dismiss()
                } label: {
                    HStack(spacing: 10) {
                        ModernNoteIconBadge(iconKey: n.icon, isProtected: store.isNoteProtected(n), size: 28, iconSize: 14)
                        Text(n.title.isEmpty ? (store.isTurkish ? "Başlıksız" : "Untitled") : n.title)
                            .foregroundStyle(.primary)
                    }
                }
            }
            .searchable(text: $query)
            .navigationTitle(store.isTurkish ? "Not Bağla [[...]]" : "Link Note [[...]]")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(store.isTurkish ? "Kapat" : "Close") { dismiss() }
                }
            }
        }
    }
}

struct MediaLightboxView: View {
    let imageURL: URL
    @Environment(\.dismiss) private var dismiss
    @State private var zoom: CGFloat = 1.0

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.ignoresSafeArea()
            if let img = UIImage(contentsOfFile: imageURL.path) {
                Image(uiImage: img)
                    .resizable()
                    .scaledToFit()
                    .scaleEffect(zoom)
                    .gesture(
                        MagnificationGesture().onChanged { zoom = max(1, min(4, $0)) }
                    )
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            Button { dismiss() } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.title)
                    .foregroundStyle(.white)
                    .padding(20)
            }
        }
    }
}

extension URL: @retroactive Identifiable {
    public var id: String { absoluteString }
}

struct CameraOcrCaptureView: UIViewControllerRepresentable {
    let onCapture: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = UIImagePickerController.isSourceTypeAvailable(.camera) ? .camera : .photoLibrary
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }

    final class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        let parent: CameraOcrCaptureView
        init(parent: CameraOcrCaptureView) { self.parent = parent }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let img = info[.originalImage] as? UIImage {
                parent.onCapture(img)
            }
            parent.dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.dismiss()
        }
    }
}
