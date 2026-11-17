import AVFoundation
import Foundation
import JavaScriptCore
import PureNoteShared
import Speech
import SQLite3
import UIKit
import UserNotifications
import Vision

// MARK: - Audio Recorder & Waveform Player Manager

@MainActor
final class IosAudioManager: NSObject, ObservableObject, AVAudioPlayerDelegate, AVAudioRecorderDelegate {
    @Published var isRecording: Bool = false
    @Published var recordingSeconds: Int = 0
    @Published var currentLevel: CGFloat = 0.22
    @Published var recordingSamples: [CGFloat] = Array(repeating: 0.18, count: 16)
    @Published var recordingError: String? = nil

    @Published var isPlaying: Bool = false
    @Published var activePlayingPath: String? = nil
    @Published var playbackSamples: [CGFloat] = Array(repeating: 0.25, count: 20)
    @Published var playbackSpeed: Double = 1.0
    @Published var currentPosition: Double = 0
    @Published var duration: Double = 0

    private var recorder: AVAudioRecorder?
    private var recordingURL: URL?
    private var recordingStartDate: Date?
    private var recordingFinishedHandler: ((URL) -> Void)?
    private var player: AVAudioPlayer?
    private var meterTimer: Timer?

    func startRecording(stagingPath: String, onFinished: @escaping (URL) -> Void) {
        if isPlaying {
            stopPlayback()
        }
        if isRecording {
            finishRecording(save: false)
        }
        recordingError = nil

        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker, .allowBluetoothHFP])
            try session.setActive(true)
        } catch {
            do {
                try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker])
                try session.setActive(true)
            } catch {
                // Continue gracefully on virtual simulator audio hardware
            }
        }

        AVAudioApplication.requestRecordPermission { [weak self] granted in
            Task { @MainActor in
                guard let self else { return }
                guard granted else {
                    self.recordingError = "Mikrofon izni gerekli."
                    return
                }
                self.beginRecorderSession(stagingPath: stagingPath, onFinished: onFinished)
            }
        }
    }

    private func beginRecorderSession(stagingPath: String, onFinished: @escaping (URL) -> Void) {
        let targetURL = URL(fileURLWithPath: stagingPath)
        try? FileManager.default.createDirectory(at: targetURL.deletingLastPathComponent(), withIntermediateDirectories: true)

        let settings: [String: Any] = [
            AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
            AVSampleRateKey: 44_100,
            AVNumberOfChannelsKey: 1,
            AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue
        ]

        do {
            let rec = try AVAudioRecorder(url: targetURL, settings: settings)
            rec.delegate = self
            rec.isMeteringEnabled = true
            rec.prepareToRecord()
            guard rec.record() else {
                recordingError = "Mikrofon kaydı başlatılamadı."
                return
            }
            self.recorder = rec
            self.recordingURL = targetURL
            self.recordingStartDate = Date()
            self.recordingFinishedHandler = onFinished
            self.isRecording = true
            self.recordingSeconds = 0
            self.currentLevel = 0.28
            self.recordingSamples = Array(repeating: 0.22, count: 16)
            startTimer()
        } catch {
            self.recorder = nil
            self.recordingError = error.localizedDescription
        }
    }

    func finishRecording(save: Bool) {
        guard isRecording else { return }
        let rec = recorder
        let url = recordingURL
        let handler = recordingFinishedHandler

        rec?.stop()
        recorder = nil
        recordingURL = nil
        recordingStartDate = nil
        recordingFinishedHandler = nil
        isRecording = false
        currentLevel = 0.18
        stopTimerIfIdle()

        if save, let url, FileManager.default.fileExists(atPath: url.path) {
            handler?(url)
        } else if let url {
            try? FileManager.default.removeItem(at: url)
        }
    }

    func togglePlayback(canonicalOrPath: String, resolvedPath: String?) {
        if isPlaying && activePlayingPath == canonicalOrPath {
            player?.pause()
            isPlaying = false
            stopTimerIfIdle()
            return
        }

        if let player, activePlayingPath == canonicalOrPath {
            player.enableRate = true
            player.rate = Float(playbackSpeed)
            player.play()
            isPlaying = true
            startTimer()
            return
        }

        stopPlayback()
        let pathToLoad = resolvedPath ?? canonicalOrPath
        guard !pathToLoad.isEmpty, FileManager.default.fileExists(atPath: pathToLoad) else { return }

        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .default)
        try? session.setActive(true)

        do {
            let p = try AVAudioPlayer(contentsOf: URL(fileURLWithPath: pathToLoad))
            p.delegate = self
            p.isMeteringEnabled = true
            p.enableRate = true
            p.rate = Float(playbackSpeed)
            p.prepareToPlay()
            p.play()
            self.player = p
            self.activePlayingPath = canonicalOrPath
            self.duration = p.duration
            self.currentPosition = 0
            self.isPlaying = true
            startTimer()
        } catch {
            stopPlayback()
        }
    }

    func cycleSpeed() {
        let speeds = [1.0, 1.25, 1.5, 2.0]
        let idx = speeds.firstIndex(where: { abs($0 - playbackSpeed) < 0.05 }) ?? 0
        let next = speeds[(idx + 1) % speeds.count]
        playbackSpeed = next
        if let player {
            player.enableRate = true
            player.rate = Float(next)
        }
    }

    func seek(to time: Double) {
        guard let player else { return }
        let clamped = max(0, min(time, player.duration))
        player.currentTime = clamped
        currentPosition = clamped
    }

    func stopPlayback() {
        player?.stop()
        player = nil
        isPlaying = false
        activePlayingPath = nil
        currentPosition = 0
        duration = 0
        currentLevel = 0.18
        stopTimerIfIdle()
    }

    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor in
            self.isPlaying = false
            self.currentPosition = 0
            self.currentLevel = 0.18
            self.stopTimerIfIdle()
        }
    }

    private func startTimer() {
        meterTimer?.invalidate()
        let timer = Timer(timeInterval: 0.06, repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.tickMeters()
            }
        }
        RunLoop.main.add(timer, forMode: .common)
        meterTimer = timer
    }

    private func stopTimerIfIdle() {
        if !isRecording && !isPlaying {
            meterTimer?.invalidate()
            meterTimer = nil
        }
    }

    private func tickMeters() {
        if isRecording, let recorder {
            recorder.updateMeters()
            let db = recorder.averagePower(forChannel: 0)
            let rawNorm = CGFloat(max(0.16, min(1.0, (db + 54.0) / 48.0)))
            let smoothed = currentLevel * 0.45 + rawNorm * 0.55
            currentLevel = smoothed
            var next = recordingSamples
            if !next.isEmpty { next.removeFirst() }
            next.append(smoothed)
            recordingSamples = next

            let wallElapsed = recordingStartDate.map { Int(Date().timeIntervalSince($0)) } ?? 0
            let recElapsed = recorder.currentTime.isFinite ? Int(recorder.currentTime) : 0
            let totalSecs = max(wallElapsed, recElapsed)
            if totalSecs != recordingSeconds {
                recordingSeconds = totalSecs
            }
        }

        if isPlaying, let player {
            player.updateMeters()
            let db = player.averagePower(forChannel: 0)
            let base = CGFloat(max(0.16, min(1.0, (db + 50.0) / 44.0)))
            let smoothed = currentLevel * 0.45 + base * 0.55
            currentLevel = smoothed
            var next = playbackSamples
            if !next.isEmpty { next.removeFirst() }
            next.append(smoothed)
            playbackSamples = next
            currentPosition = player.currentTime
            duration = player.duration
        }
    }
}

