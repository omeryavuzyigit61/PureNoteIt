import Foundation
import PureNoteShared

private struct CodableSyncCursor: Codable {
    var remoteCursor: String?
    var lastPushedAt: Int64
    var lastSyncedAt: Int64
    var deviceId: String
    var uploadedMediaKeys: [String]
}

/// Bridges `:shared` KMP `LocalSyncStore` to iOS SQLite3 + iOS sandboxed media filesystem.
/// Allows bi-directional Google Drive E2EE sync between Android, Windows, macOS, and iOS.
final class IosLocalSyncStore: NSObject, LocalSyncStore {
    private let db: IosDatabaseManager

    init(db: IosDatabaseManager) {
        self.db = db
    }

    func readModifiedSince(sinceMillis: Int64) async throws -> any AppResult {
        let cats = db.fetchAllCategories(sinceMillis: sinceMillis).map { $0.toKotlin() }
        let notes = db.fetchNotes(includeDeleted: true, sinceMillis: sinceMillis).map { $0.toKotlin() }
        let links = db.fetchLinks(includeDeleted: true, sinceMillis: sinceMillis).map { $0.toKotlin() }
        let streams = db.fetchStreamItems(includeDeleted: true, sinceMillis: sinceMillis).map { $0.toKotlin() }
        let rules = db.fetchAutomationRules(sinceMillis: sinceMillis).map { $0.toKotlin() }

        let snapshot = SharedIosSyncBridge.shared.makeLocalSyncSnapshot(
            categories: cats,
            notes: notes,
            noteLinks: links,
            streamItems: streams,
            automationRules: rules
        )
        return SharedIosSyncBridge.shared.makeSuccessSnapshotResult(snapshot: snapshot)
    }

    func applyResolvedRemoteBatch(batch: ResolvedRemoteBatch) async throws -> any AppResult {
        let cats = batch.categories.map { IosCategoryRecord.fromKotlin($0) }
        let notes = batch.notes.map { IosNoteRecord.fromKotlin($0) }
        let links = batch.noteLinks.map { IosNoteLinkRecord.fromKotlin($0) }
        let streams = batch.streamItems.map { IosStreamItemRecord.fromKotlin($0) }
        let rules = batch.automationRules.map { IosAutomationRuleRecord.fromKotlin($0) }

        db.applyRemoteSyncTransaction(
            categories: cats,
            notes: notes,
            links: links,
            streamItems: streams,
            rules: rules
        )
        return SharedIosSyncBridge.shared.makeSuccessUnitLocalResult()
    }

    func findCategoriesBySyncUuids(syncUuids: Any) async throws -> [String: PureNoteShared.Category] {
        let keys = extractUuidSet(syncUuids)
        guard !keys.isEmpty else { return [:] }
        var map: [String: PureNoteShared.Category] = [:]
        for c in db.fetchAllCategories() where keys.contains(c.syncUuid) {
            map[c.syncUuid] = c.toKotlin()
        }
        return map
    }

    func findNotesBySyncUuids(syncUuids: Any) async throws -> [String: Note] {
        let keys = extractUuidSet(syncUuids)
        guard !keys.isEmpty else { return [:] }
        var map: [String: Note] = [:]
        for n in db.fetchNotes(includeDeleted: true) where keys.contains(n.syncUuid) {
            map[n.syncUuid] = n.toKotlin()
        }
        return map
    }

    func findLinksBySyncUuids(syncUuids: Any) async throws -> [String: NoteLink] {
        let keys = extractUuidSet(syncUuids)
        guard !keys.isEmpty else { return [:] }
        var map: [String: NoteLink] = [:]
        for l in db.fetchLinks(includeDeleted: true) where keys.contains(l.syncUuid) {
            map[l.syncUuid] = l.toKotlin()
        }
        return map
    }

    func findStreamItemsBySyncUuids(syncUuids: Any) async throws -> [String: StreamItem] {
        let keys = extractUuidSet(syncUuids)
        guard !keys.isEmpty else { return [:] }
        var map: [String: StreamItem] = [:]
        for s in db.fetchStreamItems(includeDeleted: true) where keys.contains(s.syncUuid) {
            map[s.syncUuid] = s.toKotlin()
        }
        return map
    }

    func findRulesBySyncUuids(syncUuids: Any) async throws -> [String: AutomationRule] {
        let keys = extractUuidSet(syncUuids)
        guard !keys.isEmpty else { return [:] }
        var map: [String: AutomationRule] = [:]
        for r in db.fetchAutomationRules() where keys.contains(r.syncUuid) {
            map[r.syncUuid] = r.toKotlin()
        }
        return map
    }

    func hasLocalMedia(mediaPath: String) async throws -> KotlinBoolean {
        guard let resolved = db.mediaResolver.resolveToReadablePath(rawPath: mediaPath) else {
            return KotlinBoolean(bool: false)
        }
        return KotlinBoolean(bool: FileManager.default.fileExists(atPath: resolved))
    }

