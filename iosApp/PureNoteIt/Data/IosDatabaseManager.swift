import Foundation
import PureNoteShared
import SQLite3

private let sqliteTransient = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

struct IosCategoryRecord: Identifiable, Equatable, Hashable {
    var rowId: Int
    let syncUuid: String
    var name: String
    var isPassword: Bool
    var updatedAt: Int64
    var deletedAt: Int64?
    var syncVersion: Int64

    var id: String { syncUuid }
}

struct IosNoteRecord: Identifiable, Equatable, Hashable {
    var rowId: Int
    let syncUuid: String
    var title: String
    var description: String
    var richBlocksJson: String
    var categoryId: Int?
    var categorySyncUuid: String?
    var isPinned: Bool
    var isPassword: Bool
    var isEncrypted: Bool
    var icon: String
    var images: [String]
    var sounds: [String]
    var totalTasks: Int
    var completedTasks: Int
    var status: String // TODO, IN_PROGRESS, IN_REVIEW, DONE, TRASH
    var reminderTime: Int64?
    var reminderType: String?
    var reminderSound: String?
    var isReminderDone: Bool
    var date: Int64
    var updatedAt: Int64
    var deletedAt: Int64?
    var syncVersion: Int64

    var id: String { syncUuid }
}

struct IosNoteLinkRecord: Identifiable, Equatable {
    var rowId: Int
    let syncUuid: String
    var sourceNoteId: Int
    var targetNoteId: Int
    var sourceSyncUuid: String
    var targetSyncUuid: String
    var linkText: String
    var createdAt: Int64
    var updatedAt: Int64
    var deletedAt: Int64?
    var syncVersion: Int64

    var id: String { syncUuid }
}

struct IosStreamItemRecord: Identifiable, Equatable {
    var rowId: Int
    let syncUuid: String
    var content: String
    var type: String // TEXT, VOICE, PHOTO
    var audioPath: String?
    var imagePath: String?
    var timestamp: Int64
    var isProcessed: Bool
    var mergedNoteId: Int?
    var mergedNoteSyncUuid: String?
    var updatedAt: Int64
    var deletedAt: Int64?
    var syncVersion: Int64

    var id: String { syncUuid }
}

struct IosAutomationRuleRecord: Identifiable, Equatable {
    var rowId: Int
    let syncUuid: String
    var title: String
    var triggerType: String
    var triggerConfig: String?
    var actionType: String
    var actionConfig: String?
    var isEnabled: Bool
    var createdAt: Int64
    var updatedAt: Int64
    var deletedAt: Int64?
    var syncVersion: Int64

    var id: String { syncUuid }
}

/// Thread-safe local SQLite3 database manager maintaining schema & sync parity with Android Room v6 & Desktop.
final class IosDatabaseManager {
    let rootDir: URL
    let mediaResolver: MediaPathResolver
    let syncCursorURL: URL

    private var db: OpaquePointer?
    private let queue = DispatchQueue(label: "com.purenoteit.ios.sqlite")

    init() {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
        self.rootDir = docs.appendingPathComponent("PureNoteIt", isDirectory: true)
        let filesDir = rootDir.appendingPathComponent("files", isDirectory: true)
        let cacheDir = caches.appendingPathComponent("PureNoteItCache", isDirectory: true)
        try? FileManager.default.createDirectory(at: rootDir, withIntermediateDirectories: true)
        try? FileManager.default.createDirectory(at: filesDir, withIntermediateDirectories: true)
        try? FileManager.default.createDirectory(at: cacheDir, withIntermediateDirectories: true)

        self.syncCursorURL = rootDir.appendingPathComponent("sync_cursor.json")
        self.mediaResolver = SharedIosStorageFactory.shared.createMediaPathResolver(
            rootFilesPath: filesDir.path,
            cachePath: cacheDir.path
        )

        let dbURL = rootDir.appendingPathComponent("purenoteit_ios_v2.db")
        queue.sync {
            sqlite3_open(dbURL.path, &db)
            createTablesLocked()
            seedDefaultDataIfEmptyLocked()
        }
    }

    deinit {
        queue.sync {
            if let db {
                sqlite3_close(db)
            }
        }
    }

    func fetchActiveCategories() -> [IosCategoryRecord] {
        queue.sync {
            queryCategoriesLocked(whereClause: "WHERE deletedAt IS NULL", args: [])
        }
    }

    func fetchAllCategories(sinceMillis: Int64 = Int64.min) -> [IosCategoryRecord] {
        queue.sync {
            if sinceMillis == Int64.min {
                return queryCategoriesLocked(whereClause: "", args: [])
            }
            return queryCategoriesLocked(whereClause: "WHERE updatedAt >= ?", args: [sinceMillis])
        }
    }

    func fetchNotes(includeDeleted: Bool = false, sinceMillis: Int64 = Int64.min) -> [IosNoteRecord] {
        queue.sync {
            if sinceMillis != Int64.min {
                return queryNotesLocked(whereClause: "WHERE updatedAt >= ?", args: [sinceMillis])
            }
            if includeDeleted {
                return queryNotesLocked(whereClause: "", args: [])
            }
            return queryNotesLocked(whereClause: "WHERE deletedAt IS NULL", args: [])
        }
    }

    func fetchLinks(includeDeleted: Bool = false, sinceMillis: Int64 = Int64.min) -> [IosNoteLinkRecord] {
        queue.sync {
            if sinceMillis != Int64.min {
                return queryLinksLocked(whereClause: "WHERE updatedAt >= ?", args: [sinceMillis])
            }
            if includeDeleted {
                return queryLinksLocked(whereClause: "", args: [])
            }
            return queryLinksLocked(whereClause: "WHERE deletedAt IS NULL", args: [])
        }
    }

    func fetchStreamItems(includeDeleted: Bool = false, sinceMillis: Int64 = Int64.min) -> [IosStreamItemRecord] {
        queue.sync {
            if sinceMillis != Int64.min {
                return queryStreamLocked(whereClause: "WHERE updatedAt >= ?", args: [sinceMillis])
            }
            if includeDeleted {
                return queryStreamLocked(whereClause: "", args: [])
            }
            return queryStreamLocked(whereClause: "WHERE deletedAt IS NULL", args: [])
        }
    }

    func fetchAutomationRules(sinceMillis: Int64 = Int64.min) -> [IosAutomationRuleRecord] {
        queue.sync {
            if sinceMillis != Int64.min {
                return queryRulesLocked(whereClause: "WHERE updatedAt >= ?", args: [sinceMillis])
            }
            return queryRulesLocked(whereClause: "WHERE deletedAt IS NULL", args: [])
        }
    }

