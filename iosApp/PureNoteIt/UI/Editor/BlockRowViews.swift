import PureNoteShared
import SwiftUI
import WebKit

struct BlockRowCard: View {
    @Binding var block: EditableBlock
    let allBlocks: [EditableBlock]
    let allNotes: [IosNoteRecord]
    @ObservedObject var store: PureNoteStore
    let index: Int
    let totalCount: Int

    let onPersist: () -> Void
    let onMoveUp: () -> Void
    let onMoveDown: () -> Void
    let onDelete: () -> Void
    let onEditDrawing: () -> Void
    let onOpenLightbox: (URL) -> Void
    let onOpenSubPage: (String) -> Void
    let onScrollToBlockId: (String) -> Void
    let onOpenWikiTitle: (String) -> Void

    @State private var codeResult: IosCodeRunOutput? = nil
    @State private var showMathPalette: Bool = false
    @State private var mathTab: Int = 0
    @State private var copiedCode: Bool = false

    private let languages = ["kotlin", "python", "javascript", "java", "cpp", "sql", "html", "json", "bash"]

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top, spacing: 8) {
                blockBody
                    .frame(maxWidth: .infinity, alignment: .leading)

                VStack(spacing: 4) {
                    if index > 0 {
                        Button(action: onMoveUp) {
                            Image(systemName: "chevron.up")
                                .font(.caption2.bold())
                                .foregroundStyle(.tertiary)
                        }
                    }
                    if index < totalCount - 1 {
                        Button(action: onMoveDown) {
                            Image(systemName: "chevron.down")
                                .font(.caption2.bold())
                                .foregroundStyle(.tertiary)
                        }
                    }
                    if totalCount > 1 {
                        Button(action: onDelete) {
                            Image(systemName: "xmark.circle.fill")
                                .font(.caption)
                                .foregroundStyle(.tertiary)
                        }
                    }
                }
                .padding(.top, 4)
            }
        }
    }

    @ViewBuilder
    private var blockBody: some View {
        switch block.type {
        case .paragraph:
            VStack(alignment: .leading, spacing: 4) {
                TextField(
                    store.isTurkish ? "Düşüncelerinizi yazın... (/ ile blok ekle)" : "Write your thoughts... (/ for blocks)",
                    text: $block.content,
                    axis: .vertical
                )
                .font(.body)

                let tags = SharedIosEditorBridge.shared.extractHashtags(text: block.content)
                let links = SharedIosEditorBridge.shared.extractWikiLinks(text: block.content)
                if !tags.isEmpty || !links.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(links, id: \.self) { title in
                                Button {
                                    onOpenWikiTitle(title)
                                } label: {
                                    Label(title, systemImage: "link")
                                        .font(.caption2.weight(.semibold))
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 3)
                                        .background(PureColors.accentBlue.opacity(0.16))
                                        .foregroundStyle(PureColors.accentBlue)
                                        .clipShape(Capsule())
                                }
                            }
                            ForEach(tags, id: \.self) { t in
                                Text("#\(t)")
                                    .font(.caption2.weight(.semibold))
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 3)
                                    .background(PureColors.vibrantOrange.opacity(0.16))
                                    .foregroundStyle(PureColors.vibrantOrange)
                                    .clipShape(Capsule())
                            }
                        }
                    }
                }
            }

        case .heading1:
            HStack(spacing: 8) {
                ModernHeadingBadge(text: block.content, isH1: true)
                TextField(store.isTurkish ? "Başlık 1..." : "Heading 1...", text: $block.content, axis: .vertical)
                    .font(.system(size: 22, weight: .bold))
            }

        case .heading2:
            HStack(spacing: 8) {
                ModernHeadingBadge(text: block.content, isH1: false)
                TextField(store.isTurkish ? "Başlık 2..." : "Heading 2...", text: $block.content, axis: .vertical)
                    .font(.system(size: 18, weight: .semibold))
            }

        case .todo:
            HStack(alignment: .top, spacing: 10) {
                Button {
                    block.isChecked.toggle()
                    onPersist()
                } label: {
                    Image(systemName: block.isChecked ? "checkmark.square.fill" : "square")
                        .font(.title3)
                        .foregroundStyle(block.isChecked ? PureColors.vibrantOrange : .secondary)
                }
                TextField(store.isTurkish ? "Görev..." : "Task...", text: $block.content, axis: .vertical)
                    .strikethrough(block.isChecked)
                    .foregroundStyle(block.isChecked ? .secondary : .primary)
            }

        case .bulletList:
            HStack(alignment: .top, spacing: 10) {
                Circle()
                    .fill(PureColors.vibrantOrange)
                    .frame(width: 7, height: 7)
                    .padding(.top, 8)
                TextField(store.isTurkish ? "Liste öğesi..." : "List item...", text: $block.content, axis: .vertical)
            }

        case .callout:
            HStack(alignment: .top, spacing: 10) {
                ModernCalloutBadge(key: block.calloutIcon)
                TextField(store.isTurkish ? "Önemli kutu metni..." : "Callout text...", text: $block.content, axis: .vertical)
                    .font(.subheadline.weight(.medium))
            }
            .padding(12)
            .background(PureColors.vibrantOrange.opacity(0.12))
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(PureColors.vibrantOrange.opacity(0.28), lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

        case .toggle:
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    Button {
                        withAnimation(.spring(response: 0.25)) {
                            block.isExpanded.toggle()
                        }
                    } label: {
                        Image(systemName: "play.fill")
                            .font(.caption)
                            .foregroundStyle(PureColors.vibrantOrange)
                            .rotationEffect(.degrees(block.isExpanded ? 90 : 0))
                    }
                    TextField(store.isTurkish ? "Açılır başlık..." : "Toggle title...", text: $block.content)
                        .fontWeight(.semibold)
                }
                if block.isExpanded {
                    HStack(spacing: 8) {
                        RoundedRectangle(cornerRadius: 2)
                            .fill(PureColors.vibrantOrange)
                            .frame(width: 3)
                        TextField(store.isTurkish ? "Gizli içerik..." : "Hidden content...", text: $block.toggleBody, axis: .vertical)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.leading, 10)
                }
            }
            .padding(12)
            .pureGlassCard(cornerRadius: 14)

        case .table:
            tableBlockView

        case .divider:
            Divider().padding(.vertical, 6)

        case .audio:
            audioPlayerBlockView

        case .image, .drawing:
            mediaImageBlockView

        case .webBookmark:
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 8) {
                    Image(systemName: "globe")
                        .foregroundStyle(PureColors.accentBlue)
                    TextField("https://...", text: $block.content)
                        .keyboardType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .font(.subheadline)
                    if let url = URL(string: block.content.trimmingCharacters(in: .whitespacesAndNewlines)),
                       url.scheme?.hasPrefix("http") == true {
                        Link(destination: url) {
                            Image(systemName: "arrow.up.right.square.fill")
                                .foregroundStyle(PureColors.accentBlue)
                        }
                    }
                }
            }
            .padding(12)
            .pureGlassCard(cornerRadius: 14)

        case .subPage:
            Button {
                if let uuid = block.subPageSyncUuid {
                    onOpenSubPage(uuid)
                }
            } label: {
                HStack(spacing: 10) {
                    ModernNoteIconBadge(iconKey: "📄", size: 28, iconSize: 14)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(block.content.isEmpty ? (store.isTurkish ? "Alt Sayfa" : "Sub-page") : block.content)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.primary)
                        Text(store.isTurkish ? "Alt sayfayı aç ↗" : "Open sub-page ↗")
                            .font(.caption2)
                            .foregroundStyle(PureColors.vibrantOrange)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(12)
                .pureGlassCard(cornerRadius: 14)
            }

        case .code:
            codeBlockView

        case .math:
            mathBlockView

        case .tableOfContents:
            tocBlockView
        }
    }

    // MARK: - Table with Auto-Sum

    private var tableBlockView: some View {
        let rows = block.tableData.isEmpty ? [["Başlık 1", "Tutar", "Not"], ["Örnek", "100", ""]] : block.tableData
        let colCount = max(1, rows.first?.count ?? 2)
        let col1Sum: Double = rows.dropFirst().reduce(0.0) { acc, r in
            guard r.count > 1 else { return acc }
            let clean = r[1].replacingOccurrences(of: "[^0-9.,-]", with: "", options: .regularExpression)
                .replacingOccurrences(of: ",", with: ".")
            return acc + (Double(clean) ?? 0.0)
        }

        return VStack(alignment: .leading, spacing: 8) {
            ScrollView(.horizontal, showsIndicators: true) {
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(rows.indices, id: \.self) { rIdx in
                        HStack(spacing: 4) {
                            ForEach(0..<colCount, id: \.self) { cIdx in
                                TextField(
                                    "",
                                    text: Binding(
                                        get: {
                                            let cur = block.tableData.isEmpty ? rows : block.tableData
                                            return rIdx < cur.count && cIdx < cur[rIdx].count ? cur[rIdx][cIdx] : ""
                                        },
                                        set: { newVal in
                                            var cur = block.tableData.isEmpty ? rows : block.tableData
                                            while cur[rIdx].count < colCount { cur[rIdx].append("") }
                                            cur[rIdx][cIdx] = newVal
                                            block.tableData = cur
                                        }
                                    )
                                )
                                .font(rIdx == 0 ? .caption.bold() : .caption)
                                .padding(7)
                                .frame(width: 112)
                                .background(rIdx == 0 ? PureColors.vibrantOrange.opacity(0.16) : Color.secondary.opacity(0.08))
                                .clipShape(RoundedRectangle(cornerRadius: 7))
                            }
                        }
                    }
                }
            }

            HStack {
                Button {
                    var cur = block.tableData.isEmpty ? rows : block.tableData
                    cur.append(Array(repeating: "", count: colCount))
                    block.tableData = cur
                    onPersist()
                } label: {
                    Label(store.isTurkish ? "Satır" : "Row", systemImage: "plus")
                        .font(.caption2.bold())
                }
                Button {
                    var cur = block.tableData.isEmpty ? rows : block.tableData
                    for i in cur.indices { cur[i].append("") }
                    block.tableData = cur
                    onPersist()
                } label: {
                    Label(store.isTurkish ? "Sütun" : "Col", systemImage: "plus")
                        .font(.caption2.bold())
                }
                Spacer()
                if col1Sum != 0 {
                    Text("∑ \(String(format: "%.2f", col1Sum))")
                        .font(.caption.bold().monospacedDigit())
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(PureColors.emeraldGreen.opacity(0.16))
                        .foregroundStyle(PureColors.emeraldGreen)
                        .clipShape(Capsule())
                }
            }
        }
        .padding(10)
        .pureGlassCard(cornerRadius: 14)
    }

    // MARK: - Spotify-style Audio Player with Waveform

    private var audioPlayerBlockView: some View {
        let path = block.mediaPath ?? ""
        let resolvedURL = store.resolveMediaURL(path)
        let isThisPlaying = store.audioManager.activePlayingPath == path && store.audioManager.isPlaying

        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 12) {
                Button {
                    store.audioManager.togglePlayback(canonicalOrPath: path, resolvedPath: resolvedURL?.path)
                } label: {
                    Image(systemName: isThisPlaying ? "pause.circle.fill" : "play.circle.fill")
                        .font(.system(size: 34))
                        .foregroundStyle(PureColors.vibrantOrange)
                }
                .disabled(resolvedURL == nil)

                // Animated fluid frequency wave visualizer
                LiquidAudioWaveformView(
                    isActive: isThisPlaying,
                    level: isThisPlaying ? store.audioManager.currentLevel : 0.2,
                    tint: PureColors.vibrantOrange,
                    height: 28
                )
                .frame(maxWidth: .infinity)

                Button {
                    store.audioManager.cycleSpeed()
                } label: {
                    Text(String(format: "%.2gx", store.audioManager.playbackSpeed))
                        .font(.caption2.bold().monospacedDigit())
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(PureColors.vibrantOrange.opacity(0.16))
                        .foregroundStyle(PureColors.vibrantOrange)
                        .clipShape(Capsule())
                }
            }

            if store.audioManager.activePlayingPath == path && store.audioManager.duration > 0 {
                HStack(spacing: 8) {
                    Text(formatDuration(store.audioManager.currentPosition))
                        .font(.caption2.monospacedDigit())
                        .foregroundStyle(.secondary)
                    Slider(
                        value: Binding(
                            get: { store.audioManager.currentPosition },
                            set: { store.audioManager.seek(to: $0) }
                        ),
                        in: 0...max(0.1, store.audioManager.duration)
                    )
                    .tint(PureColors.vibrantOrange)
                    Text(formatDuration(store.audioManager.duration))
                        .font(.caption2.monospacedDigit())
                        .foregroundStyle(.secondary)
                }
            } else if resolvedURL == nil {
                Text(store.isTurkish ? "Ses dosyası cihazda bulunamadı (Drive eşitlemesi gerekebilir)" : "Audio file not found locally")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(12)
        .pureGlassCard(cornerRadius: 16)
    }

    // MARK: - Image & Drawing Preview

    private var mediaImageBlockView: some View {
        let url = store.resolveMediaURL(block.mediaPath)
        let widthFactor: CGFloat = block.content.contains("50") ? 0.5 : (block.content.contains("75") ? 0.75 : 1.0)

        return VStack(alignment: .leading, spacing: 8) {
            if let url, let img = UIImage(contentsOfFile: url.path) {
                Button {
                    onOpenLightbox(url)
                } label: {
                    Image(uiImage: img)
                        .resizable()
                        .scaledToFit()
                        .frame(maxWidth: .infinity, maxHeight: 280)
                        .scaleEffect(x: widthFactor, y: 1.0, anchor: .leading)
                        .background(block.type == .drawing ? Color.white : Color.clear)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
            } else {
                HStack(spacing: 8) {
                    Image(systemName: block.type == .drawing ? "paintbrush.pointed.fill" : "photo")
                        .foregroundStyle(.secondary)
                    Text(block.type == .drawing
                         ? (store.isTurkish ? "Çizim yüklenemedi" : "Drawing missing")
                         : (store.isTurkish ? "Görsel yüklenemedi" : "Image missing"))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(14)
            }

            HStack(spacing: 8) {
                ForEach(["%100", "%75", "%50"], id: \.self) { preset in
                    Button {
                        block.content = preset
                        onPersist()
                    } label: {
                        Text(preset)
                            .font(.caption2.bold())
                            .padding(.horizontal, 7)
                            .padding(.vertical, 3)
                            .background((block.content == preset || (preset == "%100" && block.content.isEmpty)) ? PureColors.vibrantOrange.opacity(0.2) : Color.secondary.opacity(0.12))
                            .clipShape(Capsule())
                    }
                }
                Spacer()
                if let url {
                    Button {
                        onOpenLightbox(url)
                    } label: {
                        Label(store.isTurkish ? "Büyüt" : "Zoom", systemImage: "magnifyingglass")
                            .font(.caption2.bold())
                    }
                }
                if block.type == .drawing {
                    Button {
                        onEditDrawing()
                    } label: {
                        Label(store.isTurkish ? "Düzenle" : "Edit", systemImage: "pencil")
                            .font(.caption2.bold())
                            .foregroundStyle(PureColors.vibrantOrange)
                    }
                }
            }
        }
        .padding(10)
        .pureGlassCard(cornerRadius: 14)
    }

    // MARK: - Code Block with Offline Execution

    private var codeBlockView: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Circle().fill(Color(red: 1.0, green: 0.37, blue: 0.34)).frame(width: 9, height: 9)
                Circle().fill(Color(red: 1.0, green: 0.74, blue: 0.18)).frame(width: 9, height: 9)
                Circle().fill(Color(red: 0.15, green: 0.79, blue: 0.25)).frame(width: 9, height: 9)

                Menu {
                    ForEach(languages, id: \.self) { lang in
                        Button(lang.uppercased()) {
                            block.codeLanguage = lang
                            onPersist()
                        }
                    }
                } label: {
                    Text(block.codeLanguage.uppercased())
                        .font(.caption2.bold())
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color.white.opacity(0.12))
                        .foregroundStyle(.white)
                        .clipShape(Capsule())
                }

                Spacer()

                Button {
                    UIPasteboard.general.string = block.content
                    copiedCode = true
                    Task {
                        try? await Task.sleep(nanoseconds: 1_500_000_000)
                        copiedCode = false
                    }
                } label: {
                    Image(systemName: copiedCode ? "checkmark" : "doc.on.doc")
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.8))
                }

                Button {
                    codeResult = IosOfflineCodeRunner.run(code: block.content, language: block.codeLanguage)
                    if let formatted = codeResult?.formattedCode {
                        block.content = formatted
                        onPersist()
                    }
                } label: {
                    Label(store.isTurkish ? "Çalıştır" : "Run", systemImage: "play.fill")
                        .font(.caption2.bold())
                        .padding(.horizontal, 9)
                        .padding(.vertical, 4)
                        .background(PureColors.vibrantOrange)
                        .foregroundStyle(.white)
                        .clipShape(Capsule())
                }
            }

            TextEditor(text: $block.content)
                .font(.system(size: 13, design: .monospaced))
                .foregroundColor(Color(red: 0.90, green: 0.93, blue: 0.96))
                .frame(minHeight: 88)
                .scrollContentBackground(.hidden)
                .padding(8)
                .background(Color.black.opacity(0.35))
                .clipShape(RoundedRectangle(cornerRadius: 8))

            if let res = codeResult {
                if res.isHtmlPreview {
                    SandboxedHtmlView(html: res.stdout)
                        .frame(height: 160)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                } else {
                    Text(res.stdout)
                        .font(.system(size: 12, design: .monospaced))
                        .foregroundStyle(res.success ? PureColors.emeraldGreen : Color(red: 1.0, green: 0.45, blue: 0.45))
                        .padding(8)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.black.opacity(0.45))
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }
            }
        }
        .padding(12)
        .background(Color(red: 0.118, green: 0.118, blue: 0.141)) // #1E1E24
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    // MARK: - Math Formula Block + 5-Tab Palette

    private var mathBlockView: some View {
        let tabs: [(name: String, symbols: [String])] = [
            (store.isTurkish ? "Temel" : "Basic", ["+", "-", "×", "÷", "=", "≠", "≈", "≤", "≥", "±"]),
            (store.isTurkish ? "Cebir" : "Algebra", ["\\frac{a}{b}", "\\sqrt{x}", "x^{2}", "x_{i}", "(a+b)^{2}", "|x|"]),
            (store.isTurkish ? "Analiz" : "Calculus", ["\\int_{a}^{b}", "\\sum_{i=1}^{n}", "\\prod", "\\lim_{x \\to 0}", "\\partial", "\\infty"]),
            (store.isTurkish ? "Yunan" : "Greek", ["\\alpha", "\\beta", "\\gamma", "\\theta", "\\lambda", "\\pi", "\\sigma", "\\omega"]),
            (store.isTurkish ? "Şablonlar" : "Presets", [
                "x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}",
                "e^{i\\pi} + 1 = 0",
                "a^2 + b^2 = c^2",
                "\\int_{-\\infty}^{\\infty} e^{-x^2} dx = \\sqrt{\\pi}"
            ])
        ]

        return VStack(alignment: .leading, spacing: 8) {
            HStack {
                Label("LaTeX Math", systemImage: "function")
                    .font(.caption.bold())
                    .foregroundStyle(PureColors.vibrantOrange)
                Spacer()
                Button(showMathPalette ? (store.isTurkish ? "Paleti Gizle" : "Hide") : (store.isTurkish ? "Sembol Paleti" : "Palette")) {
                    showMathPalette.toggle()
                }
                .font(.caption2.bold())
            }

            // Visual preview rendering common LaTeX glyphs cleanly
            Text(prettyMathPreview(block.content))
                .font(.system(size: 18, weight: .medium, design: .serif))
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .center)
                .background(PureColors.vibrantOrange.opacity(0.08))
                .clipShape(RoundedRectangle(cornerRadius: 10))

            TextField("\\int_0^1 x^2 dx", text: $block.content, axis: .vertical)
                .font(.system(.caption, design: .monospaced))
                .padding(6)
                .background(Color.secondary.opacity(0.08))
                .clipShape(RoundedRectangle(cornerRadius: 8))

            if showMathPalette {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(tabs.indices, id: \.self) { idx in
                            Button(tabs[idx].name) { mathTab = idx }
                                .font(.caption2.bold())
                                .padding(.horizontal, 9)
                                .padding(.vertical, 4)
                                .background(mathTab == idx ? PureColors.vibrantOrange : Color.secondary.opacity(0.15))
                                .foregroundStyle(mathTab == idx ? .white : .primary)
                                .clipShape(Capsule())
                        }
                    }
                }
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(tabs[mathTab].symbols, id: \.self) { sym in
                            Button {
                                if mathTab == 4 { block.content = sym }
                                else { block.content += sym }
                            } label: {
                                Text(prettyMathPreview(sym))
                                    .font(.caption.monospaced())
                                    .padding(.horizontal, 9)
                                    .padding(.vertical, 5)
                                    .background(Color.secondary.opacity(0.12))
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                            }
                        }
                    }
                }
            }
        }
        .padding(12)
        .pureGlassCard(cornerRadius: 14)
    }

    private var tocBlockView: some View {
        let headings = allBlocks.filter { $0.type == .heading1 || $0.type == .heading2 }
        return VStack(alignment: .leading, spacing: 6) {
            Label(store.isTurkish ? "İçindekiler" : "Table of Contents", systemImage: "list.number")
                .font(.caption.bold())
                .foregroundStyle(PureColors.vibrantOrange)
            if headings.isEmpty {
                Text(store.isTurkish ? "Henüz başlık (H1/H2) eklenmedi." : "No headings yet.")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(headings) { h in
                    Button {
                        onScrollToBlockId(h.id)
                    } label: {
                        HStack(spacing: 6) {
                            Text(h.type == .heading1 ? "•" : "◦")
                                .foregroundStyle(PureColors.vibrantOrange)
                            Text(h.content.isEmpty ? "…" : h.content)
                                .font(h.type == .heading1 ? .caption.bold() : .caption)
                                .foregroundStyle(.primary)
                        }
                        .padding(.leading, h.type == .heading2 ? 12 : 0)
                    }
                }
            }
        }
        .padding(12)
        .pureGlassCard(cornerRadius: 14)
    }

    private func prettyMathPreview(_ raw: String) -> String {
        raw
            .replacingOccurrences(of: "\\int", with: "∫")
            .replacingOccurrences(of: "\\sum", with: "∑")
            .replacingOccurrences(of: "\\prod", with: "∏")
            .replacingOccurrences(of: "\\sqrt", with: "√")
            .replacingOccurrences(of: "\\infty", with: "∞")
            .replacingOccurrences(of: "\\alpha", with: "α")
            .replacingOccurrences(of: "\\beta", with: "β")
            .replacingOccurrences(of: "\\gamma", with: "γ")
            .replacingOccurrences(of: "\\theta", with: "θ")
            .replacingOccurrences(of: "\\lambda", with: "λ")
            .replacingOccurrences(of: "\\pi", with: "π")
            .replacingOccurrences(of: "\\sigma", with: "σ")
            .replacingOccurrences(of: "\\omega", with: "ω")
            .replacingOccurrences(of: "\\pm", with: "±")
            .replacingOccurrences(of: "\\to", with: "→")
            .replacingOccurrences(of: "\\partial", with: "∂")
            .replacingOccurrences(of: "\\lim", with: "lim")
    }

    private func formatDuration(_ sec: TimeInterval) -> String {
        let total = max(0, Int(sec))
        return String(format: "%02d:%02d", total / 60, total % 60)
    }
}

struct SandboxedHtmlView: UIViewRepresentable {
    let html: String

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .nonPersistent()
        let wv = WKWebView(frame: .zero, configuration: config)
        wv.loadHTMLString(html, baseURL: nil)
        return wv
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        uiView.loadHTMLString(html, baseURL: nil)
    }
}