// MARK: - Live Speech-to-Text Dictation (`SFSpeechRecognizer`)

@MainActor
final class IosSpeechDictationManager: ObservableObject {
    @Published var isListening: Bool = false
    @Published var partialText: String = ""
    @Published var audioLevel: CGFloat = 0.22
    @Published var statusError: String? = nil

    private var recognizer: SFSpeechRecognizer?
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    private var audioEngine: AVAudioEngine?
    private var onFinalHandler: ((String) -> Void)?

    func toggleListening(isTurkish: Bool, onFinalResult: @escaping (String) -> Void) {
        if isListening {
            commitCurrentDictation()
            return
        }

        onFinalHandler = onFinalResult
        partialText = ""
        statusError = nil

        SFSpeechRecognizer.requestAuthorization { [weak self] authStatus in
            Task { @MainActor in
                guard let self else { return }
                guard authStatus == .authorized else {
                    self.statusError = isTurkish ? "Konuşma tanıma izni verilmedi." : "Speech permission denied."
                    return
                }
                self.beginSpeechSession(isTurkish: isTurkish)
            }
        }
    }

    func commitCurrentDictation() {
        let final = partialText.trimmingCharacters(in: .whitespacesAndNewlines)
        let handler = onFinalHandler
        stopListening()
        if !final.isEmpty {
            handler?(final)
        }
        partialText = ""
    }

    private func beginSpeechSession(isTurkish: Bool) {
        stopListening()
        let localeId = isTurkish ? "tr-TR" : "en-US"
        let speechRec = SFSpeechRecognizer(locale: Locale(identifier: localeId)) ?? SFSpeechRecognizer()
        guard let speechRec, speechRec.isAvailable else {
            statusError = isTurkish ? "Konuşma tanıma şu anda kullanılamıyor." : "Speech recognizer unavailable."
            return
        }
        recognizer = speechRec

        let audioSession = AVAudioSession.sharedInstance()
        do {
            try audioSession.setCategory(.playAndRecord, mode: .measurement, options: [.duckOthers, .defaultToSpeaker])
            try audioSession.setActive(true, options: .notifyOthersOnDeactivation)
        } catch {
            statusError = error.localizedDescription
            return
        }

        let engine = AVAudioEngine()
        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        request.taskHint = .dictation
        if #available(iOS 16.0, *) {
            request.addsPunctuation = true
        }
        self.audioEngine = engine
        self.recognitionRequest = request

