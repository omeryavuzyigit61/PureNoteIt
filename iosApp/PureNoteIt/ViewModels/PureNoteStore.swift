import Combine
import Foundation
import PureNoteShared
import SwiftUI
import UIKit

enum PureViewMode: String, CaseIterable, Identifiable {
    case grid
    case list
    case board
    case calendar
    case graph
    case whiteboard
    case stream

    var id: String { rawValue }

    func title(isTr: Bool) -> String {
        switch self {
        case .grid: return isTr ? "Izgara" : "Grid"
        case .list: return isTr ? "Liste" : "List"
        case .board: return isTr ? "Kanban" : "Board"
        case .calendar: return isTr ? "Takvim" : "Calendar"
        case .graph: return isTr ? "2D Grafik" : "2D Graph"
        case .whiteboard: return isTr ? "Sonsuz Tuval" : "Whiteboard"
        case .stream: return isTr ? "Fikir Akışı" : "Stream"
        }
    }

    var systemImage: String {
        switch self {
        case .grid: return "square.grid.2x2.fill"
        case .list: return "list.bullet.rectangle.portrait.fill"
        case .board: return "rectangle.split.3x1.fill"
        case .calendar: return "calendar"
        case .graph: return "point.3.connected.trianglepath.dotted"
        case .whiteboard: return "scribble.variable"
        case .stream: return "bolt.horizontal.circle.fill"
        }
    }
}

enum PureSmartFilter: String, CaseIterable, Identifiable {
    case all
    case pinned
    case locked
    case reminders
    case photos
    case audio
    case tasks
    case tables

    var id: String { rawValue }

    func title(isTr: Bool) -> String {
        switch self {
        case .all: return isTr ? "Tümü" : "All"
        case .pinned: return isTr ? "Sabitlenenler" : "Pinned"
        case .locked: return isTr ? "Kilitli" : "Locked"
        case .reminders: return isTr ? "Hatırlatıcılar" : "Reminders"
        case .photos: return isTr ? "Fotoğraflar" : "Photos"
        case .audio: return isTr ? "Ses Kayıtları" : "Audio"
        case .tasks: return isTr ? "Görevler" : "Tasks"
        case .tables: return isTr ? "Tablolar" : "Tables"
        }
    }

    var systemImage: String {
        switch self {
        case .all: return "tray.full.fill"
        case .pinned: return "pin.fill"
        case .locked: return "lock.fill"
        case .reminders: return "alarm.fill"
        case .photos: return "photo.fill"
        case .audio: return "waveform"
        case .tasks: return "checklist"
        case .tables: return "tablecells.fill"
        }
    }
}

struct EditableBlock: Identifiable, Equatable {
    let id: String
    var type: IosBlockKind
    var content: String
    var isChecked: Bool
    var calloutIcon: String
    var mediaPath: String?
    var isExpanded: Bool
    var toggleBody: String
    var codeLanguage: String
    var tableData: [[String]]
    var subPageSyncUuid: String?
    var subPageNoteId: Int?
}

enum IosBlockKind: String, CaseIterable, Identifiable {
    case paragraph
    case heading1
    case heading2
    case todo
    case bulletList
    case callout
    case toggle
    case table
    case divider
    case audio
    case image
    case drawing
    case webBookmark
    case subPage
    case code
    case math
    case tableOfContents

    var id: String { rawValue }

    var kotlinType: BlockType {
        switch self {
        case .paragraph: return .paragraph
        case .heading1: return .heading1
        case .heading2: return .heading2
        case .todo: return .todo
        case .bulletList: return .bulletList
        case .callout: return .callout
        case .toggle: return .toggle
        case .table: return .table
        case .divider: return .divider
        case .audio: return .audio
        case .image: return .image
        case .drawing: return .drawing
        case .webBookmark: return .webBookmark
        case .subPage: return .subPage
        case .code: return .code
        case .math: return .math
        case .tableOfContents: return .tableOfContents
        }
    }

    static func fromKotlin(_ type: BlockType) -> IosBlockKind {
        switch type {
        case .heading1: return .heading1
        case .heading2: return .heading2
        case .todo: return .todo
        case .bulletList: return .bulletList
        case .callout: return .callout
        case .toggle: return .toggle
        case .table: return .table
        case .divider: return .divider
        case .audio: return .audio
        case .image: return .image
        case .drawing: return .drawing
        case .webBookmark: return .webBookmark
        case .subPage: return .subPage
        case .code: return .code
        case .math: return .math
        case .tableOfContents: return .tableOfContents
        default: return .paragraph
        }
    }