    @discardableResult
    func upsertCategory(_ category: IosCategoryRecord) -> Int {
        queue.sync { upsertCategoryLocked(category) }
    }

    @discardableResult
    func upsertNote(_ note: IosNoteRecord) -> Int {
        queue.sync { upsertNoteLocked(note) }
    }

    func upsertNoteLink(_ link: IosNoteLinkRecord) {
        queue.sync { upsertNoteLinkLocked(link) }
    }

    func syncOutgoingLinks(for sourceNote: IosNoteRecord, linkedTitles: [String]) {
        queue.sync {
            let now = Int64(Date().timeIntervalSince1970 * 1000)
            let activeNotes = queryNotesLocked(whereClause: "WHERE deletedAt IS NULL", args: [])
            let existingLinks = queryLinksLocked(whereClause: "WHERE sourceSyncUuid = ?", args: [sourceNote.syncUuid])

            var targetByTitle: [String: IosNoteRecord] = [:]
            for n in activeNotes where n.syncUuid != sourceNote.syncUuid {
                let key = n.title.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
                if !key.isEmpty, targetByTitle[key] == nil {
                    targetByTitle[key] = n
                }
            }

            var desiredTargetUuids = Set<String>()
            for rawTitle in linkedTitles {
                let key = rawTitle.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
                if let target = targetByTitle[key] {
                    desiredTargetUuids.insert(target.syncUuid)
                    if let existing = existingLinks.first(where: { $0.targetSyncUuid == target.syncUuid }) {
                        if existing.deletedAt != nil || existing.linkText != rawTitle {
                            var updated = existing
                            updated.deletedAt = nil
                            updated.linkText = rawTitle
                            updated.updatedAt = now
                            updated.syncVersion += 1
                            upsertNoteLinkLocked(updated)
                        }
                    } else {
                        let newLink = IosNoteLinkRecord(
                            rowId: 0,
                            syncUuid: SharedIosEditorBridge.shared.doNewUuid(),
                            sourceNoteId: sourceNote.rowId,
                            targetNoteId: target.rowId,
                            sourceSyncUuid: sourceNote.syncUuid,
                            targetSyncUuid: target.syncUuid,
                            linkText: rawTitle,
                            createdAt: now,
                            updatedAt: now,
                            deletedAt: nil,
                            syncVersion: 1
                        )
                        upsertNoteLinkLocked(newLink)
                    }
                }
            }

            for existing in existingLinks where existing.deletedAt == nil && !desiredTargetUuids.contains(existing.targetSyncUuid) {
                var tomb = existing
                tomb.deletedAt = now
                tomb.updatedAt = now
                tomb.syncVersion += 1
                upsertNoteLinkLocked(tomb)
            }
        }
    }

    func upsertStreamItem(_ item: IosStreamItemRecord) {
        queue.sync { upsertStreamItemLocked(item) }
    }

    func upsertAutomationRule(_ rule: IosAutomationRuleRecord) {
        queue.sync { upsertAutomationRuleLocked(rule) }
    }

    func loadWhiteboardPositions() -> [String: CGPoint] {
        queue.sync {
            guard let db else { return [:] }
            var out: [String: CGPoint] = [:]
            var stmt: OpaquePointer?
            if sqlite3_prepare_v2(db, "SELECT noteSyncUuid, x, y FROM whiteboard_positions;", -1, &stmt, nil) == SQLITE_OK {
                while sqlite3_step(stmt) == SQLITE_ROW {
                    let uuid = String(cString: sqlite3_column_text(stmt, 0))
                    let x = sqlite3_column_double(stmt, 1)
                    let y = sqlite3_column_double(stmt, 2)
                    out[uuid] = CGPoint(x: x, y: y)
                }
            }
            sqlite3_finalize(stmt)
            return out
        }
    }