        let inputNode = engine.inputNode
        inputNode.removeTap(onBus: 0)
        let outFmt = inputNode.outputFormat(forBus: 0)
        let inFmt = inputNode.inputFormat(forBus: 0)
        let format = outFmt.sampleRate > 0 ? outFmt : inFmt
        guard format.sampleRate > 0 else {
            statusError = isTurkish ? "Mikrofon girişi bulunamadı." : "No audio input."
            return
        }

        inputNode.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak request, weak self] buffer, _ in
            request?.append(buffer)
            if let channelData = buffer.floatChannelData?[0] {
                let frameLen = Int(buffer.frameLength)
                var sum: Float = 0
                for i in stride(from: 0, to: frameLen, by: max(1, frameLen / 32)) {
                    let s = channelData[i]
                    sum += s * s
                }
                let rms = sqrt(sum / max(1, Float(min(32, frameLen))))
                let lvl = CGFloat(min(1.0, max(0.18, rms * 9.0)))
                Task { @MainActor [weak self] in
                    self?.audioLevel = lvl
                }
            }
        }

        do {
            engine.prepare()
            try engine.start()
            isListening = true
        } catch {
            stopListening()
            statusError = error.localizedDescription
            return
        }

        recognitionTask = speechRec.recognitionTask(with: request) { [weak self] result, error in
            Task { @MainActor in
                guard let self else { return }
                if let result {
                    let live = result.bestTranscription.formattedString
                    self.partialText = live
                    if result.isFinal {
                        let txt = live.trimmingCharacters(in: .whitespacesAndNewlines)
                        let handler = self.onFinalHandler
                        self.stopListening()
                        if !txt.isEmpty {
                            handler?(txt)
                        }
                        self.partialText = ""
                    }
                } else if error != nil {
                    self.stopListening()
                }
            }
        }
    }

    func stopListening() {
        audioEngine?.stop()
        audioEngine?.inputNode.removeTap(onBus: 0)
        audioEngine = nil
        recognitionRequest?.endAudio()
        recognitionRequest = nil
        recognitionTask?.cancel()
        recognitionTask = nil
        isListening = false
        audioLevel = 0.2
        onFinalHandler = nil
    }
}

// MARK: - On-Device Vision OCR Service

enum IosOcrService {
    static func recognizeText(from image: UIImage) async -> String {
        guard let cgImage = image.cgImage else { return "" }
        return await withCheckedContinuation { continuation in
            DispatchQueue.global(qos: .userInitiated).async {
                let request = VNRecognizeTextRequest { req, _ in
                    let observations = (req.results as? [VNRecognizedTextObservation]) ?? []
                    let lines = observations.compactMap { $0.topCandidates(1).first?.string }
                    continuation.resume(returning: lines.joined(separator: "\n"))
                }
                request.recognitionLevel = .accurate
                request.recognitionLanguages = ["tr-TR", "en-US"]
                request.usesLanguageCorrection = true

                let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
                do {
                    try handler.perform([request])
                } catch {
                    continuation.resume(returning: "")
                }
            }
        }
    }
}

// MARK: - Local Notifications & Reminders

enum IosReminderService {
    static func scheduleReminder(
        noteSyncUuid: String,
        title: String,
        isProtected: Bool,
        fireDate: Date,
        type: String
    ) async -> Bool {
        let center = UNUserNotificationCenter.current()
        let granted = (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
        guard granted else { return false }

        let content = UNMutableNotificationContent()
        content.title = "PureNoteIt"
        content.body = isProtected
            ? "🔒 Kilitli kasa notu hatırlatıcısı"
            : (title.isEmpty ? "Not Hatırlatıcısı" : title)
        content.sound = type == "ALARM" ? .defaultCritical : .default
        content.userInfo = ["noteSyncUuid": noteSyncUuid]

        let comps = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: fireDate)
        let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
        let req = UNNotificationRequest(identifier: "purenote_\(noteSyncUuid)", content: content, trigger: trigger)
        do {
            try await center.add(req)
            return true
        } catch {
            return false
        }
    }

    static func cancelReminder(noteSyncUuid: String) {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ["purenote_\(noteSyncUuid)"])
    }
}

// MARK: - Multi-Language Offline Code Execution & Static Syntax Validation

struct IosCodeRunOutput: Equatable {
    let stdout: String
    let success: Bool
    let isHtmlPreview: Bool
    let formattedCode: String?
}