    func title(isTr: Bool) -> String {
        switch self {
        case .paragraph: return isTr ? "Paragraf" : "Paragraph"
        case .heading1: return isTr ? "Başlık 1" : "Heading 1"
        case .heading2: return isTr ? "Başlık 2" : "Heading 2"
        case .todo: return isTr ? "Yapılacak" : "Checklist"
        case .bulletList: return isTr ? "Madde İşareti" : "Bullet List"
        case .callout: return isTr ? "Önemli Kutu" : "Callout"
        case .toggle: return isTr ? "Açılır Kapanır" : "Toggle"
        case .table: return isTr ? "Tablo (∑)" : "Table (∑)"
        case .divider: return isTr ? "Ayırıcı" : "Divider"
        case .audio: return isTr ? "Ses Kaydı" : "Audio"
        case .image: return isTr ? "Görsel" : "Image"
        case .drawing: return isTr ? "Çizim" : "Drawing"
        case .webBookmark: return isTr ? "Web Yer İmi" : "Web Bookmark"
        case .subPage: return isTr ? "Alt Sayfa" : "Sub-Page"
        case .code: return isTr ? "Kod Bloğu" : "Code"
        case .math: return isTr ? "Matematik" : "Math"
        case .tableOfContents: return isTr ? "İçindekiler" : "Table of Contents"
        }
    }

    var systemImage: String {
        switch self {
        case .paragraph: return "text.alignleft"
        case .heading1: return "textformat.size.larger"
        case .heading2: return "textformat.size"
        case .todo: return "checklist"
        case .bulletList: return "list.bullet"
        case .callout: return "lightbulb.fill"
        case .toggle: return "chevron.right.square.fill"
        case .table: return "tablecells.fill"
        case .divider: return "minus"
        case .audio: return "waveform"
        case .image: return "photo.fill"
        case .drawing: return "paintbrush.pointed.fill"
        case .webBookmark: return "globe"
        case .subPage: return "doc.on.doc.fill"
        case .code: return "chevron.left.forwardslash.chevron.right"
        case .math: return "function"
        case .tableOfContents: return "list.number"
        }
    }
}

@MainActor
final class PureNoteStore: ObservableObject {
    @Published var categories: [IosCategoryRecord] = []
    @Published var activeNotes: [IosNoteRecord] = []
    @Published var trashedNotes: [IosNoteRecord] = []
    @Published var noteLinks: [IosNoteLinkRecord] = []
    @Published var streamItems: [IosStreamItemRecord] = []
    @Published var automationRules: [IosAutomationRuleRecord] = []
    @Published var whiteboardPositions: [String: CGPoint] = [:]

    @Published var viewMode: PureViewMode = .grid
    @Published var selectedCategoryUuid: String? = nil
    @Published var smartFilter: PureSmartFilter = .all
    @Published var selectedHashtag: String? = nil
    @Published var searchQuery: String = ""

    @Published var isMultiSelectMode: Bool = false
    @Published var selectedNoteUuids: Set<String> = []

    @Published var themePreference: AppThemePreference = .dark
    @Published var isTurkish: Bool = true
    @Published var trashRetentionDays: Int = 30

    @Published var isDriveConnected: Bool = false
    @Published var isSyncing: Bool = false
    @Published var syncStatusMessage: String? = nil
    @Published var isSyncError: Bool = false
    @Published var customIosOAuthClientId: String = ""
    @Published var toastMessage: String? = nil

    let db: IosDatabaseManager
    let vaultCrypto: VaultCrypto
    let tokenStorage: IosSecureTokenStorage
    let localSyncStore: IosLocalSyncStore
    let oauthManager = IosOAuthPkceManager()
    let securityManager = IosVaultSecurityManager()
    let audioManager = IosAudioManager()
    let speechManager = IosSpeechDictationManager()

    private let appClock = AppClockAdapter()
    private var cancellables = Set<AnyCancellable>()