    func readLocalMediaBytes(mediaPath: String) async throws -> KotlinByteArray? {
        guard let resolved = db.mediaResolver.resolveToReadablePath(rawPath: mediaPath),
              let data = try? Data(contentsOf: URL(fileURLWithPath: resolved)) else {
            return nil
        }
        return data.toKotlinByteArray()
    }

    func writeLocalMediaBytes(mediaPath: String, bytes: KotlinByteArray) async throws -> any AppResult {
        guard db.mediaResolver.isCanonicalRelativePath(rawPath: mediaPath) else {
            return SharedIosSyncBridge.shared.makeFailureUnitLocalResult(error: .validationFailed)
        }
        let targetPath = db.rootDir.appendingPathComponent("files/\(mediaPath)")
        do {
            try FileManager.default.createDirectory(
                at: targetPath.deletingLastPathComponent(),
                withIntermediateDirectories: true
            )
            try bytes.toData().write(to: targetPath, options: .atomic)
            return SharedIosSyncBridge.shared.makeSuccessUnitLocalResult()
        } catch {
            return SharedIosSyncBridge.shared.makeFailureUnitLocalResult(error: .unknown)
        }
    }

    func getSyncCursorState() async throws -> SyncCursorState {
        if let data = try? Data(contentsOf: db.syncCursorURL),
           let dto = try? JSONDecoder().decode(CodableSyncCursor.self, from: data) {
            return SharedIosSyncBridge.shared.makeSyncCursorState(
                remoteCursor: dto.remoteCursor,
                lastPushedAt: dto.lastPushedAt,
                lastSyncedAt: dto.lastSyncedAt,
                deviceId: dto.deviceId,
                uploadedMediaKeys: dto.uploadedMediaKeys
            )
        }
        return SharedIosSyncBridge.shared.makeSyncCursorState(
            remoteCursor: nil,
            lastPushedAt: 0,
            lastSyncedAt: 0,
            deviceId: SharedIosEditorBridge.shared.doNewUuid(),
            uploadedMediaKeys: []
        )
    }

    func saveSyncCursorState(state: SyncCursorState) async throws {
        let dto = CodableSyncCursor(
            remoteCursor: state.remoteCursor,
            lastPushedAt: state.lastPushedAt,
            lastSyncedAt: state.lastSyncedAt,
            deviceId: state.deviceId,
            uploadedMediaKeys: Array(state.uploadedMediaKeys)
        )
        if let data = try? JSONEncoder().encode(dto) {
            try? data.write(to: db.syncCursorURL, options: .atomic)
        }
    }

    private func extractUuidSet(_ raw: Any) -> Set<String> {
        if let s = raw as? Set<String> { return s }
        if let arr = raw as? [String] { return Set(arr) }
        if let nsSet = raw as? NSSet { return Set(nsSet.allObjects.compactMap { $0 as? String }) }
        return []
    }
}

private extension IosCategoryRecord {
    func toKotlin() -> PureNoteShared.Category {
        SharedIosSyncBridge.shared.makeCategory(
            id: Int32(rowId),
            name: name,
            isPassword: KotlinBoolean(bool: isPassword),
            syncUuid: syncUuid,
            updatedAt: updatedAt,
            deletedAt: deletedAt.map { KotlinLong(value: $0) },
            syncVersion: syncVersion
        )
    }

    static func fromKotlin(_ c: PureNoteShared.Category) -> IosCategoryRecord {
        IosCategoryRecord(
            rowId: Int(c.id),
            syncUuid: c.syncUuid,
            name: c.name,
            isPassword: c.isPassword?.boolValue ?? false,
            updatedAt: c.updatedAt,
            deletedAt: c.deletedAt?.int64Value,
            syncVersion: c.syncVersion
        )
    }
}

private extension IosNoteRecord {
    func toKotlin() -> Note {
        SharedIosSyncBridge.shared.makeNote(
            id: Int32(rowId),
            title: title,
            description: description,
            date: date,
            isPassword: KotlinBoolean(bool: isPassword),
            images: images,
            categoryId: categoryId.map { KotlinInt(value: Int32($0)) },
            sounds: sounds,
            isPinned: isPinned,
            icon: icon,
            isEncrypted: isEncrypted,
            totalTasks: Int32(totalTasks),
            completedTasks: Int32(completedTasks),
            status: status,
            reminderTime: reminderTime.map { KotlinLong(value: $0) },
            reminderType: reminderType,
            reminderSound: reminderSound,
            isReminderDone: isReminderDone,
            richBlocksJson: richBlocksJson,
            syncUuid: syncUuid,
            categorySyncUuid: categorySyncUuid,
            updatedAt: updatedAt,
            deletedAt: deletedAt.map { KotlinLong(value: $0) },
            syncVersion: syncVersion
        )
    }