enum IosOfflineCodeRunner {
    static func run(code: String, language: String) -> IosCodeRunOutput {
        let lang = language.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let trimmed = code.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            return IosCodeRunOutput(stdout: "Hata: Kod bloğu boş.", success: false, isHtmlPreview: false, formattedCode: nil)
        }

        switch lang {
        case "javascript", "js":
            return runJavaScript(code: code)
        case "sql":
            return runInMemorySql(sql: code)
        case "json":
            return validateAndFormatJson(raw: code)
        case "html":
            return IosCodeRunOutput(stdout: code, success: true, isHtmlPreview: true, formattedCode: nil)
        default:
            return runSharedStaticSyntaxCheck(code: code, language: lang)
        }
    }

    private static func runJavaScript(code: String) -> IosCodeRunOutput {
        guard let ctx = JSContext() else {
            return IosCodeRunOutput(stdout: "JavaScriptCore başlatılamadı.", success: false, isHtmlPreview: false, formattedCode: nil)
        }
        var logs: [String] = []
        var jsException: String? = nil

        ctx.exceptionHandler = { _, exc in
            jsException = exc?.toString() ?? "Bilinmeyen JS hatası"
        }

        let logFn: @convention(block) (JSValue) -> Void = { val in
            logs.append(val.toString() ?? "undefined")
        }
        ctx.evaluateScript("var console = {};")
        ctx.objectForKeyedSubscript("console")?.setObject(logFn, forKeyedSubscript: "log")
        ctx.objectForKeyedSubscript("console")?.setObject(logFn, forKeyedSubscript: "info")
        ctx.objectForKeyedSubscript("console")?.setObject(logFn, forKeyedSubscript: "warn")
        ctx.objectForKeyedSubscript("console")?.setObject(logFn, forKeyedSubscript: "error")

        let result = ctx.evaluateScript(code)
        if let jsException {
            return IosCodeRunOutput(stdout: "❌ JS Hata: \(jsException)", success: false, isHtmlPreview: false, formattedCode: nil)
        }
        var outLines = logs
        if let result, !result.isUndefined, !result.isNull {
            let s = result.toString() ?? ""
            if !s.isEmpty && logs.isEmpty {
                outLines.append("↳ \(s)")
            }
        }
        let outputText = outLines.isEmpty ? "✓ JavaScript çalıştırıldı (çıktı yok)." : outLines.joined(separator: "\n")
        return IosCodeRunOutput(stdout: outputText, success: true, isHtmlPreview: false, formattedCode: nil)
    }

    private static func runInMemorySql(sql: String) -> IosCodeRunOutput {
        var db: OpaquePointer?
        guard sqlite3_open(":memory:", &db) == SQLITE_OK, let db else {
            return IosCodeRunOutput(stdout: "Bellek içi SQLite açılamadı.", success: false, isHtmlPreview: false, formattedCode: nil)
        }
        defer { sqlite3_close(db) }

        let statements = sql.split(separator: ";").map { String($0).trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
        var output: [String] = []
        for stmtText in statements {
            var stmt: OpaquePointer?
            if sqlite3_prepare_v2(db, stmtText, -1, &stmt, nil) != SQLITE_OK {
                let errMsg = String(cString: sqlite3_errmsg(db))
                return IosCodeRunOutput(stdout: "❌ SQL Hata: \(errMsg)", success: false, isHtmlPreview: false, formattedCode: nil)
            }
            defer { sqlite3_finalize(stmt) }
            let colCount = Int(sqlite3_column_count(stmt))
            if colCount > 0 {
                let headers = (0..<colCount).map { i in
                    String(cString: sqlite3_column_name(stmt, Int32(i)))
                }
                output.append(headers.joined(separator: " | "))
                output.append(String(repeating: "-", count: max(10, headers.joined(separator: " | ").count)))
                while sqlite3_step(stmt) == SQLITE_ROW {
                    let row = (0..<colCount).map { i -> String in
                        if let ptr = sqlite3_column_text(stmt, Int32(i)) {
                            return String(cString: ptr)
                        }
                        return "NULL"
                    }
                    output.append(row.joined(separator: " | "))
                }
            } else {
                let rc = sqlite3_step(stmt)
                if rc != SQLITE_DONE {
                    let errMsg = String(cString: sqlite3_errmsg(db))
                    return IosCodeRunOutput(stdout: "❌ SQL Yürütme Hatası: \(errMsg)", success: false, isHtmlPreview: false, formattedCode: nil)
                }
                output.append("✓ Sorgu uygulandı (\(sqlite3_changes(db)) satır).")
            }
        }
        return IosCodeRunOutput(stdout: output.joined(separator: "\n"), success: true, isHtmlPreview: false, formattedCode: nil)
    }

    private static func validateAndFormatJson(raw: String) -> IosCodeRunOutput {
        guard let data = raw.data(using: .utf8) else {
            return IosCodeRunOutput(stdout: "Geçersiz UTF-8 JSON.", success: false, isHtmlPreview: false, formattedCode: nil)
        }
        do {
            let obj = try JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed])
            let prettyData = try JSONSerialization.data(withJSONObject: obj, options: [.prettyPrinted, .sortedKeys])
            let formatted = String(data: prettyData, encoding: .utf8) ?? raw
            return IosCodeRunOutput(stdout: "✓ Geçerli JSON — Biçimlendirildi.", success: true, isHtmlPreview: false, formattedCode: formatted)
        } catch {
            return IosCodeRunOutput(stdout: "❌ JSON Sözdizimi Hatası: \(error.localizedDescription)", success: false, isHtmlPreview: false, formattedCode: nil)
        }
    }

    private static func runSharedStaticSyntaxCheck(code: String, language: String) -> IosCodeRunOutput {
        let report = SharedIosEditorBridge.shared.analyzeCodeSyntax(code: code, language: language)
        if report.isValid {
            return IosCodeRunOutput(
                stdout: "✓ [\(language.uppercased())] Statik sözdizimi doğrulaması geçti (\(report.lineCount) satır).",
                success: true,
                isHtmlPreview: false,
                formattedCode: nil
            )
        } else {
            let lineInfo = report.issueLine.map { "Satır \($0.intValue): " } ?? ""
            let issueName = report.issueType?.name ?? "SYNTAX_ERROR"
            let exp = report.expectedToken.map { " Beklenen: \($0)" } ?? ""
            let act = report.actualToken.map { " Bulunan: \($0)" } ?? ""
            return IosCodeRunOutput(
                stdout: "❌ \(lineInfo)\(issueName).\(exp)\(act)",
                success: false,
                isHtmlPreview: false,
                formattedCode: nil
            )
        }
    }
}