    init() {
        let database = IosDatabaseManager()
        self.db = database
        self.vaultCrypto = SharedIosCryptoFactory.shared.createVaultCrypto(bridge: AppleCryptoBridge())
        self.tokenStorage = IosSecureTokenStorage()
        self.localSyncStore = IosLocalSyncStore(db: database)

        audioManager.objectWillChange
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &cancellables)
        speechManager.objectWillChange
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &cancellables)
        securityManager.objectWillChange
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &cancellables)

        if let savedTheme = UserDefaults.standard.string(forKey: "purenote_theme"),
           let pref = AppThemePreference(rawValue: savedTheme) {
            self.themePreference = pref
        }
        if UserDefaults.standard.object(forKey: "purenote_is_tr") != nil {
            self.isTurkish = UserDefaults.standard.bool(forKey: "purenote_is_tr")
        }
        let ret = UserDefaults.standard.object(forKey: "purenote_trash_retention") as? Int
        self.trashRetentionDays = ret ?? 30
        if self.trashRetentionDays > 0 {
            self.db.purgeExpiredTrash(retentionDays: self.trashRetentionDays)
        }

        self.customIosOAuthClientId = resolvedIosOAuthClientId()
        reloadAll()
        Task { await refreshDriveStatus() }
    }

    var preferredColorScheme: ColorScheme? {
        switch themePreference {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }

    func setTheme(_ pref: AppThemePreference) {
        themePreference = pref
        UserDefaults.standard.set(pref.rawValue, forKey: "purenote_theme")
    }

    func toggleLanguage() {
        isTurkish.toggle()
        UserDefaults.standard.set(isTurkish, forKey: "purenote_is_tr")
    }

    func setTrashRetention(_ days: Int) {
        trashRetentionDays = days
        UserDefaults.standard.set(days, forKey: "purenote_trash_retention")
        if days > 0 {
            db.purgeExpiredTrash(retentionDays: days)
            reloadAll()
        }
    }

    func showToast(_ message: String) {
        toastMessage = message
        Task {
            try? await Task.sleep(nanoseconds: 2_600_000_000)
            if self.toastMessage == message {
                self.toastMessage = nil
            }
        }
    }

    func reloadAll() {
        categories = db.fetchActiveCategories()
        let all = db.fetchNotes(includeDeleted: false)
        activeNotes = all.filter { $0.status != "TRASH" }
        trashedNotes = all.filter { $0.status == "TRASH" }
        noteLinks = db.fetchLinks(includeDeleted: false)
        streamItems = db.fetchStreamItems(includeDeleted: false)
        automationRules = db.fetchAutomationRules()
        whiteboardPositions = db.loadWhiteboardPositions()
    }

    // MARK: - Protection & Search Oracle Guard

    var lockedCategoryUuids: Set<String> {
        Set(categories.filter { $0.isPassword }.map { $0.syncUuid })
    }

    func isNoteProtected(_ note: IosNoteRecord) -> Bool {
        if note.isPassword || note.isEncrypted { return true }
        if let catUuid = note.categorySyncUuid, lockedCategoryUuids.contains(catUuid) {
            return true
        }
        return false
    }

    func isSubPage(_ note: IosNoteRecord) -> Bool {
        if let meta = BlockParser.shared.parseRichMetadata(rawJson: note.richBlocksJson), meta.isSubPage {
            return true
        }
        return activeNotes.contains { parent in
            parent.syncUuid != note.syncUuid &&
            !parent.isEncrypted &&
            BlockParser.shared.parse(raw: parent.richBlocksJson).contains {
                $0.type == .subPage && ($0.subPageSyncUuid == note.syncUuid || $0.subPageNoteId?.intValue == note.rowId)
            }
        }
    }

    var allHashtags: [String] {
        var tags: [String] = []
        var seen = Set<String>()
        for note in activeNotes {
            if isNoteProtected(note) && !securityManager.isVaultUnlocked { continue }
            let combined = "\(note.title) \(plainSummary(for: note))"
            for tag in SharedIosEditorBridge.shared.extractHashtags(text: combined) {
                if seen.insert(tag.lowercased()).inserted {
                    tags.append(tag)
                }
            }
        }
        return tags.sorted()
    }

    func activeRootCount(for categoryUuid: String?) -> Int {
        activeNotes.filter { note in
            !isSubPage(note) && (categoryUuid == nil || note.categorySyncUuid == categoryUuid)
        }.count
    }

    var sortedCategories: [IosCategoryRecord] {
        categories.sorted { a, b in
            let ca = activeRootCount(for: a.syncUuid)
            let cb = activeRootCount(for: b.syncUuid)
            if ca != cb { return ca > cb }
            return a.name.localizedCaseInsensitiveCompare(b.name) == .orderedAscending
        }
    }

    var filteredNotes: [IosNoteRecord] {
        let query = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        return activeNotes.filter { note in
            if query.isEmpty && isSubPage(note) { return false }
            if let cat = selectedCategoryUuid, note.categorySyncUuid != cat { return false }

            let isLocked = isNoteProtected(note)
            switch smartFilter {
            case .all: break
            case .pinned: if !note.isPinned { return false }
            case .locked: if !isLocked { return false }
            case .reminders: if note.reminderTime == nil { return false }
            case .photos:
                if isLocked || (note.images.isEmpty && !hasBlockType(note, .image) && !hasBlockType(note, .drawing)) {
                    return false
                }
            case .audio:
                if isLocked || (note.sounds.isEmpty && !hasBlockType(note, .audio)) {
                    return false
                }
            case .tasks: if note.totalTasks == 0 { return false }
            case .tables: if isLocked || !hasBlockType(note, .table) { return false }
            }

            if let tag = selectedHashtag {
                if isLocked && !securityManager.isVaultUnlocked { return false }
                let tags = SharedIosEditorBridge.shared.extractHashtags(text: "\(note.title) \(plainSummary(for: note))")
                if !tags.contains(where: { $0.caseInsensitiveCompare(tag) == .orderedSame }) {
                    return false
                }
            }

            if !query.isEmpty {
                if isLocked && !securityManager.isVaultUnlocked { return false }
                if note.title.localizedCaseInsensitiveContains(query) { return true }
                if note.isEncrypted { return false }
                return BlockParser.shared.findSearchSnippet(rawDescription: note.richBlocksJson, query: query) != nil
            }
            return true
        }
    }

    var recentNotes: [IosNoteRecord] {
        Array(activeNotes.filter { !isSubPage($0) }.sorted { $0.updatedAt > $1.updatedAt }.prefix(6))
    }

    private func hasBlockType(_ note: IosNoteRecord, _ kind: IosBlockKind) -> Bool {
        guard !note.isEncrypted else { return false }
        return BlockParser.shared.parse(raw: note.richBlocksJson).contains { $0.type == kind.kotlinType }
    }

    func plainSummary(for note: IosNoteRecord) -> String {
        if note.isEncrypted {
            return isTurkish ? "AES-256-GCM şifreli kasa notu" : "Encrypted AES-256-GCM note"
        }
        let query = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        if !query.isEmpty,
           let match = BlockParser.shared.findSearchSnippet(rawDescription: note.richBlocksJson, query: query) {
            return "🔍 \(match.snippet)"
        }
        let blocks = BlockParser.shared.parse(raw: note.richBlocksJson)
        let summary = BlockParser.shared.toPlainTextSummary(blocks: blocks, maxLines: 2)
        return summary.isEmpty ? (isTurkish ? "Boş sayfa" : "Empty page") : summary
    }

    // MARK: - Block Parsing & Serialization

    func parseBlocks(raw: String) -> [EditableBlock] {
        BlockParser.shared.parse(raw: raw).map { b in
            EditableBlock(
                id: b.id,
                type: IosBlockKind.fromKotlin(b.type),
                content: b.content,
                isChecked: b.isChecked,
                calloutIcon: b.calloutIcon,
                mediaPath: b.mediaPath,
                isExpanded: b.isExpanded,
                toggleBody: b.toggleBody,
                codeLanguage: b.codeLanguage,
                tableData: b.tableData,
                subPageSyncUuid: b.subPageSyncUuid,
                subPageNoteId: b.subPageNoteId?.intValue
            )
        }
    }

    func serializeBlocks(_ blocks: [EditableBlock]) -> (json: String, totalTasks: Int, completedTasks: Int, images: [String], sounds: [String]) {
        let kotlinBlocks: [NoteBlock] = blocks.map { b in
            NoteBlock(
                id: b.id,
                type: b.type.kotlinType,
                content: b.content,
                isChecked: b.isChecked,
                calloutIcon: b.calloutIcon,
                mediaPath: b.mediaPath,
                isExpanded: b.isExpanded,
                tableData: b.tableData,
                toggleBody: b.toggleBody,
                codeLanguage: b.codeLanguage,
                subPageNoteId: b.subPageNoteId.map { KotlinInt(value: Int32($0)) },
                subPageSyncUuid: b.subPageSyncUuid
            )
        }
        let json = BlockParser.shared.blocksToJson(blocks: kotlinBlocks)
        let tasks = BlockParser.shared.calculateTasks(blocks: kotlinBlocks)
        let imgs = blocks.filter { ($0.type == .image || $0.type == .drawing) && $0.mediaPath != nil }.compactMap { $0.mediaPath }
        let snds = blocks.filter { $0.type == .audio && $0.mediaPath != nil }.compactMap { $0.mediaPath }
        return (json, tasks.first?.intValue ?? 0, tasks.second?.intValue ?? 0, imgs, snds)
    }

    // MARK: - Note CRUD & Automation

    func createNote(
        title: String = "",
        icon: String = "📄",
        blocks: [EditableBlock]? = nil,
        status: String = "TODO",
        categoryUuid: String? = nil,
        dateMillis: Int64? = nil
    ) -> IosNoteRecord {
        let now = dateMillis ?? Int64(Date().timeIntervalSince1970 * 1000)
        let effectiveCat = categoryUuid ?? selectedCategoryUuid ?? categories.first?.syncUuid
        let catRowId = categories.first(where: { $0.syncUuid == effectiveCat })?.rowId
        let initialBlocks = blocks ?? [
            EditableBlock(
                id: SharedIosEditorBridge.shared.doNewUuid(),
                type: .paragraph,
                content: "",
                isChecked: false,
                calloutIcon: "💡",
                mediaPath: nil,
                isExpanded: true,
                toggleBody: "",
                codeLanguage: "kotlin",
                tableData: []
            )
        ]
        let ser = serializeBlocks(initialBlocks)
        var note = IosNoteRecord(
            rowId: 0,
            syncUuid: SharedIosEditorBridge.shared.doNewUuid(),
            title: title,
            description: ser.json,
            richBlocksJson: ser.json,
            categoryId: catRowId,
            categorySyncUuid: effectiveCat,
            isPinned: false,
            isPassword: false,
            isEncrypted: false,
            icon: icon,
            images: ser.images,
            sounds: ser.sounds,
            totalTasks: ser.totalTasks,
            completedTasks: ser.completedTasks,
            status: status,
            reminderTime: dateMillis,
            reminderType: dateMillis != nil ? "NOTIFICATION" : nil,
            reminderSound: nil,
            isReminderDone: false,
            date: now,
            updatedAt: Int64(Date().timeIntervalSince1970 * 1000),
            deletedAt: nil,
            syncVersion: 1
        )
        note.rowId = db.upsertNote(note)
        reloadAll()
        return note
    }

    func createFromTemplate(_ template: IosNoteTemplate) -> IosNoteRecord {
        let blocks = template.buildBlocks(isTurkish)
        let note = createNote(title: template.title(isTr: isTurkish), icon: template.iconKey, blocks: blocks)
        showToast(isTurkish ? "Şablon oluşturuldu: \(note.title)" : "Template created: \(note.title)")
        return note
    }

    func createDailyAgendaNote() -> IosNoteRecord {
        let now = Date()
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: isTurkish ? "tr_TR" : "en_US")
        formatter.dateFormat = "d MMMM yyyy, EEEE"
        let todayStr = formatter.string(from: now)
        let dailyCat = categories.first { $0.name.caseInsensitiveCompare("Günlük") == .orderedSame }?.syncUuid

        let blocks: [EditableBlock] = [
            EditableBlock(id: SharedIosEditorBridge.shared.doNewUuid(), type: .callout, content: isTurkish ? "Günün Mottosu: Küçük adımlar, büyük zaferler doğurur." : "Daily Motto: Small steps lead to big wins.", isChecked: false, calloutIcon: "☀️", mediaPath: nil, isExpanded: true, toggleBody: "", codeLanguage: "kotlin", tableData: []),
            EditableBlock(id: SharedIosEditorBridge.shared.doNewUuid(), type: .heading2, content: isTurkish ? "Öncelikli Görevler" : "Priority Tasks", isChecked: false, calloutIcon: "💡", mediaPath: nil, isExpanded: true, toggleBody: "", codeLanguage: "kotlin", tableData: []),
            EditableBlock(id: SharedIosEditorBridge.shared.doNewUuid(), type: .todo, content: isTurkish ? "En kritik 1. işi tamamla" : "Complete #1 priority task", isChecked: false, calloutIcon: "💡", mediaPath: nil, isExpanded: true, toggleBody: "", codeLanguage: "kotlin", tableData: []),
            EditableBlock(id: SharedIosEditorBridge.shared.doNewUuid(), type: .todo, content: isTurkish ? "Önemli e-postaları yanıtla" : "Reply to key emails", isChecked: false, calloutIcon: "💡", mediaPath: nil, isExpanded: true, toggleBody: "", codeLanguage: "kotlin", tableData: []),
            EditableBlock(id: SharedIosEditorBridge.shared.doNewUuid(), type: .todo, content: isTurkish ? "Günün değerlendirmesini yap" : "Evening review", isChecked: false, calloutIcon: "💡", mediaPath: nil, isExpanded: true, toggleBody: "", codeLanguage: "kotlin", tableData: []),
            EditableBlock(id: SharedIosEditorBridge.shared.doNewUuid(), type: .divider, content: "", isChecked: false, calloutIcon: "💡", mediaPath: nil, isExpanded: true, toggleBody: "", codeLanguage: "kotlin", tableData: []),
            EditableBlock(id: SharedIosEditorBridge.shared.doNewUuid(), type: .heading2, content: isTurkish ? "Günün Notları & Düşünceler" : "Daily Notes & Thoughts", isChecked: false, calloutIcon: "💡", mediaPath: nil, isExpanded: true, toggleBody: "", codeLanguage: "kotlin", tableData: []),
            EditableBlock(id: SharedIosEditorBridge.shared.doNewUuid(), type: .paragraph, content: "", isChecked: false, calloutIcon: "💡", mediaPath: nil, isExpanded: true, toggleBody: "", codeLanguage: "kotlin", tableData: [])
        ]
        let note = createNote(
            title: isTurkish ? "Ajanda: \(todayStr)" : "Agenda: \(todayStr)",
            icon: "📅",
            blocks: blocks,
            status: "TODO",
            categoryUuid: dailyCat
        )
        showToast(isTurkish ? "Günün ajandası oluşturuldu" : "Daily agenda created")
        return note
    }

    func createSubPage(parent: IosNoteRecord, title: String) -> IosNoteRecord {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let metaJson = BlockParser.shared.buildSubPageMetadataJson(
            parentNoteId: Int32(parent.rowId),
            parentSyncUuid: parent.syncUuid
        )
        let initBlock = EditableBlock(id: SharedIosEditorBridge.shared.doNewUuid(), type: .paragraph, content: "", isChecked: false, calloutIcon: "💡", mediaPath: nil, isExpanded: true, toggleBody: "", codeLanguage: "kotlin", tableData: [])
        let childBlocksJson = serializeBlocks([initBlock]).json
        var child = IosNoteRecord(
            rowId: 0,
            syncUuid: SharedIosEditorBridge.shared.doNewUuid(),
            title: title,
            description: childBlocksJson,
            richBlocksJson: metaJson,
            categoryId: parent.categoryId,
            categorySyncUuid: parent.categorySyncUuid,
            isPinned: false,
            isPassword: false,
            isEncrypted: false,
            icon: "📄",
            images: [],
            sounds: [],
            totalTasks: 0,
            completedTasks: 0,
            status: "TODO",
            reminderTime: nil,
            reminderType: nil,
            reminderSound: nil,
            isReminderDone: false,
            date: now,
            updatedAt: now,
            deletedAt: nil,
            syncVersion: 1
        )
        // Store actual blocks in richBlocksJson for block parser + parent pointer in parent's SUB_PAGE block
        child.richBlocksJson = childBlocksJson
        child.rowId = db.upsertNote(child)

        var parentBlocks = parseBlocks(raw: parent.richBlocksJson)
        parentBlocks.append(
            EditableBlock(
                id: SharedIosEditorBridge.shared.doNewUuid(),
                type: .subPage,
                content: title,
                isChecked: false,
                calloutIcon: "📄",
                mediaPath: nil,
                isExpanded: true,
                toggleBody: "",
                codeLanguage: "kotlin",
                tableData: [],
                subPageSyncUuid: child.syncUuid,
                subPageNoteId: child.rowId
            )
        )
        var updatedParent = parent
        let ser = serializeBlocks(parentBlocks)
        updatedParent.richBlocksJson = ser.json
        updatedParent.description = ser.json
        updatedParent.updatedAt = now
        updatedParent.syncVersion += 1
        db.upsertNote(updatedParent)
        reloadAll()
        return child
    }

    func saveNote(_ note: IosNoteRecord, blocks: [EditableBlock]? = nil) {
        var updated = note
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        if let blocks, !updated.isEncrypted {
            let ser = serializeBlocks(blocks)
            updated.richBlocksJson = ser.json
            updated.description = ser.json
            updated.totalTasks = ser.totalTasks
            updated.completedTasks = ser.completedTasks
            updated.images = ser.images
            updated.sounds = ser.sounds

            let allTasksRule = automationRules.first { $0.triggerType == "ALL_TASKS_COMPLETED" && $0.isEnabled }
            if allTasksRule != nil && updated.totalTasks > 0 && updated.completedTasks == updated.totalTasks && updated.status != "DONE" {
                updated.status = "DONE"
            }

            let allText = "\(updated.title) " + blocks.map { "\($0.content) \($0.toggleBody)" }.joined(separator: " ")
            let wikiTitles = SharedIosEditorBridge.shared.extractWikiLinks(text: allText)
            db.syncOutgoingLinks(for: updated, linkedTitles: wikiTitles)
        }
        updated.updatedAt = now
        updated.syncVersion += 1
        updated.rowId = db.upsertNote(updated)
        reloadAll()
    }

    func updateNoteStatus(_ note: IosNoteRecord, status: String) {
        var copy = note
        copy.status = status
        saveNote(copy)
    }

    func togglePin(_ note: IosNoteRecord) {
        var copy = note
        copy.isPinned.toggle()
        saveNote(copy)
    }

    func moveToTrash(_ note: IosNoteRecord) {
        var copy = note
        copy.status = "TRASH"
        saveNote(copy)
    }

    func restoreFromTrash(_ note: IosNoteRecord) {
        var copy = note
        copy.status = "TODO"
        saveNote(copy)
    }

    func permanentlyDelete(_ note: IosNoteRecord) {
        var copy = note
        copy.deletedAt = Int64(Date().timeIntervalSince1970 * 1000)
        saveNote(copy)
    }

    func emptyTrash() {
        for item in trashedNotes {
            permanentlyDelete(item)
        }
    }

    func batchMoveToTrash() {
        for uuid in selectedNoteUuids {
            if let note = activeNotes.first(where: { $0.syncUuid == uuid }) {
                moveToTrash(note)
            }
        }
        selectedNoteUuids.removeAll()
        isMultiSelectMode = false
    }

    func batchToggleLock(passphrase: String) {
        for uuid in selectedNoteUuids {
            if let note = activeNotes.first(where: { $0.syncUuid == uuid }) {
                if !note.isEncrypted {
                    _ = lockNote(note, passphrase: passphrase)
                }
            }
        }
        selectedNoteUuids.removeAll()
        isMultiSelectMode = false
    }

    // MARK: - Encryption & Note Locking

    func lockNote(_ note: IosNoteRecord, passphrase: String) -> Bool {
        guard !passphrase.isEmpty else { return false }
        guard let cipher = SharedIosCryptoFactory.shared.encryptText(
            crypto: vaultCrypto,
            plainText: note.richBlocksJson,
            passphrase: passphrase
        ) else {
            return false
        }
        var copy = note
        copy.richBlocksJson = cipher
        copy.description = cipher
        copy.isPassword = true
        copy.isEncrypted = true
        saveNote(copy)
        return true
    }

    func decryptNote(_ note: IosNoteRecord, passphrase: String) -> String? {
        guard note.isEncrypted else { return note.richBlocksJson }
        return SharedIosCryptoFactory.shared.decryptText(
            crypto: vaultCrypto,
            encryptedText: note.richBlocksJson,
            passphrase: passphrase
        )
    }

    func removeNoteLock(_ note: IosNoteRecord, decryptedBlocksJson: String) {
        var copy = note
        copy.richBlocksJson = decryptedBlocksJson
        copy.description = decryptedBlocksJson
        copy.isPassword = false
        copy.isEncrypted = false
        saveNote(copy)
    }

    // MARK: - Categories

    func createCategory(name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let cat = IosCategoryRecord(
            rowId: 0,
            syncUuid: SharedIosEditorBridge.shared.doNewUuid(),
            name: trimmed,
            isPassword: false,
            updatedAt: now,
            deletedAt: nil,
            syncVersion: 1
        )
        db.upsertCategory(cat)
        reloadAll()
    }

    func toggleCategoryLock(_ category: IosCategoryRecord) {
        var copy = category
        copy.isPassword.toggle()
        copy.updatedAt = Int64(Date().timeIntervalSince1970 * 1000)
        copy.syncVersion += 1
        db.upsertCategory(copy)
        reloadAll()
    }

    func deleteCategory(_ category: IosCategoryRecord) {
        guard !["Not", "Önemli", "Günlük"].contains(category.name) else { return }
        var copy = category
        copy.deletedAt = Int64(Date().timeIntervalSince1970 * 1000)
        copy.updatedAt = copy.deletedAt ?? 0
        copy.syncVersion += 1
        db.upsertCategory(copy)
        if selectedCategoryUuid == category.syncUuid {
            selectedCategoryUuid = nil
        }
        reloadAll()
    }

    // MARK: - Media Storage (Content-Addressed SHA-256 via :shared)

    func storeMediaData(_ data: Data, subFolder: String, extension ext: String) async -> String? {
        do {
            return try await db.mediaResolver.storeMediaBytes(
                bytes: data.toKotlinByteArray(),
                subFolder: subFolder,
                extension: ext
            )
        } catch {
            return nil
        }
    }

    func resolveMediaURL(_ rawPath: String?) -> URL? {
        guard let resolved = db.mediaResolver.resolveToReadablePath(rawPath: rawPath),
              FileManager.default.fileExists(atPath: resolved) else {
            return nil
        }
        return URL(fileURLWithPath: resolved)
    }

    // MARK: - Quick Thought Stream

    func addStreamItem(content: String, type: String = "TEXT", audioPath: String? = nil, imagePath: String? = nil) {
        let clean = content.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty || audioPath != nil || imagePath != nil else { return }
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let item = IosStreamItemRecord(
            rowId: 0,
            syncUuid: SharedIosEditorBridge.shared.doNewUuid(),
            content: clean.isEmpty ? (type == "VOICE" ? "Sesli Fikir" : "Fotoğraf Fikri") : clean,
            type: type,
            audioPath: audioPath,
            imagePath: imagePath,
            timestamp: now,
            isProcessed: false,
            mergedNoteId: nil,
            mergedNoteSyncUuid: nil,
            updatedAt: now,
            deletedAt: nil,
            syncVersion: 1
        )
        db.upsertStreamItem(item)
        reloadAll()
    }

    func convertStreamItemsToNote(_ selectedIds: Set<String>) -> IosNoteRecord? {
        let chosen = streamItems.filter { selectedIds.contains($0.syncUuid) || (selectedIds.isEmpty && !$0.isProcessed) }
        guard !chosen.isEmpty else { return nil }
        var blocks: [EditableBlock] = [
            EditableBlock(id: SharedIosEditorBridge.shared.doNewUuid(), type: .callout, content: isTurkish ? "Fikir Akışından Dönüştürüldü (\(chosen.count) öğe)" : "Converted from Thought Stream (\(chosen.count) items)", isChecked: false, calloutIcon: "⚡", mediaPath: nil, isExpanded: true, toggleBody: "", codeLanguage: "kotlin", tableData: [])
        ]
        for item in chosen {
            blocks.append(EditableBlock(id: SharedIosEditorBridge.shared.doNewUuid(), type: .bulletList, content: item.content, isChecked: false, calloutIcon: "💡", mediaPath: nil, isExpanded: true, toggleBody: "", codeLanguage: "kotlin", tableData: []))
            if let img = item.imagePath {
                blocks.append(EditableBlock(id: SharedIosEditorBridge.shared.doNewUuid(), type: .image, content: "", isChecked: false, calloutIcon: "🖼️", mediaPath: img, isExpanded: true, toggleBody: "", codeLanguage: "kotlin", tableData: []))
            }
            if let aud = item.audioPath {
                blocks.append(EditableBlock(id: SharedIosEditorBridge.shared.doNewUuid(), type: .audio, content: "", isChecked: false, calloutIcon: "🎙️", mediaPath: aud, isExpanded: true, toggleBody: "", codeLanguage: "kotlin", tableData: []))
            }
        }
        let created = createNote(
            title: isTurkish ? "Fikir Derlemesi" : "Thought Digest",
            icon: "⚡",
            blocks: blocks
        )
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        for var item in chosen {
            item.isProcessed = true
            item.mergedNoteId = created.rowId
            item.mergedNoteSyncUuid = created.syncUuid
            item.updatedAt = now
            item.syncVersion += 1
            db.upsertStreamItem(item)
        }
        reloadAll()
        return created
    }

    func updateWhiteboardPosition(noteSyncUuid: String, point: CGPoint) {
        whiteboardPositions[noteSyncUuid] = point
        db.saveWhiteboardPosition(noteSyncUuid: noteSyncUuid, point: point)
    }

    // MARK: - Google Drive E2EE Sync

    func resolvedIosOAuthClientId() -> String {
        if let saved = tokenStorage.getCustomClientId(), !saved.isEmpty {
            return saved
        }
        if let bundled = Bundle.main.object(forInfoDictionaryKey: "PURE_IOS_OAUTH_CLIENT_ID") as? String,
           !bundled.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return bundled.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return ""
    }

    func saveIosOAuthClientId(_ clientId: String) {
        if tokenStorage.saveCustomClientId(clientId) {
            customIosOAuthClientId = clientId.trimmingCharacters(in: .whitespacesAndNewlines)
            showToast(isTurkish ? "iOS OAuth Client ID kaydedildi." : "iOS OAuth Client ID saved.")
        }
    }

    func refreshDriveStatus() async {
        let connected = (try? await SharedIosSyncBridge.shared.hasTokenBundle(tokenStorage: tokenStorage))?.boolValue ?? false
        isDriveConnected = connected
    }

    func connectAndSyncDrive() async {
        let cid = resolvedIosOAuthClientId()
        guard !cid.isEmpty else {
            isSyncError = true
            syncStatusMessage = isTurkish
                ? "Önce Google iOS OAuth Client ID girin."
                : "Enter your Google iOS OAuth Client ID first."
            return
        }
        isSyncing = true
        isSyncError = false
        syncStatusMessage = nil
        let (ok, err) = await oauthManager.authorize(
            clientId: cid,
            tokenStorage: tokenStorage,
            clock: appClock
        )
        if !ok {
            isSyncing = false
            isSyncError = true
            syncStatusMessage = err
            await refreshDriveStatus()
            return
        }
        isDriveConnected = true
        await triggerDriveSync(customPassphrase: nil)
    }

    func triggerDriveSync(customPassphrase: String? = nil) async {
        let cid = resolvedIosOAuthClientId()
        guard !cid.isEmpty,
              let redirect = IosOAuthPkceManager.computeGoogleRedirectUri(clientId: cid) else {
            isSyncError = true
            syncStatusMessage = isTurkish ? "iOS OAuth Client ID eksik." : "Missing iOS OAuth Client ID."
            return
        }
        isSyncing = true
        isSyncError = false
        do {
            let res = try await SharedIosSyncBridge.shared.runGoogleDriveSync(
                localStore: localSyncStore,
                tokenStorage: tokenStorage,
                vaultCrypto: vaultCrypto,
                clientId: cid,
                redirectUri: redirect.redirectUri,
                clock: appClock,
                customPassphrase: customPassphrase
            )
            if res.success {
                reloadAll()
                isSyncError = false
                syncStatusMessage = isTurkish
                    ? "Eşitlendi ↑\(res.pushedEntities) kayıt, ↓\(res.pulledNotes) not, 📎\(res.mediaDownloaded + res.mediaUploaded) medya"
                    : "Synced ↑\(res.pushedEntities) pushed, ↓\(res.pulledNotes) notes"
                showToast(syncStatusMessage ?? "Sync OK")
            } else {
                isSyncError = true
                syncStatusMessage = res.errorMessage
            }
        } catch {
            isSyncError = true
            syncStatusMessage = error.localizedDescription
        }
        await refreshDriveStatus()
        isSyncing = false
    }

    func disconnectDrive() async {
        _ = try? await SharedIosSyncBridge.shared.clearAllTokens(tokenStorage: tokenStorage)
        isDriveConnected = false
        isSyncError = false
        syncStatusMessage = isTurkish ? "Google Drive oturumu kapatıldı." : "Google Drive disconnected."
    }
}

private final class AppClockAdapter: NSObject, AppClock {
    func nowMillis() -> Int64 {
        Int64(Date().timeIntervalSince1970 * 1000)
    }
}