    static func fromKotlin(_ n: Note) -> IosNoteRecord {
        let rich = n.richBlocksJson ?? n.description ?? ""
        return IosNoteRecord(
            rowId: Int(n.id),
            syncUuid: n.syncUuid,
            title: n.title ?? "",
            description: n.description ?? rich,
            richBlocksJson: rich,
            categoryId: n.categoryId?.intValue,
            categorySyncUuid: n.categorySyncUuid,
            isPinned: n.isPinned,
            isPassword: n.isPassword?.boolValue ?? false,
            isEncrypted: n.isEncrypted,
            icon: n.icon ?? "📄",
            images: n.images ?? [],
            sounds: n.sounds ?? [],
            totalTasks: Int(n.totalTasks),
            completedTasks: Int(n.completedTasks),
            status: n.status,
            reminderTime: n.reminderTime?.int64Value,
            reminderType: n.reminderType,
            reminderSound: n.reminderSound,
            isReminderDone: n.isReminderDone,
            date: n.date,
            updatedAt: n.updatedAt,
            deletedAt: n.deletedAt?.int64Value,
            syncVersion: n.syncVersion
        )
    }
}

private extension IosNoteLinkRecord {
    func toKotlin() -> NoteLink {
        SharedIosSyncBridge.shared.makeNoteLink(
            id: Int32(rowId),
            sourceNoteId: Int32(sourceNoteId),
            targetNoteId: Int32(targetNoteId),
            linkText: linkText,
            createdAt: createdAt,
            syncUuid: syncUuid,
            sourceSyncUuid: sourceSyncUuid,
            targetSyncUuid: targetSyncUuid,
            updatedAt: updatedAt,
            deletedAt: deletedAt.map { KotlinLong(value: $0) },
            syncVersion: syncVersion
        )
    }

    static func fromKotlin(_ l: NoteLink) -> IosNoteLinkRecord {
        IosNoteLinkRecord(
            rowId: Int(l.id),
            syncUuid: l.syncUuid,
            sourceNoteId: Int(l.sourceNoteId),
            targetNoteId: Int(l.targetNoteId),
            sourceSyncUuid: l.sourceSyncUuid,
            targetSyncUuid: l.targetSyncUuid,
            linkText: l.linkText,
            createdAt: l.createdAt,
            updatedAt: l.updatedAt,
            deletedAt: l.deletedAt?.int64Value,
            syncVersion: l.syncVersion
        )
    }
}

private extension IosStreamItemRecord {
    func toKotlin() -> StreamItem {
        SharedIosSyncBridge.shared.makeStreamItem(
            id: Int32(rowId),
            content: content,
            type: type,
            audioPath: audioPath,
            imagePath: imagePath,
            timestamp: timestamp,
            isProcessed: isProcessed,
            mergedNoteId: mergedNoteId.map { KotlinInt(value: Int32($0)) },
            syncUuid: syncUuid,
            mergedNoteSyncUuid: mergedNoteSyncUuid,
            updatedAt: updatedAt,
            deletedAt: deletedAt.map { KotlinLong(value: $0) },
            syncVersion: syncVersion
        )
    }

    static func fromKotlin(_ s: StreamItem) -> IosStreamItemRecord {
        IosStreamItemRecord(
            rowId: Int(s.id),
            syncUuid: s.syncUuid,
            content: s.content,
            type: s.type,
            audioPath: s.audioPath,
            imagePath: s.imagePath,
            timestamp: s.timestamp,
            isProcessed: s.isProcessed,
            mergedNoteId: s.mergedNoteId?.intValue,
            mergedNoteSyncUuid: s.mergedNoteSyncUuid,
            updatedAt: s.updatedAt,
            deletedAt: s.deletedAt?.int64Value,
            syncVersion: s.syncVersion
        )
    }
}

private extension IosAutomationRuleRecord {
    func toKotlin() -> AutomationRule {
        SharedIosSyncBridge.shared.makeAutomationRule(
            id: Int32(rowId),
            title: title,
            triggerType: triggerType,
            triggerConfig: triggerConfig,
            actionType: actionType,
            actionConfig: actionConfig,
            isEnabled: isEnabled,
            createdAt: createdAt,
            syncUuid: syncUuid,
            updatedAt: updatedAt,
            deletedAt: deletedAt.map { KotlinLong(value: $0) },
            syncVersion: syncVersion
        )
    }

    static func fromKotlin(_ r: AutomationRule) -> IosAutomationRuleRecord {
        IosAutomationRuleRecord(
            rowId: Int(r.id),
            syncUuid: r.syncUuid,
            title: r.title,
            triggerType: r.triggerType,
            triggerConfig: r.triggerConfig,
            actionType: r.actionType,
            actionConfig: r.actionConfig,
            isEnabled: r.isEnabled,
            createdAt: r.createdAt,
            updatedAt: r.updatedAt,
            deletedAt: r.deletedAt?.int64Value,
            syncVersion: r.syncVersion
        )
    }
}