// MARK: - PDF Studio, Markdown Export & Encrypted `.purevault.json` Backup

struct PdfExportOptions {
    var includeText: Bool = true
    var includeTasks: Bool = true
    var includeImages: Bool = true
    var includeDrawings: Bool = true
    var includeAudioBadge: Bool = true
    var includeHeader: Bool = true
}

private struct IosVaultBackupEnvelope: Codable {
    var format: String = "PURE_VAULT_JSON_V1"
    var createdAt: Int64
    var cipher: String
}

private struct IosVaultBackupPayload: Codable {
    var version: Int = 4
    var backupDate: Int64
    var categories: [IosBackupCategoryDto]
    var notes: [IosBackupNoteDto]
    var links: [IosBackupLinkDto]
    var streamItems: [IosBackupStreamDto]?
}

private struct IosBackupCategoryDto: Codable {
    var syncUuid: String
    var name: String
    var isPassword: Bool
    var updatedAt: Int64
    var deletedAt: Int64?
    var syncVersion: Int64
}

private struct IosBackupNoteDto: Codable {
    var syncUuid: String
    var title: String
    var description: String
    var richBlocksJson: String
    var categorySyncUuid: String?
    var isPinned: Bool
    var isPassword: Bool
    var isEncrypted: Bool
    var icon: String
    var images: [String]
    var sounds: [String]
    var totalTasks: Int
    var completedTasks: Int
    var status: String
    var reminderTime: Int64?
    var reminderType: String?
    var reminderSound: String?
    var isReminderDone: Bool
    var date: Int64
    var updatedAt: Int64
    var deletedAt: Int64?
    var syncVersion: Int64
}

private struct IosBackupLinkDto: Codable {
    var syncUuid: String
    var sourceSyncUuid: String
    var targetSyncUuid: String
    var linkText: String
    var createdAt: Int64
    var updatedAt: Int64
    var deletedAt: Int64?
    var syncVersion: Int64
}

private struct IosBackupStreamDto: Codable {
    var syncUuid: String
    var content: String
    var type: String
    var audioPath: String?
    var imagePath: String?
    var timestamp: Int64
    var isProcessed: Bool
    var mergedNoteSyncUuid: String?
    var updatedAt: Int64
    var deletedAt: Int64?
    var syncVersion: Int64
}