    func saveWhiteboardPosition(noteSyncUuid: String, point: CGPoint) {
        queue.sync {
            guard let db else { return }
            let sql = """
            INSERT INTO whiteboard_positions (noteSyncUuid, x, y) VALUES (?, ?, ?)
            ON CONFLICT(noteSyncUuid) DO UPDATE SET x = excluded.x, y = excluded.y;
            """
            var stmt: OpaquePointer?
            if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
                sqlite3_bind_text(stmt, 1, noteSyncUuid, -1, sqliteTransient)
                sqlite3_bind_double(stmt, 2, Double(point.x))
                sqlite3_bind_double(stmt, 3, Double(point.y))
                sqlite3_step(stmt)
            }
            sqlite3_finalize(stmt)
        }
    }

    func purgeExpiredTrash(retentionDays: Int) {
        guard retentionDays > 0 else { return }
        queue.sync {
            guard let db else { return }
            let cutoff = Int64(Date().timeIntervalSince1970 * 1000) - Int64(retentionDays) * 86_400_000
            var stmt: OpaquePointer?
            if sqlite3_prepare_v2(db, "UPDATE notes SET deletedAt = ?, updatedAt = ? WHERE status = 'TRASH' AND updatedAt < ? AND deletedAt IS NULL;", -1, &stmt, nil) == SQLITE_OK {
                let now = Int64(Date().timeIntervalSince1970 * 1000)
                sqlite3_bind_int64(stmt, 1, now)
                sqlite3_bind_int64(stmt, 2, now)
                sqlite3_bind_int64(stmt, 3, cutoff)
                sqlite3_step(stmt)
            }
            sqlite3_finalize(stmt)
        }
    }

    func applyRemoteSyncTransaction(
        categories: [IosCategoryRecord],
        notes: [IosNoteRecord],
        links: [IosNoteLinkRecord],
        streamItems: [IosStreamItemRecord],
        rules: [IosAutomationRuleRecord]
    ) {
        queue.sync {
            guard let db else { return }
            sqlite3_exec(db, "BEGIN IMMEDIATE TRANSACTION;", nil, nil, nil)
            var catIdMap: [String: Int] = [:]
            for c in queryCategoriesLocked(whereClause: "", args: []) {
                catIdMap[c.syncUuid] = c.rowId
            }
            for cat in categories {
                let id = upsertCategoryLocked(cat)
                if id > 0 { catIdMap[cat.syncUuid] = id }
            }

            let fallbackCatId = queryCategoriesLocked(whereClause: "WHERE deletedAt IS NULL ORDER BY id ASC LIMIT 1", args: []).first?.rowId

            var noteIdMap: [String: Int] = [:]
            for n in queryNotesLocked(whereClause: "", args: []) {
                noteIdMap[n.syncUuid] = n.rowId
            }
            for var note in notes {
                if let catUuid = note.categorySyncUuid, let mapped = catIdMap[catUuid] {
                    note.categoryId = mapped
                } else if note.categoryId == nil {
                    note.categoryId = fallbackCatId
                }
                let id = upsertNoteLocked(note)
                if id > 0 { noteIdMap[note.syncUuid] = id }
            }

            for var link in links {
                if let sId = noteIdMap[link.sourceSyncUuid] { link.sourceNoteId = sId }
                if let tId = noteIdMap[link.targetSyncUuid] { link.targetNoteId = tId }
                upsertNoteLinkLocked(link)
            }

            for var item in streamItems {
                if let mUuid = item.mergedNoteSyncUuid, let mId = noteIdMap[mUuid] {
                    item.mergedNoteId = mId
                }
                upsertStreamItemLocked(item)
            }

            for rule in rules {
                upsertAutomationRuleLocked(rule)
            }
            sqlite3_exec(db, "COMMIT;", nil, nil, nil)
        }
    }

    // MARK: - Private SQLite Helpers

    private func createTablesLocked() {
        guard let db else { return }
        let ddl = """
        CREATE TABLE IF NOT EXISTS categories (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            isPassword INTEGER,
            syncUuid TEXT NOT NULL UNIQUE,
            updatedAt INTEGER NOT NULL DEFAULT 0,
            deletedAt INTEGER,
            syncVersion INTEGER NOT NULL DEFAULT 1
        );
        CREATE TABLE IF NOT EXISTS notes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT,
            description TEXT,
            date INTEGER NOT NULL,
            isPassword INTEGER,
            images TEXT,
            categoryId INTEGER,
            isSound TEXT,
            isPinned INTEGER NOT NULL DEFAULT 0,
            icon TEXT,
            isEncrypted INTEGER NOT NULL DEFAULT 0,
            totalTasks INTEGER NOT NULL DEFAULT 0,
            completedTasks INTEGER NOT NULL DEFAULT 0,
            status TEXT NOT NULL DEFAULT 'TODO',
            reminderTime INTEGER,
            reminderType TEXT,
            reminderSound TEXT,
            isReminderDone INTEGER NOT NULL DEFAULT 0,
            richBlocksJson TEXT,
            syncUuid TEXT NOT NULL UNIQUE,
            categorySyncUuid TEXT,
            updatedAt INTEGER NOT NULL DEFAULT 0,
            deletedAt INTEGER,
            syncVersion INTEGER NOT NULL DEFAULT 1
        );
        CREATE TABLE IF NOT EXISTS note_links (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            sourceNoteId INTEGER NOT NULL DEFAULT 0,
            targetNoteId INTEGER NOT NULL DEFAULT 0,
            linkText TEXT NOT NULL,
            createdAt INTEGER NOT NULL DEFAULT 0,
            syncUuid TEXT NOT NULL UNIQUE,
            sourceSyncUuid TEXT NOT NULL DEFAULT '',
            targetSyncUuid TEXT NOT NULL DEFAULT '',
            updatedAt INTEGER NOT NULL DEFAULT 0,
            deletedAt INTEGER,
            syncVersion INTEGER NOT NULL DEFAULT 1
        );
        CREATE TABLE IF NOT EXISTS stream_items (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            content TEXT NOT NULL,
            type TEXT NOT NULL DEFAULT 'TEXT',
            audioPath TEXT,
            imagePath TEXT,
            timestamp INTEGER NOT NULL DEFAULT 0,
            isProcessed INTEGER NOT NULL DEFAULT 0,
            mergedNoteId INTEGER,
            syncUuid TEXT NOT NULL UNIQUE,
            mergedNoteSyncUuid TEXT,
            updatedAt INTEGER NOT NULL DEFAULT 0,
            deletedAt INTEGER,
            syncVersion INTEGER NOT NULL DEFAULT 1
        );
        CREATE TABLE IF NOT EXISTS automation_rules (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            triggerType TEXT NOT NULL,
            triggerConfig TEXT,
            actionType TEXT NOT NULL,
            actionConfig TEXT,
            isEnabled INTEGER NOT NULL DEFAULT 1,
            createdAt INTEGER NOT NULL DEFAULT 0,
            syncUuid TEXT NOT NULL UNIQUE,
            updatedAt INTEGER NOT NULL DEFAULT 0,
            deletedAt INTEGER,
            syncVersion INTEGER NOT NULL DEFAULT 1
        );
        CREATE TABLE IF NOT EXISTS whiteboard_positions (
            noteSyncUuid TEXT PRIMARY KEY,
            x REAL NOT NULL,
            y REAL NOT NULL
        );
        """
        sqlite3_exec(db, ddl, nil, nil, nil)
    }

    private func seedDefaultDataIfEmptyLocked() {
        let existingCats = queryCategoriesLocked(whereClause: "", args: [])
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        var allCats: [IosCategoryRecord] = []
        if existingCats.isEmpty {
            let defaults = ["Not", "Önemli", "Günlük"]
            for name in defaults {
                let cat = IosCategoryRecord(
                    rowId: 0,
                    syncUuid: SharedIosEditorBridge.shared.doNewUuid(),
                    name: name,
                    isPassword: false,
                    updatedAt: now,
                    deletedAt: nil,
                    syncVersion: 1
                )
                let newId = upsertCategoryLocked(cat)
                var copy = cat
                copy.rowId = newId
                allCats.append(copy)
            }
            let rule = IosAutomationRuleRecord(
                rowId: 0,
                syncUuid: SharedIosEditorBridge.shared.doNewUuid(),
                title: "Tüm Görevler Tamamlanınca Tamamlandı Yap",
                triggerType: "ALL_TASKS_COMPLETED",
                triggerConfig: nil,
                actionType: "SET_STATUS_DONE",
                actionConfig: "DONE",
                isEnabled: true,
                createdAt: now,
                updatedAt: now,
                deletedAt: nil,
                syncVersion: 1
            )
            upsertAutomationRuleLocked(rule)
        } else {
            allCats = existingCats
        }
        let catNot = allCats.first { $0.name == "Not" } ?? allCats.first
        let catImp = allCats.first { $0.name == "Önemli" } ?? allCats.first
        let catDay = allCats.first { $0.name == "Günlük" } ?? allCats.first

        let existingNotes = queryNotesLocked(whereClause: "", args: [])
        guard existingNotes.isEmpty else { return }

        let bridge = SharedIosEditorBridge.shared
        let welcomeBlocks = [
            bridge.createBlock(type: .callout, content: "PureNoteIt iOS artık Android ile tam özellik eşleniğinde: Blok Editör, Ses Kaydı, Çizim, OCR, 2D Grafik, Sonsuz Tuval, Kanban, Takvim ve Google Drive E2EE Senkronizasyon!", isChecked: false, calloutIcon: "🚀", mediaPath: nil, isExpanded: true, tableData: [], toggleBody: "", codeLanguage: "kotlin", subPageNoteId: nil, subPageSyncUuid: nil),
            bridge.createBlock(type: .heading1, content: "Özellikler & Kontrol Listesi", isChecked: false, calloutIcon: "💡", mediaPath: nil, isExpanded: true, tableData: [], toggleBody: "", codeLanguage: "kotlin", subPageNoteId: nil, subPageSyncUuid: nil),
            bridge.createBlock(type: .todo, content: "Şablonlar Galerisi (14 hazır şablon) & Günün Ajandası", isChecked: true, calloutIcon: "💡", mediaPath: nil, isExpanded: true, tableData: [], toggleBody: "", codeLanguage: "kotlin", subPageNoteId: nil, subPageSyncUuid: nil),
            bridge.createBlock(type: .todo, content: "Çizim Tuvali, Ses Kaydı, Fotoğraf & OCR Tara", isChecked: false, calloutIcon: "💡", mediaPath: nil, isExpanded: true, tableData: [], toggleBody: "", codeLanguage: "kotlin", subPageNoteId: nil, subPageSyncUuid: nil),
            bridge.createBlock(type: .code, content: "fun pureNoteSync(platform: String): String {\n    return \"Encrypted Drive E2EE on $platform\"\n}", isChecked: false, calloutIcon: "⚡", mediaPath: nil, isExpanded: true, tableData: [], toggleBody: "", codeLanguage: "kotlin", subPageNoteId: nil, subPageSyncUuid: nil),
            bridge.createBlock(type: .table, content: "Tablo", isChecked: false, calloutIcon: "💡", mediaPath: nil, isExpanded: true, tableData: [["Kalem", "Tutar (₺)", "Durum"], ["PureNoteIt Sync", "0", "Aktif"], ["Kasa AES-256", "256", "Koruma"]], toggleBody: "", codeLanguage: "kotlin", subPageNoteId: nil, subPageSyncUuid: nil),
            bridge.createBlock(type: .math, content: "\\int_{0}^{1} x^2 \\, dx = \\frac{1}{3}", isChecked: false, calloutIcon: "♾️", mediaPath: nil, isExpanded: true, tableData: [], toggleBody: "", codeLanguage: "kotlin", subPageNoteId: nil, subPageSyncUuid: nil),
            bridge.createBlock(type: .toggle, content: "Çift Yönlü Bağlantı Örneği", isChecked: false, calloutIcon: "💡", mediaPath: nil, isExpanded: false, tableData: [], toggleBody: "Başka bir nota [[KMP Mimari Özeti]] şeklinde bağlantı vererek 2D Bilgi Grafiği ve Sonsuz Tuval'de otomatik oklar oluşturabilirsiniz.", codeLanguage: "kotlin", subPageNoteId: nil, subPageSyncUuid: nil)
        ]
        let serializedWelcome = BlockParser.shared.blocksToJson(blocks: welcomeBlocks)
        let welcomeTasks = BlockParser.shared.calculateTasks(blocks: welcomeBlocks)
        let welcomeNote = IosNoteRecord(
            rowId: 0,
            syncUuid: bridge.doNewUuid(),
            title: "PureNoteIt iOS Başlangıç Rehberi #rehber",
            description: serializedWelcome,
            richBlocksJson: serializedWelcome,
            categoryId: catNot?.rowId,
            categorySyncUuid: catNot?.syncUuid,
            isPinned: true,
            isPassword: false,
            isEncrypted: false,
            icon: "🚀",
            images: [],
            sounds: [],
            totalTasks: welcomeTasks.first?.intValue ?? 2,
            completedTasks: welcomeTasks.second?.intValue ?? 1,
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
        let welcomeId = upsertNoteLocked(welcomeNote)

        let archBlocks = [
            bridge.createBlock(type: .heading2, content: "Cihazlar Arası E2EE Mimarisi", isChecked: false, calloutIcon: "🏛️", mediaPath: nil, isExpanded: true, tableData: [], toggleBody: "", codeLanguage: "kotlin", subPageNoteId: nil, subPageSyncUuid: nil),
            bridge.createBlock(type: .bulletList, content: "Windows, Android, macOS ve iOS aynı `NOTION_BLOCKS_V1` ve `ENC_P3_PWD` formatını paylaşır.", isChecked: false, calloutIcon: "💡", mediaPath: nil, isExpanded: true, tableData: [], toggleBody: "", codeLanguage: "kotlin", subPageNoteId: nil, subPageSyncUuid: nil),
            bridge.createBlock(type: .paragraph, content: "Bağlı sayfa: [[PureNoteIt iOS Başlangıç Rehberi #rehber]]", isChecked: false, calloutIcon: "💡", mediaPath: nil, isExpanded: true, tableData: [], toggleBody: "", codeLanguage: "kotlin", subPageNoteId: nil, subPageSyncUuid: nil)
        ]
        let archSerialized = BlockParser.shared.blocksToJson(blocks: archBlocks)
        let archNote = IosNoteRecord(
            rowId: 0,
            syncUuid: bridge.doNewUuid(),
            title: "KMP Mimari Özeti #kmp",
            description: archSerialized,
            richBlocksJson: archSerialized,
            categoryId: catNot?.rowId,
            categorySyncUuid: catNot?.syncUuid,
            isPinned: false,
            isPassword: false,
            isEncrypted: false,
            icon: "⚡",
            images: [],
            sounds: [],
            totalTasks: 0,
            completedTasks: 0,
            status: "IN_PROGRESS",
            reminderTime: nil,
            reminderType: nil,
            reminderSound: nil,
            isReminderDone: false,
            date: now - 60_000,
            updatedAt: now - 60_000,
            deletedAt: nil,
            syncVersion: 1
        )
        let archId = upsertNoteLocked(archNote)

        let secBlocks = [
            bridge.createBlock(type: .heading2, content: "Bitwarden Seviyesi Yerel Kasa", isChecked: false, calloutIcon: "🔐", mediaPath: nil, isExpanded: true, tableData: [], toggleBody: "", codeLanguage: "kotlin", subPageNoteId: nil, subPageSyncUuid: nil),
            bridge.createBlock(type: .todo, content: "6 haneli PBKDF2-HMAC-SHA256 Master PIN", isChecked: true, calloutIcon: "💡", mediaPath: nil, isExpanded: true, tableData: [], toggleBody: "", codeLanguage: "kotlin", subPageNoteId: nil, subPageSyncUuid: nil),
            bridge.createBlock(type: .todo, content: "Face ID / Touch ID biyometrik doğrulama", isChecked: true, calloutIcon: "💡", mediaPath: nil, isExpanded: true, tableData: [], toggleBody: "", codeLanguage: "kotlin", subPageNoteId: nil, subPageSyncUuid: nil),
            bridge.createBlock(type: .todo, content: "Şifreli .purevault.json yedeği test et", isChecked: false, calloutIcon: "💡", mediaPath: nil, isExpanded: true, tableData: [], toggleBody: "", codeLanguage: "kotlin", subPageNoteId: nil, subPageSyncUuid: nil)
        ]
        let secSerialized = BlockParser.shared.blocksToJson(blocks: secBlocks)
        let secNote = IosNoteRecord(
            rowId: 0,
            syncUuid: bridge.doNewUuid(),
            title: "AES-256 Kasa & Güvenlik #kasa",
            description: secSerialized,
            richBlocksJson: secSerialized,
            categoryId: catImp?.rowId,
            categorySyncUuid: catImp?.syncUuid,
            isPinned: false,
            isPassword: false,
            isEncrypted: false,
            icon: "🔐",
            images: [],
            sounds: [],
            totalTasks: 3,
            completedTasks: 2,
            status: "IN_REVIEW",
            reminderTime: nil,
            reminderType: nil,
            reminderSound: nil,
            isReminderDone: false,
            date: now - 120_000,
            updatedAt: now - 120_000,
            deletedAt: nil,
            syncVersion: 1
        )
        let secId = upsertNoteLocked(secNote)

        let dayBlocks = [
            bridge.createBlock(type: .callout, content: "Günün Mottosu: Küçük adımlar, büyük zaferler doğurur.", isChecked: false, calloutIcon: "☀️", mediaPath: nil, isExpanded: true, tableData: [], toggleBody: "", codeLanguage: "kotlin", subPageNoteId: nil, subPageSyncUuid: nil),
            bridge.createBlock(type: .todo, content: "Sabah hedeflerini ve öncelikleri gözden geçir", isChecked: true, calloutIcon: "💡", mediaPath: nil, isExpanded: true, tableData: [], toggleBody: "", codeLanguage: "kotlin", subPageNoteId: nil, subPageSyncUuid: nil),
            bridge.createBlock(type: .todo, content: "Google Drive E2EE eşitlemesini doğrula", isChecked: true, calloutIcon: "💡", mediaPath: nil, isExpanded: true, tableData: [], toggleBody: "", codeLanguage: "kotlin", subPageNoteId: nil, subPageSyncUuid: nil)
        ]
        let daySerialized = BlockParser.shared.blocksToJson(blocks: dayBlocks)
        let dayNote = IosNoteRecord(
            rowId: 0,
            syncUuid: bridge.doNewUuid(),
            title: "Günün Ajandası & Plan #günlük",
            description: daySerialized,
            richBlocksJson: daySerialized,
            categoryId: catDay?.rowId,
            categorySyncUuid: catDay?.syncUuid,
            isPinned: true,
            isPassword: false,
            isEncrypted: false,
            icon: "📅",
            images: [],
            sounds: [],
            totalTasks: 2,
            completedTasks: 2,
            status: "DONE",
            reminderTime: nil,
            reminderType: nil,
            reminderSound: nil,
            isReminderDone: false,
            date: now - 180_000,
            updatedAt: now - 180_000,
            deletedAt: nil,
            syncVersion: 1
        )
        let dayId = upsertNoteLocked(dayNote)

        if welcomeId > 0 && archId > 0 {
            upsertNoteLinkLocked(IosNoteLinkRecord(rowId: 0, syncUuid: bridge.doNewUuid(), sourceNoteId: archId, targetNoteId: welcomeId, sourceSyncUuid: archNote.syncUuid, targetSyncUuid: welcomeNote.syncUuid, linkText: welcomeNote.title, createdAt: now, updatedAt: now, deletedAt: nil, syncVersion: 1))
        }
        if secId > 0 && archId > 0 {
            upsertNoteLinkLocked(IosNoteLinkRecord(rowId: 0, syncUuid: bridge.doNewUuid(), sourceNoteId: secId, targetNoteId: archId, sourceSyncUuid: secNote.syncUuid, targetSyncUuid: archNote.syncUuid, linkText: archNote.title, createdAt: now, updatedAt: now, deletedAt: nil, syncVersion: 1))
        }
        if dayId > 0 && welcomeId > 0 {
            upsertNoteLinkLocked(IosNoteLinkRecord(rowId: 0, syncUuid: bridge.doNewUuid(), sourceNoteId: dayId, targetNoteId: welcomeId, sourceSyncUuid: dayNote.syncUuid, targetSyncUuid: welcomeNote.syncUuid, linkText: welcomeNote.title, createdAt: now, updatedAt: now, deletedAt: nil, syncVersion: 1))
        }
    }

    private func upsertCategoryLocked(_ cat: IosCategoryRecord) -> Int {
        guard let db else { return 0 }
        let sql = """
        INSERT INTO categories (name, isPassword, syncUuid, updatedAt, deletedAt, syncVersion)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT(syncUuid) DO UPDATE SET
            name = excluded.name,
            isPassword = excluded.isPassword,
            updatedAt = excluded.updatedAt,
            deletedAt = excluded.deletedAt,
            syncVersion = excluded.syncVersion;
        """
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            sqlite3_bind_text(stmt, 1, cat.name, -1, sqliteTransient)
            sqlite3_bind_int(stmt, 2, cat.isPassword ? 1 : 0)
            sqlite3_bind_text(stmt, 3, cat.syncUuid, -1, sqliteTransient)
            sqlite3_bind_int64(stmt, 4, cat.updatedAt)
            if let del = cat.deletedAt { sqlite3_bind_int64(stmt, 5, del) } else { sqlite3_bind_null(stmt, 5) }
            sqlite3_bind_int64(stmt, 6, cat.syncVersion)
            sqlite3_step(stmt)
        }
        sqlite3_finalize(stmt)
        return fetchRowIdLocked(table: "categories", syncUuid: cat.syncUuid)
    }

    private func upsertNoteLocked(_ note: IosNoteRecord) -> Int {
        guard let db else { return 0 }
        let sql = """
        INSERT INTO notes (
            title, description, date, isPassword, images, categoryId, isSound, isPinned, icon,
            isEncrypted, totalTasks, completedTasks, status, reminderTime, reminderType, reminderSound,
            isReminderDone, richBlocksJson, syncUuid, categorySyncUuid, updatedAt, deletedAt, syncVersion
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(syncUuid) DO UPDATE SET
            title = excluded.title,
            description = excluded.description,
            date = excluded.date,
            isPassword = excluded.isPassword,
            images = excluded.images,
            categoryId = excluded.categoryId,
            isSound = excluded.isSound,
            isPinned = excluded.isPinned,
            icon = excluded.icon,
            isEncrypted = excluded.isEncrypted,
            totalTasks = excluded.totalTasks,
            completedTasks = excluded.completedTasks,
            status = excluded.status,
            reminderTime = excluded.reminderTime,
            reminderType = excluded.reminderType,
            reminderSound = excluded.reminderSound,
            isReminderDone = excluded.isReminderDone,
            richBlocksJson = excluded.richBlocksJson,
            categorySyncUuid = excluded.categorySyncUuid,
            updatedAt = excluded.updatedAt,
            deletedAt = excluded.deletedAt,
            syncVersion = excluded.syncVersion;
        """
        let imagesJson = encodeStringArray(note.images)
        let soundsJson = encodeStringArray(note.sounds)

        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            sqlite3_bind_text(stmt, 1, note.title, -1, sqliteTransient)
            sqlite3_bind_text(stmt, 2, note.description, -1, sqliteTransient)
            sqlite3_bind_int64(stmt, 3, note.date)
            sqlite3_bind_int(stmt, 4, note.isPassword ? 1 : 0)
            if let imagesJson { sqlite3_bind_text(stmt, 5, imagesJson, -1, sqliteTransient) } else { sqlite3_bind_null(stmt, 5) }
            if let cid = note.categoryId { sqlite3_bind_int(stmt, 6, Int32(cid)) } else { sqlite3_bind_null(stmt, 6) }
            if let soundsJson { sqlite3_bind_text(stmt, 7, soundsJson, -1, sqliteTransient) } else { sqlite3_bind_null(stmt, 7) }
            sqlite3_bind_int(stmt, 8, note.isPinned ? 1 : 0)
            sqlite3_bind_text(stmt, 9, note.icon, -1, sqliteTransient)
            sqlite3_bind_int(stmt, 10, note.isEncrypted ? 1 : 0)
            sqlite3_bind_int(stmt, 11, Int32(note.totalTasks))
            sqlite3_bind_int(stmt, 12, Int32(note.completedTasks))
            sqlite3_bind_text(stmt, 13, note.status, -1, sqliteTransient)
            if let rt = note.reminderTime { sqlite3_bind_int64(stmt, 14, rt) } else { sqlite3_bind_null(stmt, 14) }
            if let rtype = note.reminderType { sqlite3_bind_text(stmt, 15, rtype, -1, sqliteTransient) } else { sqlite3_bind_null(stmt, 15) }
            if let rsnd = note.reminderSound { sqlite3_bind_text(stmt, 16, rsnd, -1, sqliteTransient) } else { sqlite3_bind_null(stmt, 16) }
            sqlite3_bind_int(stmt, 17, note.isReminderDone ? 1 : 0)
            sqlite3_bind_text(stmt, 18, note.richBlocksJson, -1, sqliteTransient)
            sqlite3_bind_text(stmt, 19, note.syncUuid, -1, sqliteTransient)
            if let cUuid = note.categorySyncUuid { sqlite3_bind_text(stmt, 20, cUuid, -1, sqliteTransient) } else { sqlite3_bind_null(stmt, 20) }
            sqlite3_bind_int64(stmt, 21, note.updatedAt)
            if let del = note.deletedAt { sqlite3_bind_int64(stmt, 22, del) } else { sqlite3_bind_null(stmt, 22) }
            sqlite3_bind_int64(stmt, 23, note.syncVersion)
            sqlite3_step(stmt)
        }
        sqlite3_finalize(stmt)
        return fetchRowIdLocked(table: "notes", syncUuid: note.syncUuid)
    }

    private func upsertNoteLinkLocked(_ link: IosNoteLinkRecord) {
        guard let db else { return }
        let sql = """
        INSERT INTO note_links (
            sourceNoteId, targetNoteId, linkText, createdAt, syncUuid,
            sourceSyncUuid, targetSyncUuid, updatedAt, deletedAt, syncVersion
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(syncUuid) DO UPDATE SET
            sourceNoteId = excluded.sourceNoteId,
            targetNoteId = excluded.targetNoteId,
            linkText = excluded.linkText,
            sourceSyncUuid = excluded.sourceSyncUuid,
            targetSyncUuid = excluded.targetSyncUuid,
            updatedAt = excluded.updatedAt,
            deletedAt = excluded.deletedAt,
            syncVersion = excluded.syncVersion;
        """
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            sqlite3_bind_int(stmt, 1, Int32(link.sourceNoteId))
            sqlite3_bind_int(stmt, 2, Int32(link.targetNoteId))
            sqlite3_bind_text(stmt, 3, link.linkText, -1, sqliteTransient)
            sqlite3_bind_int64(stmt, 4, link.createdAt)
            sqlite3_bind_text(stmt, 5, link.syncUuid, -1, sqliteTransient)
            sqlite3_bind_text(stmt, 6, link.sourceSyncUuid, -1, sqliteTransient)
            sqlite3_bind_text(stmt, 7, link.targetSyncUuid, -1, sqliteTransient)
            sqlite3_bind_int64(stmt, 8, link.updatedAt)
            if let del = link.deletedAt { sqlite3_bind_int64(stmt, 9, del) } else { sqlite3_bind_null(stmt, 9) }
            sqlite3_bind_int64(stmt, 10, link.syncVersion)
            sqlite3_step(stmt)
        }
        sqlite3_finalize(stmt)
    }

    private func upsertStreamItemLocked(_ item: IosStreamItemRecord) {
        guard let db else { return }
        let sql = """
        INSERT INTO stream_items (
            content, type, audioPath, imagePath, timestamp, isProcessed,
            mergedNoteId, syncUuid, mergedNoteSyncUuid, updatedAt, deletedAt, syncVersion
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(syncUuid) DO UPDATE SET
            content = excluded.content,
            type = excluded.type,
            audioPath = excluded.audioPath,
            imagePath = excluded.imagePath,
            isProcessed = excluded.isProcessed,
            mergedNoteId = excluded.mergedNoteId,
            mergedNoteSyncUuid = excluded.mergedNoteSyncUuid,
            updatedAt = excluded.updatedAt,
            deletedAt = excluded.deletedAt,
            syncVersion = excluded.syncVersion;
        """
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            sqlite3_bind_text(stmt, 1, item.content, -1, sqliteTransient)
            sqlite3_bind_text(stmt, 2, item.type, -1, sqliteTransient)
            if let ap = item.audioPath { sqlite3_bind_text(stmt, 3, ap, -1, sqliteTransient) } else { sqlite3_bind_null(stmt, 3) }
            if let ip = item.imagePath { sqlite3_bind_text(stmt, 4, ip, -1, sqliteTransient) } else { sqlite3_bind_null(stmt, 4) }
            sqlite3_bind_int64(stmt, 5, item.timestamp)
            sqlite3_bind_int(stmt, 6, item.isProcessed ? 1 : 0)
            if let mid = item.mergedNoteId { sqlite3_bind_int(stmt, 7, Int32(mid)) } else { sqlite3_bind_null(stmt, 7) }
            sqlite3_bind_text(stmt, 8, item.syncUuid, -1, sqliteTransient)
            if let mu = item.mergedNoteSyncUuid { sqlite3_bind_text(stmt, 9, mu, -1, sqliteTransient) } else { sqlite3_bind_null(stmt, 9) }
            sqlite3_bind_int64(stmt, 10, item.updatedAt)
            if let del = item.deletedAt { sqlite3_bind_int64(stmt, 11, del) } else { sqlite3_bind_null(stmt, 11) }
            sqlite3_bind_int64(stmt, 12, item.syncVersion)
            sqlite3_step(stmt)
        }
        sqlite3_finalize(stmt)
    }

    private func upsertAutomationRuleLocked(_ rule: IosAutomationRuleRecord) {
        guard let db else { return }
        let sql = """
        INSERT INTO automation_rules (
            title, triggerType, triggerConfig, actionType, actionConfig, isEnabled,
            createdAt, syncUuid, updatedAt, deletedAt, syncVersion
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(syncUuid) DO UPDATE SET
            title = excluded.title,
            triggerType = excluded.triggerType,
            triggerConfig = excluded.triggerConfig,
            actionType = excluded.actionType,
            actionConfig = excluded.actionConfig,
            isEnabled = excluded.isEnabled,
            updatedAt = excluded.updatedAt,
            deletedAt = excluded.deletedAt,
            syncVersion = excluded.syncVersion;
        """
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            sqlite3_bind_text(stmt, 1, rule.title, -1, sqliteTransient)
            sqlite3_bind_text(stmt, 2, rule.triggerType, -1, sqliteTransient)
            if let tc = rule.triggerConfig { sqlite3_bind_text(stmt, 3, tc, -1, sqliteTransient) } else { sqlite3_bind_null(stmt, 3) }
            sqlite3_bind_text(stmt, 4, rule.actionType, -1, sqliteTransient)
            if let ac = rule.actionConfig { sqlite3_bind_text(stmt, 5, ac, -1, sqliteTransient) } else { sqlite3_bind_null(stmt, 5) }
            sqlite3_bind_int(stmt, 6, rule.isEnabled ? 1 : 0)
            sqlite3_bind_int64(stmt, 7, rule.createdAt)
            sqlite3_bind_text(stmt, 8, rule.syncUuid, -1, sqliteTransient)
            sqlite3_bind_int64(stmt, 9, rule.updatedAt)
            if let del = rule.deletedAt { sqlite3_bind_int64(stmt, 10, del) } else { sqlite3_bind_null(stmt, 10) }
            sqlite3_bind_int64(stmt, 11, rule.syncVersion)
            sqlite3_step(stmt)
        }
        sqlite3_finalize(stmt)
    }

    private func fetchRowIdLocked(table: String, syncUuid: String) -> Int {
        guard let db else { return 0 }
        var out = 0
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, "SELECT id FROM \(table) WHERE syncUuid = ? LIMIT 1;", -1, &stmt, nil) == SQLITE_OK {
            sqlite3_bind_text(stmt, 1, syncUuid, -1, sqliteTransient)
            if sqlite3_step(stmt) == SQLITE_ROW {
                out = Int(sqlite3_column_int(stmt, 0))
            }
        }
        sqlite3_finalize(stmt)
        return out
    }

    private func queryCategoriesLocked(whereClause: String, args: [Int64]) -> [IosCategoryRecord] {
        guard let db else { return [] }
        var out: [IosCategoryRecord] = []
        var stmt: OpaquePointer?
        let sql = "SELECT id, syncUuid, name, isPassword, updatedAt, deletedAt, syncVersion FROM categories \(whereClause) ORDER BY id ASC;"
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            for (idx, val) in args.enumerated() { sqlite3_bind_int64(stmt, Int32(idx + 1), val) }
            while sqlite3_step(stmt) == SQLITE_ROW {
                out.append(IosCategoryRecord(
                    rowId: Int(sqlite3_column_int(stmt, 0)),
                    syncUuid: colString(stmt, 1) ?? "",
                    name: colString(stmt, 2) ?? "",
                    isPassword: sqlite3_column_int(stmt, 3) != 0,
                    updatedAt: sqlite3_column_int64(stmt, 4),
                    deletedAt: colNullableInt64(stmt, 5),
                    syncVersion: sqlite3_column_int64(stmt, 6)
                ))
            }
        }
        sqlite3_finalize(stmt)
        return out
    }

    private func queryNotesLocked(whereClause: String, args: [Any]) -> [IosNoteRecord] {
        guard let db else { return [] }
        var out: [IosNoteRecord] = []
        var stmt: OpaquePointer?
        let sql = """
        SELECT id, title, description, date, isPassword, images, categoryId, isSound, isPinned, icon,
               isEncrypted, totalTasks, completedTasks, status, reminderTime, reminderType, reminderSound,
               isReminderDone, richBlocksJson, syncUuid, categorySyncUuid, updatedAt, deletedAt, syncVersion
        FROM notes \(whereClause)
        ORDER BY isPinned DESC, updatedAt DESC;
        """
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            for (idx, val) in args.enumerated() {
                if let i64 = val as? Int64 { sqlite3_bind_int64(stmt, Int32(idx + 1), i64) }
                else if let str = val as? String { sqlite3_bind_text(stmt, Int32(idx + 1), str, -1, sqliteTransient) }
            }
            while sqlite3_step(stmt) == SQLITE_ROW {
                let desc = colString(stmt, 2) ?? ""
                let rich = colString(stmt, 18) ?? desc
                out.append(IosNoteRecord(
                    rowId: Int(sqlite3_column_int(stmt, 0)),
                    syncUuid: colString(stmt, 19) ?? "",
                    title: colString(stmt, 1) ?? "",
                    description: desc,
                    richBlocksJson: rich,
                    categoryId: colNullableInt(stmt, 6),
                    categorySyncUuid: colString(stmt, 20),
                    isPinned: sqlite3_column_int(stmt, 8) != 0,
                    isPassword: sqlite3_column_int(stmt, 4) != 0,
                    isEncrypted: sqlite3_column_int(stmt, 10) != 0,
                    icon: colString(stmt, 9) ?? "📄",
                    images: decodeStringArray(colString(stmt, 5)),
                    sounds: decodeStringArray(colString(stmt, 7)),
                    totalTasks: Int(sqlite3_column_int(stmt, 11)),
                    completedTasks: Int(sqlite3_column_int(stmt, 12)),
                    status: colString(stmt, 13) ?? "TODO",
                    reminderTime: colNullableInt64(stmt, 14),
                    reminderType: colString(stmt, 15),
                    reminderSound: colString(stmt, 16),
                    isReminderDone: sqlite3_column_int(stmt, 17) != 0,
                    date: sqlite3_column_int64(stmt, 3),
                    updatedAt: sqlite3_column_int64(stmt, 21),
                    deletedAt: colNullableInt64(stmt, 22),
                    syncVersion: sqlite3_column_int64(stmt, 23)
                ))
            }
        }
        sqlite3_finalize(stmt)
        return out
    }

    private func queryLinksLocked(whereClause: String, args: [Any]) -> [IosNoteLinkRecord] {
        guard let db else { return [] }
        var out: [IosNoteLinkRecord] = []
        var stmt: OpaquePointer?
        let sql = "SELECT id, sourceNoteId, targetNoteId, linkText, createdAt, syncUuid, sourceSyncUuid, targetSyncUuid, updatedAt, deletedAt, syncVersion FROM note_links \(whereClause);"
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            for (idx, val) in args.enumerated() {
                if let i64 = val as? Int64 { sqlite3_bind_int64(stmt, Int32(idx + 1), i64) }
                else if let str = val as? String { sqlite3_bind_text(stmt, Int32(idx + 1), str, -1, sqliteTransient) }
            }
            while sqlite3_step(stmt) == SQLITE_ROW {
                out.append(IosNoteLinkRecord(
                    rowId: Int(sqlite3_column_int(stmt, 0)),
                    syncUuid: colString(stmt, 5) ?? "",
                    sourceNoteId: Int(sqlite3_column_int(stmt, 1)),
                    targetNoteId: Int(sqlite3_column_int(stmt, 2)),
                    sourceSyncUuid: colString(stmt, 6) ?? "",
                    targetSyncUuid: colString(stmt, 7) ?? "",
                    linkText: colString(stmt, 3) ?? "",
                    createdAt: sqlite3_column_int64(stmt, 4),
                    updatedAt: sqlite3_column_int64(stmt, 8),
                    deletedAt: colNullableInt64(stmt, 9),
                    syncVersion: sqlite3_column_int64(stmt, 10)
                ))
            }
        }
        sqlite3_finalize(stmt)
        return out
    }

    private func queryStreamLocked(whereClause: String, args: [Int64]) -> [IosStreamItemRecord] {
        guard let db else { return [] }
        var out: [IosStreamItemRecord] = []
        var stmt: OpaquePointer?
        let sql = "SELECT id, content, type, audioPath, imagePath, timestamp, isProcessed, mergedNoteId, syncUuid, mergedNoteSyncUuid, updatedAt, deletedAt, syncVersion FROM stream_items \(whereClause) ORDER BY timestamp DESC;"
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            for (idx, val) in args.enumerated() { sqlite3_bind_int64(stmt, Int32(idx + 1), val) }
            while sqlite3_step(stmt) == SQLITE_ROW {
                out.append(IosStreamItemRecord(
                    rowId: Int(sqlite3_column_int(stmt, 0)),
                    syncUuid: colString(stmt, 8) ?? "",
                    content: colString(stmt, 1) ?? "",
                    type: colString(stmt, 2) ?? "TEXT",
                    audioPath: colString(stmt, 3),
                    imagePath: colString(stmt, 4),
                    timestamp: sqlite3_column_int64(stmt, 5),
                    isProcessed: sqlite3_column_int(stmt, 6) != 0,
                    mergedNoteId: colNullableInt(stmt, 7),
                    mergedNoteSyncUuid: colString(stmt, 9),
                    updatedAt: sqlite3_column_int64(stmt, 10),
                    deletedAt: colNullableInt64(stmt, 11),
                    syncVersion: sqlite3_column_int64(stmt, 12)
                ))
            }
        }
        sqlite3_finalize(stmt)
        return out
    }

    private func queryRulesLocked(whereClause: String, args: [Int64]) -> [IosAutomationRuleRecord] {
        guard let db else { return [] }
        var out: [IosAutomationRuleRecord] = []
        var stmt: OpaquePointer?
        let sql = "SELECT id, title, triggerType, triggerConfig, actionType, actionConfig, isEnabled, createdAt, syncUuid, updatedAt, deletedAt, syncVersion FROM automation_rules \(whereClause);"
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            for (idx, val) in args.enumerated() { sqlite3_bind_int64(stmt, Int32(idx + 1), val) }
            while sqlite3_step(stmt) == SQLITE_ROW {
                out.append(IosAutomationRuleRecord(
                    rowId: Int(sqlite3_column_int(stmt, 0)),
                    syncUuid: colString(stmt, 8) ?? "",
                    title: colString(stmt, 1) ?? "",
                    triggerType: colString(stmt, 2) ?? "",
                    triggerConfig: colString(stmt, 3),
                    actionType: colString(stmt, 4) ?? "",
                    actionConfig: colString(stmt, 5),
                    isEnabled: sqlite3_column_int(stmt, 6) != 0,
                    createdAt: sqlite3_column_int64(stmt, 7),
                    updatedAt: sqlite3_column_int64(stmt, 9),
                    deletedAt: colNullableInt64(stmt, 10),
                    syncVersion: sqlite3_column_int64(stmt, 11)
                ))
            }
        }
        sqlite3_finalize(stmt)
        return out
    }

    private func colString(_ stmt: OpaquePointer?, _ index: Int32) -> String? {
        guard sqlite3_column_type(stmt, index) != SQLITE_NULL,
              let ptr = sqlite3_column_text(stmt, index) else { return nil }
        return String(cString: ptr)
    }

    private func colNullableInt(_ stmt: OpaquePointer?, _ index: Int32) -> Int? {
        guard sqlite3_column_type(stmt, index) != SQLITE_NULL else { return nil }
        return Int(sqlite3_column_int(stmt, index))
    }

    private func colNullableInt64(_ stmt: OpaquePointer?, _ index: Int32) -> Int64? {
        guard sqlite3_column_type(stmt, index) != SQLITE_NULL else { return nil }
        return sqlite3_column_int64(stmt, index)
    }

    private func encodeStringArray(_ list: [String]) -> String? {
        guard !list.isEmpty,
              let data = try? JSONEncoder().encode(list),
              let str = String(data: data, encoding: .utf8) else { return nil }
        return str
    }

    private func decodeStringArray(_ raw: String?) -> [String] {
        guard let raw, !raw.isEmpty, let data = raw.data(using: .utf8),
              let arr = try? JSONDecoder().decode([String].self, from: data) else { return [] }
        return arr
    }
}