enum IosExporterAndBackupService {
    static func exportPdf(
        title: String,
        blocks: [EditableBlock],
        options: PdfExportOptions,
        mediaResolver: MediaPathResolver
    ) -> URL? {
        let pageRect = CGRect(x: 0, y: 0, width: 595, height: 842) // A4 points
        let margin: CGFloat = 44
        let contentWidth = pageRect.width - margin * 2

        let renderer = UIGraphicsPDFRenderer(bounds: pageRect)
        let data = renderer.pdfData { ctx in
            var pageNumber = 1
            var y: CGFloat = margin

            func startNewPage() {
                ctx.beginPage()
                if options.includeHeader {
                    let headerAttr: [NSAttributedString.Key: Any] = [
                        .font: UIFont.systemFont(ofSize: 9, weight: .semibold),
                        .foregroundColor: UIColor.systemGray
                    ]
                    NSString(string: "PureNoteIt • \(title.isEmpty ? "Untitled" : title)").draw(at: CGPoint(x: margin, y: 20), withAttributes: headerAttr)
                    NSString(string: "Sayfa \(pageNumber)").draw(at: CGPoint(x: pageRect.width - margin - 52, y: pageRect.height - 26), withAttributes: headerAttr)
                }
                y = margin + (options.includeHeader ? 12 : 0)
                pageNumber += 1
            }

            func ensureSpace(_ needed: CGFloat) {
                if y + needed > pageRect.height - margin - 20 {
                    startNewPage()
                }
            }

            func drawText(_ text: String, font: UIFont, color: UIColor = .label, indent: CGFloat = 0, bottomSpacing: CGFloat = 8) {
                let avail = contentWidth - indent
                let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color]
                let rect = NSString(string: text).boundingRect(
                    with: CGSize(width: avail, height: 680),
                    options: [.usesLineFragmentOrigin, .usesFontLeading],
                    attributes: attrs,
                    context: nil
                )
                ensureSpace(rect.height + bottomSpacing)
                NSString(string: text).draw(in: CGRect(x: margin + indent, y: y, width: avail, height: rect.height + 2), withAttributes: attrs)
                y += rect.height + bottomSpacing
            }

            startNewPage()
            drawText(title.isEmpty ? "Başlıksız Not" : title, font: .systemFont(ofSize: 24, weight: .bold), color: .black, bottomSpacing: 16)

            for block in blocks {
                switch block.type {
                case .heading1:
                    guard options.includeText else { continue }
                    drawText(block.content, font: .systemFont(ofSize: 19, weight: .bold), color: .black, bottomSpacing: 10)
                case .heading2:
                    guard options.includeText else { continue }
                    drawText(block.content, font: .systemFont(ofSize: 15, weight: .semibold), color: .darkGray, bottomSpacing: 8)
                case .todo:
                    guard options.includeTasks else { continue }
                    let prefix = block.isChecked ? "[✓] " : "[  ] "
                    drawText(prefix + block.content, font: .systemFont(ofSize: 12), color: block.isChecked ? .gray : .black, indent: 12)
                case .bulletList:
                    guard options.includeText else { continue }
                    drawText("• " + block.content, font: .systemFont(ofSize: 12), color: .black, indent: 10)
                case .callout:
                    guard options.includeText else { continue }
                    drawText("\(block.calloutIcon) \(block.content)", font: .systemFont(ofSize: 12, weight: .medium), color: UIColor(red: 0.85, green: 0.42, blue: 0.0, alpha: 1.0), indent: 8)
                case .toggle:
                    guard options.includeText else { continue }
                    drawText("▸ \(block.content)", font: .systemFont(ofSize: 12, weight: .semibold), color: .black)
                    if !block.toggleBody.isEmpty {
                        drawText(block.toggleBody, font: .systemFont(ofSize: 11), color: .darkGray, indent: 14)
                    }
                case .code:
                    guard options.includeText else { continue }
                    drawText("[\(block.codeLanguage.uppercased())]\n\(block.content)", font: .monospacedSystemFont(ofSize: 10.5, weight: .regular), color: .darkGray, indent: 8)
                case .math:
                    guard options.includeText else { continue }
                    drawText("∑ \(block.content)", font: .monospacedSystemFont(ofSize: 12, weight: .medium), color: .black, indent: 8)
                case .table:
                    guard options.includeText else { continue }
                    let lines = block.tableData.map { $0.joined(separator: "  |  ") }.joined(separator: "\n")
                    drawText(lines, font: .monospacedSystemFont(ofSize: 10.5, weight: .regular), color: .black)
                case .image, .drawing:
                    let allowed = block.type == .image ? options.includeImages : options.includeDrawings
                    guard allowed, let rel = block.mediaPath,
                          let resolved = mediaResolver.resolveToReadablePath(rawPath: rel),
                          let img = UIImage(contentsOfFile: resolved) else { continue }
                    let ratio = img.size.height / max(1, img.size.width)
                    let drawW = min(contentWidth, 380)
                    let drawH = min(drawW * ratio, 260)
                    ensureSpace(drawH + 12)
                    img.draw(in: CGRect(x: margin, y: y, width: drawW, height: drawH))
                    y += drawH + 12
                case .audio:
                    guard options.includeAudioBadge else { continue }
                    drawText("🎙️ Ses Kaydı (\(block.mediaPath ?? "audio"))", font: .italicSystemFont(ofSize: 11), color: .systemOrange)
                case .divider:
                    ensureSpace(14)
                    let path = UIBezierPath()
                    path.move(to: CGPoint(x: margin, y: y + 4))
                    path.addLine(to: CGPoint(x: pageRect.width - margin, y: y + 4))
                    UIColor.systemGray4.setStroke()
                    path.lineWidth = 1
                    path.stroke()
                    y += 14
                case .webBookmark:
                    guard options.includeText else { continue }
                    drawText("🌐 \(block.content)", font: .systemFont(ofSize: 11.5), color: .systemBlue)
                case .subPage:
                    guard options.includeText else { continue }
                    drawText("↳ Alt Sayfa: \(block.content)", font: .systemFont(ofSize: 12, weight: .medium), color: .black)
                case .paragraph:
                    guard options.includeText, !block.content.isEmpty else { continue }
                    drawText(block.content, font: .systemFont(ofSize: 12), color: .black)
                case .tableOfContents:
                    continue
                }
            }
        }

        let safeName = (title.isEmpty ? "PureNote" : title).replacingOccurrences(of: "/", with: "-")
        let outURL = FileManager.default.temporaryDirectory.appendingPathComponent("\(safeName).pdf")
        try? data.write(to: outURL)
        return outURL
    }

    static func exportMarkdown(title: String, blocks: [EditableBlock]) -> URL? {
        var md: [String] = ["# \(title.isEmpty ? "Untitled" : title)", ""]
        for b in blocks {
            switch b.type {
            case .heading1: md.append("# \(b.content)")
            case .heading2: md.append("## \(b.content)")
            case .todo: md.append("- [\(b.isChecked ? "x" : " ")] \(b.content)")
            case .bulletList: md.append("- \(b.content)")
            case .callout: md.append("> \(b.calloutIcon) \(b.content)")
            case .toggle: md.append("<details><summary>\(b.content)</summary>\n\n\(b.toggleBody)\n</details>")
            case .code: md.append("```\(b.codeLanguage)\n\(b.content)\n```")
            case .math: md.append("$$\n\(b.content)\n$$")
            case .divider: md.append("---")
            case .webBookmark: md.append("[\(b.content)](\(b.content))")
            case .subPage: md.append("📄 **\(b.content)**")
            case .table:
                if let header = b.tableData.first {
                    md.append("| " + header.joined(separator: " | ") + " |")
                    md.append("| " + header.map { _ in "---" }.joined(separator: " | ") + " |")
                    for row in b.tableData.dropFirst() {
                        md.append("| " + row.joined(separator: " | ") + " |")
                    }
                }
            case .image, .drawing:
                md.append("![](\(b.mediaPath ?? ""))")
            case .audio:
                md.append("[Audio](\(b.mediaPath ?? ""))")
            case .paragraph:
                md.append(b.content)
            case .tableOfContents:
                break
            }
            md.append("")
        }
        let safeName = (title.isEmpty ? "PureNote" : title).replacingOccurrences(of: "/", with: "-")
        let outURL = FileManager.default.temporaryDirectory.appendingPathComponent("\(safeName).md")
        try? md.joined(separator: "\n").write(to: outURL, atomically: true, encoding: .utf8)
        return outURL
    }

    static func exportEncryptedVaultJson(
        db: IosDatabaseManager,
        vaultCrypto: VaultCrypto,
        passphrase: String
    ) -> URL? {
        let cleanPass = passphrase.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanPass.isEmpty else { return nil }

        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let payload = IosVaultBackupPayload(
            version: 4,
            backupDate: now,
            categories: db.fetchAllCategories().map {
                IosBackupCategoryDto(syncUuid: $0.syncUuid, name: $0.name, isPassword: $0.isPassword, updatedAt: $0.updatedAt, deletedAt: $0.deletedAt, syncVersion: $0.syncVersion)
            },
            notes: db.fetchNotes(includeDeleted: true).map {
                IosBackupNoteDto(
                    syncUuid: $0.syncUuid,
                    title: $0.title,
                    description: $0.description,
                    richBlocksJson: $0.richBlocksJson,
                    categorySyncUuid: $0.categorySyncUuid,
                    isPinned: $0.isPinned,
                    isPassword: $0.isPassword,
                    isEncrypted: $0.isEncrypted,
                    icon: $0.icon,
                    images: $0.images,
                    sounds: $0.sounds,
                    totalTasks: $0.totalTasks,
                    completedTasks: $0.completedTasks,
                    status: $0.status,
                    reminderTime: $0.reminderTime,
                    reminderType: $0.reminderType,
                    reminderSound: $0.reminderSound,
                    isReminderDone: $0.isReminderDone,
                    date: $0.date,
                    updatedAt: $0.updatedAt,
                    deletedAt: $0.deletedAt,
                    syncVersion: $0.syncVersion
                )
            },
            links: db.fetchLinks(includeDeleted: true).map {
                IosBackupLinkDto(syncUuid: $0.syncUuid, sourceSyncUuid: $0.sourceSyncUuid, targetSyncUuid: $0.targetSyncUuid, linkText: $0.linkText, createdAt: $0.createdAt, updatedAt: $0.updatedAt, deletedAt: $0.deletedAt, syncVersion: $0.syncVersion)
            },
            streamItems: db.fetchStreamItems(includeDeleted: true).map {
                IosBackupStreamDto(syncUuid: $0.syncUuid, content: $0.content, type: $0.type, audioPath: $0.audioPath, imagePath: $0.imagePath, timestamp: $0.timestamp, isProcessed: $0.isProcessed, mergedNoteSyncUuid: $0.mergedNoteSyncUuid, updatedAt: $0.updatedAt, deletedAt: $0.deletedAt, syncVersion: $0.syncVersion)
            }
        )

        guard let jsonData = try? JSONEncoder().encode(payload),
              let plainJson = String(data: jsonData, encoding: .utf8),
              let cipher = SharedIosCryptoFactory.shared.encryptText(crypto: vaultCrypto, plainText: plainJson, passphrase: cleanPass) else {
            return nil
        }

        let envelope = IosVaultBackupEnvelope(createdAt: now, cipher: cipher)
        guard let outData = try? JSONEncoder().encode(envelope) else { return nil }
        let outURL = FileManager.default.temporaryDirectory.appendingPathComponent("PureNoteIt-Backup.purevault.json")
        try? outData.write(to: outURL)
        return outURL
    }

    static func importEncryptedVaultJson(
        from url: URL,
        db: IosDatabaseManager,
        vaultCrypto: VaultCrypto,
        passphrase: String
    ) -> Int {
        let cleanPass = passphrase.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanPass.isEmpty,
              let rawData = try? Data(contentsOf: url),
              let rawText = String(data: rawData, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines) else {
            return -1
        }

        let cipherString: String
        if let env = try? JSONDecoder().decode(IosVaultBackupEnvelope.self, from: rawData) {
            cipherString = env.cipher
        } else {
            cipherString = rawText
        }

        guard let decrypted = SharedIosCryptoFactory.shared.decryptText(
            crypto: vaultCrypto,
            encryptedText: cipherString,
            passphrase: cleanPass
        ),
        let payloadData = decrypted.data(using: .utf8),
        let payload = try? JSONDecoder().decode(IosVaultBackupPayload.self, from: payloadData) else {
            return -1
        }

        let categories = payload.categories.map {
            IosCategoryRecord(rowId: 0, syncUuid: $0.syncUuid, name: $0.name, isPassword: $0.isPassword, updatedAt: $0.updatedAt, deletedAt: $0.deletedAt, syncVersion: $0.syncVersion)
        }
        let notes = payload.notes.map {
            IosNoteRecord(
                rowId: 0,
                syncUuid: $0.syncUuid,
                title: $0.title,
                description: $0.description,
                richBlocksJson: $0.richBlocksJson,
                categoryId: nil,
                categorySyncUuid: $0.categorySyncUuid,
                isPinned: $0.isPinned,
                isPassword: $0.isPassword,
                isEncrypted: $0.isEncrypted,
                icon: $0.icon,
                images: $0.images,
                sounds: $0.sounds,
                totalTasks: $0.totalTasks,
                completedTasks: $0.completedTasks,
                status: $0.status,
                reminderTime: $0.reminderTime,
                reminderType: $0.reminderType,
                reminderSound: $0.reminderSound,
                isReminderDone: $0.isReminderDone,
                date: $0.date,
                updatedAt: $0.updatedAt,
                deletedAt: $0.deletedAt,
                syncVersion: $0.syncVersion
            )
        }
        let links = payload.links.map {
            IosNoteLinkRecord(rowId: 0, syncUuid: $0.syncUuid, sourceNoteId: 0, targetNoteId: 0, sourceSyncUuid: $0.sourceSyncUuid, targetSyncUuid: $0.targetSyncUuid, linkText: $0.linkText, createdAt: $0.createdAt, updatedAt: $0.updatedAt, deletedAt: $0.deletedAt, syncVersion: $0.syncVersion)
        }
        let streams = (payload.streamItems ?? []).map {
            IosStreamItemRecord(rowId: 0, syncUuid: $0.syncUuid, content: $0.content, type: $0.type, audioPath: $0.audioPath, imagePath: $0.imagePath, timestamp: $0.timestamp, isProcessed: $0.isProcessed, mergedNoteId: nil, mergedNoteSyncUuid: $0.mergedNoteSyncUuid, updatedAt: $0.updatedAt, deletedAt: $0.deletedAt, syncVersion: $0.syncVersion)
        }

        for c in categories { db.upsertCategory(c) }
        for n in notes { db.upsertNote(n) }
        for l in links { db.upsertNoteLink(l) }
        for s in streams { db.upsertStreamItem(s) }
        return notes.count
    }
}
