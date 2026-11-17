import PureNoteShared
import SwiftUI

struct NotesHomeView: View {
    @ObservedObject var store: PureNoteStore

    @State private var activeEditorTarget: EditorNavigationTarget? = nil
    @State private var unlockedPayloads: [String: String] = [:]

    @State private var showGlassDrawer = false
    @State private var showFilterSheet = false
    @State private var showTemplatesSheet = false
    @State private var showSyncSheet = false
    @State private var showVaultSheet = false
    @State private var showTrashSheet = false
    @State private var showCategoriesSheet = false
    @State private var showSettingsSheet = false

    @Namespace private var categoryBarNS
    @State private var categoryPressBump = false
    @State private var showCreateGlassMenu = false

    @State private var pendingProtectedNote: IosNoteRecord? = nil
    @State private var showBatchLockPrompt = false
    @State private var batchLockPassphrase = ""

    @Environment(\.colorScheme) private var colorScheme

    struct EditorNavigationTarget: Identifiable, Hashable {
        let note: IosNoteRecord
        let decryptedJson: String
        var initialAction: EditorInitialAction = .none
        var id: String { "\(note.syncUuid)_\(note.syncVersion)_\(initialAction.hashValue)" }
    }

    var body: some View {
        NavigationStack {
            ZStack(alignment: .bottom) {
                liquidBackgroundGradient

                VStack(spacing: 0) {
                    glassTopHeaderBar
                    if store.viewMode == .grid || store.viewMode == .list || store.viewMode == .board {
                        glassCategoryBar
                        activeFilterChipsRow
                    }
                    mainWorkspaceBody
                }

                if store.isMultiSelectMode {
                    multiSelectGlassBar
                        .padding(.horizontal, 16)
                        .padding(.bottom, 14)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                } else if store.viewMode == .grid || store.viewMode == .list || store.viewMode == .board {
                    VStack(spacing: 8) {
                        if store.speechManager.isListening {
                            HomeLiveDictationGlassBanner(
                                speech: store.speechManager,
                                isTurkish: store.isTurkish,
                                onApplySearch: { spoken in
                                    if !spoken.isEmpty { store.searchQuery = spoken }
                                },
                                onCreateNote: { spoken in
                                    guard !spoken.isEmpty else { return }
                                    let created = store.createNote(
                                        title: String(spoken.prefix(40)),
                                        icon: "🗣️",
                                        blocks: [
                                            EditableBlock(
                                                id: SharedIosEditorBridge.shared.doNewUuid(),
                                                type: .paragraph,
                                                content: spoken,
                                                isChecked: false,
                                                calloutIcon: "💡",
                                                mediaPath: nil,
                                                isExpanded: true,
                                                toggleBody: "",
                                                codeLanguage: "kotlin",
                                                tableData: []
                                            )
                                        ]
                                    )
                                    openUnlockedNote(created, decryptedJson: created.richBlocksJson)
                                },
                                onCancel: {
                                    store.speechManager.stopListening()
                                    store.speechManager.partialText = ""
                                }
                            )
                            .transition(.move(edge: .bottom).combined(with: .opacity))
                        }
                        floatingGlassSearchAndComposeDock
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 12)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                }

                if showCreateGlassMenu {
                    glassQuickCreateMenuOverlay
                        .zIndex(40)
                }

                if let toast = store.toastMessage {
                    VStack {
                        HStack(spacing: 8) {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundStyle(PureColors.emeraldGreen)
                            Text(toast)
                                .font(.caption.weight(.semibold))
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .pureGlassCapsule()
                        .padding(.top, 60)
                        Spacer()
                    }
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .zIndex(50)
                }
            }
            .animation(.spring(response: 0.32, dampingFraction: 0.85), value: store.viewMode)
            .animation(.spring(response: 0.3, dampingFraction: 0.85), value: store.isMultiSelectMode)
            .animation(.easeInOut(duration: 0.2), value: store.toastMessage)
            .animation(.spring(response: 0.34, dampingFraction: 0.78), value: showCreateGlassMenu)
            .navigationBarHidden(true)
            .navigationDestination(item: $activeEditorTarget) { target in
                NoteEditorView(
                    store: store,
                    note: target.note,
                    initialDecryptedJson: target.decryptedJson,
                    initialAction: target.initialAction
                ) { linkedNote in
                    handleOpenNote(linkedNote)
                }
            }
            #if DEBUG
            .onAppear {
                let args = ProcessInfo.processInfo.arguments
                if let idx = args.firstIndex(of: "-demoScene"), idx + 1 < args.count {
                    let scene = args[idx + 1]
                    switch scene {
                    case "quickcreate":
                        showCreateGlassMenu = true
                    case "drawer":
                        showGlassDrawer = true
                    case "board":
                        store.viewMode = .board
                    case "graph":
                        store.viewMode = .graph
                    case "whiteboard":
                        store.viewMode = .whiteboard
                    case "calendar":
                        store.viewMode = .calendar
                    case "editor":
                        if let first = store.activeNotes.first {
                            openUnlockedNote(first, decryptedJson: first.richBlocksJson)
                        }
                    default:
                        break
                    }
                }
            }
            #endif
            .sheet(isPresented: $showGlassDrawer) {
                GlassNavigationDrawerSheet(
                    store: store,
                    onSelectMode: { mode in
                        store.viewMode = mode
                        showGlassDrawer = false
                    },
                    onDailyAgenda: {
                        let note = store.createDailyAgendaNote()
                        showGlassDrawer = false
                        openUnlockedNote(note, decryptedJson: note.richBlocksJson)
                    },
                    onOpenTemplates: {
                        showGlassDrawer = false
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) { showTemplatesSheet = true }
                    },
                    onOpenSync: {
                        showGlassDrawer = false
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) { showSyncSheet = true }
                    },
                    onOpenVault: {
                        showGlassDrawer = false
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) { showVaultSheet = true }
                    },
                    onOpenCategories: {
                        showGlassDrawer = false
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) { showCategoriesSheet = true }
                    },
                    onOpenTrash: {
                        showGlassDrawer = false
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) { showTrashSheet = true }
                    },
                    onOpenSettings: {
                        showGlassDrawer = false
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) { showSettingsSheet = true }
                    }
                )
                .presentationBackground(.ultraThinMaterial)
                .presentationDetents([.large])
            }
            .sheet(isPresented: $showFilterSheet) {
                HomeFilterSheet(store: store)
                    .presentationBackground(.ultraThinMaterial)
                    .presentationDetents([.medium, .large])
            }
            .sheet(isPresented: $showTemplatesSheet) {
                TemplateHubSheet(store: store) { tpl in
                    let created = store.createFromTemplate(tpl)
                    openUnlockedNote(created, decryptedJson: created.richBlocksJson)
                }
            }
            .sheet(isPresented: $showSyncSheet) {
                CloudSyncSheet(store: store)
            }
            .sheet(isPresented: $showVaultSheet) {
                VaultSecuritySheet(store: store)
            }
            .sheet(isPresented: $showTrashSheet) {
                TrashBinSheet(store: store)
            }
            .sheet(isPresented: $showCategoriesSheet) {
                CategoryManagementSheet(store: store)
            }
            .sheet(isPresented: $showSettingsSheet) {
                AppSettingsSheet(
                    store: store,
                    onOpenSync: {
                        showSettingsSheet = false
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) { showSyncSheet = true }
                    },
                    onOpenVault: {
                        showSettingsSheet = false
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) { showVaultSheet = true }
                    },
                    onOpenCategories: {
                        showSettingsSheet = false
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) { showCategoriesSheet = true }
                    },
                    onOpenTrash: {
                        showSettingsSheet = false
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) { showTrashSheet = true }
                    }
                )
            }
            .sheet(item: $pendingProtectedNote) { note in
                ProtectedNoteUnlockSheet(store: store, note: note) { decryptedJson in
                    unlockedPayloads[note.syncUuid] = decryptedJson
                    pendingProtectedNote = nil
                    openUnlockedNote(note, decryptedJson: decryptedJson)
                }
                .presentationBackground(.ultraThinMaterial)
                .presentationDetents([.medium])
            }
            .alert(store.isTurkish ? "Seçili Notları Şifrele" : "Encrypt Selected Notes", isPresented: $showBatchLockPrompt) {
                SecureField(store.isTurkish ? "Şifreleme parolası" : "Passphrase", text: $batchLockPassphrase)
                Button(store.isTurkish ? "Kilitle" : "Lock") {
                    if batchLockPassphrase.count >= 4 {
                        store.batchToggleLock(passphrase: batchLockPassphrase)
                    }
                    batchLockPassphrase = ""
                }
                Button(store.isTurkish ? "İptal" : "Cancel", role: .cancel) {
                    batchLockPassphrase = ""
                }
            }
        }
    }

    // MARK: - Ambient Background

    private var liquidBackgroundGradient: some View {
        ZStack {
            (colorScheme == .dark ? PureColors.backgroundDark : PureColors.backgroundLight)
                .ignoresSafeArea()
            Circle()
                .fill(PureColors.vibrantOrange.opacity(colorScheme == .dark ? 0.12 : 0.08))
                .frame(width: 280, height: 280)
                .blur(radius: 70)
                .offset(x: -130, y: -260)
            Circle()
                .fill(PureColors.accentBlue.opacity(colorScheme == .dark ? 0.10 : 0.06))
                .frame(width: 300, height: 300)
                .blur(radius: 80)
                .offset(x: 150, y: 240)
        }
    }

    // MARK: - Top Glass Header

    private var glassTopHeaderBar: some View {
        HStack(spacing: 10) {
            Button {
                showGlassDrawer = true
            } label: {
                Image(systemName: "line.3.horizontal")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(.primary)
                    .frame(width: 38, height: 38)
                    .pureGlassCapsule()
            }

            VStack(alignment: .leading, spacing: 1) {
                Text("PureNoteIt")
                    .font(.system(size: 19, weight: .bold, design: .rounded))
                Text(store.viewMode.title(isTr: store.isTurkish))
                    .font(.caption2.weight(.medium))
                    .foregroundStyle(.secondary)
            }

            Spacer()

            // Drive Sync status pill
            Button {
                if store.isDriveConnected && !store.isSyncing {
                    Task { await store.triggerDriveSync() }
                } else {
                    showSyncSheet = true
                }
            } label: {
                HStack(spacing: 5) {
                    if store.isSyncing {
                        ProgressView()
                            .scaleEffect(0.75)
                    } else {
                        Image(systemName: store.isDriveConnected ? "checkmark.icloud.fill" : "icloud.fill")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(store.isDriveConnected ? PureColors.emeraldGreen : .secondary)
                    }
                    if store.isDriveConnected {
                        Text("Drive")
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(PureColors.emeraldGreen)
                    }
                }
                .padding(.horizontal, 10)
                .frame(height: 36)
                .pureGlassCapsule()
            }

            // 7-Mode View Switcher Menu
            Menu {
                ForEach(PureViewMode.allCases) { mode in
                    Button {
                        store.viewMode = mode
                    } label: {
                        Label(
                            mode.title(isTr: store.isTurkish),
                            systemImage: store.viewMode == mode ? "checkmark" : mode.systemImage
                        )
                    }
                }
            } label: {
                Image(systemName: store.viewMode.systemImage)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(PureColors.vibrantOrange)
                    .frame(width: 38, height: 38)
                    .pureGlassCapsule()
            }

            // Quick Create Liquid Glass Menu Button
            Button {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                withAnimation(.spring(response: 0.36, dampingFraction: 0.72)) {
                    showCreateGlassMenu.toggle()
                }
            } label: {
                Image(systemName: "plus")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(.white)
                    .rotationEffect(.degrees(showCreateGlassMenu ? 45 : 0))
                    .frame(width: 38, height: 38)
                    .background(PureColors.vibrantOrange)
                    .clipShape(Circle())
                    .shadow(color: PureColors.vibrantOrange.opacity(0.35), radius: 6, y: 3)
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .padding(.bottom, 10)
        .background(.ultraThinMaterial.opacity(0.65))
    }

    // MARK: - Elastic Liquid Glass Category Bar

    private var glassCategoryBar: some View {
        ScrollViewReader { scrollProxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    // Frosted translucent Liquid Glass track containing all category tabs
                    HStack(spacing: 4) {
                        elasticGlassCategoryChip(
                            chipId: "CAT_ALL",
                            title: store.isTurkish ? "Tümü" : "All",
                            count: store.activeRootCount(for: nil),
                            color: PureColors.vibrantOrange,
                            isLocked: false,
                            isSelected: store.selectedCategoryUuid == nil
                        ) {
                            UIImpactFeedbackGenerator(style: .light).impactOccurred()
                            withAnimation(.spring(response: 0.44, dampingFraction: 0.56, blendDuration: 0.18)) {
                                store.selectedCategoryUuid = nil
                                categoryPressBump.toggle()
                                scrollProxy.scrollTo("CAT_ALL", anchor: .center)
                            }
                        }
                        .id("CAT_ALL")

                        ForEach(Array(store.sortedCategories.enumerated()), id: \.element.syncUuid) { idx, cat in
                            let cid = cat.syncUuid
                            elasticGlassCategoryChip(
                                chipId: cid,
                                title: cat.name,
                                count: store.activeRootCount(for: cid),
                                color: PureColors.categoryColor(index: idx),
                                isLocked: cat.isPassword,
                                isSelected: store.selectedCategoryUuid == cid
                            ) {
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                withAnimation(.spring(response: 0.44, dampingFraction: 0.56, blendDuration: 0.18)) {
                                    let target = store.selectedCategoryUuid == cid ? nil : cid
                                    store.selectedCategoryUuid = target
                                    categoryPressBump.toggle()
                                    scrollProxy.scrollTo(target ?? "CAT_ALL", anchor: .center)
                                }
                            }
                            .id(cid)
                        }
                    }
                    .padding(5)
                    .background(
                        ZStack {
                            RoundedRectangle(cornerRadius: 26, style: .continuous)
                                .fill(.ultraThinMaterial)
                            RoundedRectangle(cornerRadius: 26, style: .continuous)
                                .fill(
                                    LinearGradient(
                                        colors: [
                                            Color.white.opacity(colorScheme == .dark ? 0.10 : 0.48),
                                            Color.white.opacity(colorScheme == .dark ? 0.02 : 0.16)
                                        ],
                                        startPoint: .top,
                                        endPoint: .bottom
                                    )
                                )
                        }
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 26, style: .continuous)
                            .stroke(
                                LinearGradient(
                                    colors: [
                                        Color.white.opacity(colorScheme == .dark ? 0.22 : 0.80),
                                        Color.white.opacity(colorScheme == .dark ? 0.04 : 0.18)
                                    ],
                                    startPoint: .top,
                                    endPoint: .bottom
                                ),
                                lineWidth: 0.9
                            )
                    )

                    Button {
                        showFilterSheet = true
                    } label: {
                        let hasFilters = store.smartFilter != .all || store.selectedHashtag != nil
                        HStack(spacing: 5) {
                            Image(systemName: "line.3.horizontal.decrease.circle\(hasFilters ? ".fill" : "")")
                                .font(.caption.weight(.semibold))
                            Text(store.isTurkish ? "Filtrele" : "Filter")
                                .font(.caption.weight(.semibold))
                        }
                        .padding(.horizontal, 13)
                        .padding(.vertical, 9)
                        .foregroundStyle(hasFilters ? PureColors.accentBlue : .primary)
                        .background(
                            Capsule().fill(.ultraThinMaterial)
                                .overlay(
                                    Capsule().fill(hasFilters ? PureColors.accentBlue.opacity(0.18) : Color.clear)
                                )
                        )
                        .overlay(
                            Capsule().stroke(
                                hasFilters ? PureColors.accentBlue.opacity(0.45) : Color.white.opacity(colorScheme == .dark ? 0.18 : 0.65),
                                lineWidth: 1
                            )
                        )
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
            }
        }
    }

    private func elasticGlassCategoryChip(
        chipId: String,
        title: String,
        count: Int,
        color: Color,
        isLocked: Bool,
        isSelected: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Circle()
                    .fill(color)
                    .frame(width: 7, height: 7)
                    .shadow(color: isSelected ? color.opacity(0.65) : .clear, radius: 4)
                if isLocked {
                    Image(systemName: "lock.fill")
                        .font(.system(size: 10))
                        .foregroundStyle(isSelected ? color : .secondary)
                }
                Text(title)
                    .font(.caption.weight(isSelected ? .bold : .semibold))
                    .lineLimit(1)
                Text("\(count)")
                    .font(.system(size: 10, weight: .bold).monospacedDigit())
                    .padding(.horizontal, 6)
                    .padding(.vertical, 1.5)
                    .background(isSelected ? color.opacity(0.22) : Color.secondary.opacity(0.14))
                    .foregroundStyle(isSelected ? color : .secondary)
                    .clipShape(Capsule())
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .foregroundStyle(isSelected ? .primary : .secondary)
            .background {
                if isSelected {
                    Capsule()
                        .fill(.thinMaterial)
                        .overlay(
                            Capsule()
                                .fill(
                                    LinearGradient(
                                        colors: [
                                            Color.white.opacity(colorScheme == .dark ? 0.22 : 0.85),
                                            color.opacity(colorScheme == .dark ? 0.18 : 0.12)
                                        ],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    )
                                )
                        )
                        .overlay(
                            Capsule()
                                .stroke(
                                    LinearGradient(
                                        colors: [
                                            Color.white.opacity(colorScheme == .dark ? 0.34 : 0.95),
                                            color.opacity(0.38)
                                        ],
                                        startPoint: .top,
                                        endPoint: .bottom
                                    ),
                                    lineWidth: 1.1
                                )
                        )
                        .matchedGeometryEffect(id: "ACTIVE_CATEGORY_PILL", in: categoryBarNS)
                }
            }
            .scaleEffect(isSelected ? 1.03 : 0.97)
            .rotation3DEffect(.degrees(isSelected ? 0 : 3), axis: (x: 0, y: 1, z: 0), perspective: 0.45)
        }
        .buttonStyle(.plain)
    }

    // MARK: - Active Filter Chips

    @ViewBuilder
    private var activeFilterChipsRow: some View {
        if store.smartFilter != .all || store.selectedHashtag != nil || !store.searchQuery.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    if store.smartFilter != .all {
                        removableChip(icon: store.smartFilter.systemImage, label: store.smartFilter.title(isTr: store.isTurkish), tint: PureColors.vibrantOrange) {
                            store.smartFilter = .all
                        }
                    }
                    if let tag = store.selectedHashtag {
                        removableChip(icon: "number", label: tag, tint: PureColors.accentBlue) {
                            store.selectedHashtag = nil
                        }
                    }
                    if !store.searchQuery.isEmpty {
                        removableChip(icon: "magnifyingglass", label: store.searchQuery, tint: PureColors.emeraldGreen) {
                            store.searchQuery = ""
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 6)
            }
        }
    }

    private func removableChip(icon: String, label: String, tint: Color, onRemove: @escaping () -> Void) -> some View {
        Button(action: onRemove) {
            HStack(spacing: 5) {
                Image(systemName: icon).font(.system(size: 10))
                Text(label).font(.caption2.weight(.semibold))
                Image(systemName: "xmark.circle.fill").font(.system(size: 11))
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(tint.opacity(0.16))
            .foregroundStyle(tint)
            .clipShape(Capsule())
        }
    }

    // MARK: - Workspace Content Switcher

    @ViewBuilder
    private var mainWorkspaceBody: some View {
        switch store.viewMode {
        case .grid:
            gridHomeBody
        case .list:
            listHomeBody
        case .board:
            KanbanBoardView(store: store, onOpenNote: handleOpenNote)
                .padding(.bottom, 68)
        case .calendar:
            CalendarPlannerView(store: store, onOpenNote: handleOpenNote)
        case .graph:
            KnowledgeGraphView(store: store, onOpenNote: handleOpenNote)
        case .whiteboard:
            InfiniteWhiteboardView(store: store, onOpenNote: handleOpenNote)
        case .stream:
            IdeaStreamView(store: store, onOpenNote: handleOpenNote)
        }
    }

    // MARK: - Grid Mode (Recents + Pinned + 2-Column Masonry Cards)

    private var gridHomeBody: some View {
        let notes = store.filteredNotes
        let pinned = notes.filter { $0.isPinned }
        let others = notes.filter { !$0.isPinned }
        let showRecents = store.searchQuery.isEmpty && store.selectedCategoryUuid == nil && store.smartFilter == .all && store.selectedHashtag == nil && !store.recentNotes.isEmpty

        return ScrollView {
            LazyVStack(alignment: .leading, spacing: 18) {
                if showRecents {
                    VStack(alignment: .leading, spacing: 10) {
                        HStack {
                            Label(store.isTurkish ? "Son Düzenlenenler" : "Recents", systemImage: "clock.arrow.circlepath")
                                .font(.caption.weight(.bold))
                                .foregroundStyle(.secondary)
                            Spacer()
                            Button {
                                let agenda = store.createDailyAgendaNote()
                                openUnlockedNote(agenda, decryptedJson: agenda.richBlocksJson)
                            } label: {
                                Label(store.isTurkish ? "Günün Notu" : "Daily Agenda", systemImage: "sun.max.fill")
                                    .font(.caption2.weight(.semibold))
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 5)
                                    .background(PureColors.vibrantOrange.opacity(0.14))
                                    .foregroundStyle(PureColors.vibrantOrange)
                                    .clipShape(Capsule())
                            }
                        }
                        .padding(.horizontal, 16)

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(Array(store.recentNotes.enumerated()), id: \.element.syncUuid) { idx, note in
                                    recentBannerCard(note: note, index: idx)
                                }
                            }
                            .padding(.horizontal, 16)
                        }
                    }
                }

                if !pinned.isEmpty {
                    sectionHeader(title: store.isTurkish ? "Sabitlenenler" : "Pinned", symbol: "pin.fill", tint: PureColors.vibrantOrange)
                    twoColumnGrid(notes: pinned)
                }

                if !others.isEmpty {
                    sectionHeader(title: store.isTurkish ? "Tüm Notlar (\(others.count))" : "Notes (\(others.count))", symbol: "doc.text.fill", tint: .secondary)
                    twoColumnGrid(notes: others)
                } else if notes.isEmpty {
                    emptyNotesPlaceholder
                }
            }
            .padding(.top, 6)
            .padding(.bottom, 96)
        }
    }

    private func recentBannerCard(note: IosNoteRecord, index: Int) -> some View {
        let palette = PureColors.recentBannerPalettes[index % PureColors.recentBannerPalettes.count]
        let isLocked = store.isNoteProtected(note)
        return Button {
            handleOpenNote(note)
        } label: {
            VStack(alignment: .leading, spacing: 0) {
                ZStack(alignment: .bottomLeading) {
                    LinearGradient(colors: [palette.0, palette.1], startPoint: .topLeading, endPoint: .bottomTrailing)
                        .frame(height: 52)
                    ModernNoteIconBadge(iconKey: note.icon, isProtected: isLocked, size: 34, iconSize: 16, cornerRadius: 10)
                        .offset(x: 12, y: 15)
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text(note.title.isEmpty ? (store.isTurkish ? "Başlıksız" : "Untitled") : note.title)
                        .font(.subheadline.weight(.bold))
                        .lineLimit(1)
                        .foregroundStyle(.primary)
                    Text(isLocked ? (store.isTurkish ? "Kilitli Kasa" : "Locked Vault") : store.plainSummary(for: note))
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                .padding(.top, 20)
                .padding(.horizontal, 12)
                .padding(.bottom, 12)
            }
            .frame(width: 176)
            .pureGlassCard(cornerRadius: 18)
        }
        .buttonStyle(.plain)
    }

    private func sectionHeader(title: String, symbol: String, tint: Color) -> some View {
        Label(title, systemImage: symbol)
            .font(.caption.weight(.bold))
            .foregroundStyle(tint)
            .padding(.horizontal, 16)
    }

    private func twoColumnGrid(notes: [IosNoteRecord]) -> some View {
        let pair = splitMasonryColumns(notes)
        return HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 12) {
                ForEach(pair.left) { note in
                    notionGridCard(note: note)
                }
            }
            .frame(maxWidth: .infinity, alignment: .top)

            VStack(alignment: .leading, spacing: 12) {
                ForEach(pair.right) { note in
                    notionGridCard(note: note)
                }
            }
            .frame(maxWidth: .infinity, alignment: .top)
        }
        .padding(.horizontal, 16)
    }

    private func splitMasonryColumns(_ notes: [IosNoteRecord]) -> (left: [IosNoteRecord], right: [IosNoteRecord]) {
        var left: [IosNoteRecord] = []
        var right: [IosNoteRecord] = []
        var leftWeight = 0
        var rightWeight = 0
        for note in notes {
            let weight = 80
                + min(90, note.title.count * 2)
                + (note.totalTasks > 0 ? 45 : 0)
                + min(90, store.plainSummary(for: note).count)
            if leftWeight <= rightWeight {
                left.append(note)
                leftWeight += weight
            } else {
                right.append(note)
                rightWeight += weight
            }
        }
        return (left, right)
    }

    private func notionGridCard(note: IosNoteRecord) -> some View {
        let isLocked = store.isNoteProtected(note)
        let isSelected = store.selectedNoteUuids.contains(note.syncUuid)
        let tags = isLocked ? [] : Array(SharedIosEditorBridge.shared.extractHashtags(text: "\(note.title) \(store.plainSummary(for: note))").prefix(3))
        let catName = store.categories.first(where: { $0.syncUuid == note.categorySyncUuid })?.name

        return VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                ModernNoteIconBadge(iconKey: note.icon, isProtected: isLocked, size: 30, iconSize: 14, cornerRadius: 9)
                Spacer()
                if store.isMultiSelectMode {
                    Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                        .font(.title3)
                        .foregroundStyle(isSelected ? PureColors.vibrantOrange : .secondary)
                } else {
                    if note.isPinned {
                        Image(systemName: "pin.fill")
                            .font(.caption2)
                            .foregroundStyle(PureColors.vibrantOrange)
                    }
                    if note.reminderTime != nil {
                        Image(systemName: "alarm.fill")
                            .font(.caption2)
                            .foregroundStyle(PureColors.accentBlue)
                    }
                }
            }

            Text(note.title.isEmpty ? (store.isTurkish ? "Başlıksız" : "Untitled") : note.title)
                .font(.subheadline.weight(.bold))
                .lineLimit(2)
                .multilineTextAlignment(.leading)
                .foregroundStyle(.primary)

            Text(isLocked ? (store.isTurkish ? "🔒 AES-256 Şifreli" : "🔒 AES-256 Encrypted") : store.plainSummary(for: note))
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(3)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)

            if note.totalTasks > 0 && !isLocked {
                VStack(alignment: .leading, spacing: 3) {
                    ProgressView(value: Double(note.completedTasks), total: Double(max(1, note.totalTasks)))
                        .tint(note.completedTasks == note.totalTasks ? PureColors.emeraldGreen : PureColors.vibrantOrange)
                    Text("\(note.completedTasks)/\(note.totalTasks) \(store.isTurkish ? "görev" : "tasks")")
                        .font(.system(size: 10, weight: .semibold).monospacedDigit())
                        .foregroundStyle(.secondary)
                }
            }

            if !tags.isEmpty {
                HStack(spacing: 4) {
                    ForEach(tags, id: \.self) { t in
                        Text("#\(t)")
                            .font(.system(size: 9.5, weight: .semibold))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(PureColors.accentBlue.opacity(0.14))
                            .foregroundStyle(PureColors.accentBlue)
                            .clipShape(Capsule())
                            .lineLimit(1)
                    }
                }
            }

            HStack(spacing: 6) {
                if let catName {
                    Text(catName)
                        .font(.system(size: 9.5, weight: .semibold))
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Color.secondary.opacity(0.14))
                        .clipShape(Capsule())
                        .lineLimit(1)
                }
                Spacer()
                if !isLocked {
                    if !note.images.isEmpty {
                        Label("\(note.images.count)", systemImage: "photo.fill")
                            .font(.system(size: 10, weight: .semibold))
                            .foregroundStyle(PureColors.accentBlue)
                    }
                    if !note.sounds.isEmpty {
                        Label("\(note.sounds.count)", systemImage: "waveform")
                            .font(.system(size: 10, weight: .semibold))
                            .foregroundStyle(PureColors.emeraldGreen)
                    }
                }
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .pureGlassCard(cornerRadius: 18)
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(isSelected ? PureColors.vibrantOrange : Color.clear, lineWidth: 2)
        )
        .onTapGesture {
            if store.isMultiSelectMode {
                toggleSelection(note.syncUuid)
            } else {
                handleOpenNote(note)
            }
        }
        .onLongPressGesture {
            if !store.isMultiSelectMode {
                store.isMultiSelectMode = true
                store.selectedNoteUuids.insert(note.syncUuid)
            }
        }
        .contextMenu {
            noteContextMenu(note: note)
        }
    }

    // MARK: - List Mode

    private var listHomeBody: some View {
        let notes = store.filteredNotes
        return Group {
            if notes.isEmpty {
                emptyNotesPlaceholder
            } else {
                List {
                    ForEach(notes) { note in
                        let isLocked = store.isNoteProtected(note)
                        let isSelected = store.selectedNoteUuids.contains(note.syncUuid)
                        HStack(spacing: 12) {
                            ModernNoteIconBadge(iconKey: note.icon, isProtected: isLocked, size: 36, iconSize: 16, cornerRadius: 10)
                            VStack(alignment: .leading, spacing: 4) {
                                HStack(spacing: 6) {
                                    Text(note.title.isEmpty ? (store.isTurkish ? "Başlıksız" : "Untitled") : note.title)
                                        .font(.subheadline.weight(.bold))
                                        .lineLimit(1)
                                    if note.isPinned {
                                        Image(systemName: "pin.fill")
                                            .font(.caption2)
                                            .foregroundStyle(PureColors.vibrantOrange)
                                    }
                                    Spacer()
                                    Text(note.status.replacingOccurrences(of: "_", with: " "))
                                        .font(.system(size: 9.5, weight: .bold))
                                        .padding(.horizontal, 7)
                                        .padding(.vertical, 3)
                                        .background(PureColors.vibrantOrange.opacity(0.14))
                                        .foregroundStyle(PureColors.vibrantOrange)
                                        .clipShape(Capsule())
                                }
                                Text(isLocked ? (store.isTurkish ? "🔒 Şifreli Kasa Notu" : "🔒 Encrypted Vault Note") : store.plainSummary(for: note))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(2)
                            }
                            if store.isMultiSelectMode {
                                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                                    .foregroundStyle(isSelected ? PureColors.vibrantOrange : .secondary)
                            }
                        }
                        .padding(12)
                        .pureGlassCard(cornerRadius: 16)
                        .listRowInsets(EdgeInsets(top: 5, leading: 16, bottom: 5, trailing: 16))
                        .listRowSeparator(.hidden)
                        .listRowBackground(Color.clear)
                        .onTapGesture {
                            if store.isMultiSelectMode {
                                toggleSelection(note.syncUuid)
                            } else {
                                handleOpenNote(note)
                            }
                        }
                        .onLongPressGesture {
                            if !store.isMultiSelectMode {
                                store.isMultiSelectMode = true
                                store.selectedNoteUuids.insert(note.syncUuid)
                            }
                        }
                        .contextMenu {
                            noteContextMenu(note: note)
                        }
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
                .padding(.bottom, 76)
            }
        }
    }

    @ViewBuilder
    private func noteContextMenu(note: IosNoteRecord) -> some View {
        Button {
            store.togglePin(note)
        } label: {
            Label(note.isPinned ? (store.isTurkish ? "Sabitlemeyi Kaldır" : "Unpin") : (store.isTurkish ? "Sabitle" : "Pin"), systemImage: note.isPinned ? "pin.slash" : "pin")
        }

        Menu {
            Button("To Do") { store.updateNoteStatus(note, status: "TODO") }
            Button("In Progress") { store.updateNoteStatus(note, status: "IN_PROGRESS") }
            Button("In Review") { store.updateNoteStatus(note, status: "IN_REVIEW") }
            Button("Done") { store.updateNoteStatus(note, status: "DONE") }
        } label: {
            Label(store.isTurkish ? "Kanban Durumu" : "Kanban Status", systemImage: "rectangle.split.3x1")
        }

        Button {
            store.isMultiSelectMode = true
            store.selectedNoteUuids.insert(note.syncUuid)
        } label: {
            Label(store.isTurkish ? "Çoklu Seçim" : "Select Multiple", systemImage: "checkmark.circle")
        }

        Button(role: .destructive) {
            store.moveToTrash(note)
        } label: {
            Label(store.isTurkish ? "Çöp Kutusuna Taşı" : "Move to Trash", systemImage: "trash")
        }
    }

    private var emptyNotesPlaceholder: some View {
        VStack(spacing: 12) {
            Spacer(minLength: 48)
            Image(systemName: "doc.richtext")
                .font(.system(size: 42))
                .foregroundStyle(PureColors.vibrantOrange.opacity(0.75))
            Text(store.isTurkish ? "Bu görünümde not yok" : "No notes match filters")
                .font(.headline)
            Text(store.isTurkish ? "Yeni not ekleyin veya hazır şablonlardan birini seçin." : "Create a note or start from one of 14 built-in templates.")
                .font(.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 36)
            Button {
                showTemplatesSheet = true
            } label: {
                Label(store.isTurkish ? "Şablon Merkezini Aç" : "Browse Templates", systemImage: "rectangle.stack.badge.plus")
                    .font(.subheadline.weight(.semibold))
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(PureColors.vibrantOrange)
                    .foregroundStyle(.white)
                    .clipShape(Capsule())
            }
            Spacer(minLength: 48)
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: - Multi-Select Bottom Glass Bar

    private var multiSelectGlassBar: some View {
        HStack(spacing: 12) {
            Text("\(store.selectedNoteUuids.count) \(store.isTurkish ? "seçili" : "selected")")
                .font(.subheadline.weight(.bold))

            Spacer()

            Button {
                showBatchLockPrompt = true
            } label: {
                Label(store.isTurkish ? "Kilitle" : "Lock", systemImage: "lock.fill")
                    .font(.caption.weight(.semibold))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(PureColors.vibrantOrange.opacity(0.18))
                    .foregroundStyle(PureColors.vibrantOrange)
                    .clipShape(Capsule())
            }

            Button {
                store.batchMoveToTrash()
            } label: {
                Label(store.isTurkish ? "Çöp" : "Trash", systemImage: "trash.fill")
                    .font(.caption.weight(.semibold))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(Color.red.opacity(0.18))
                    .foregroundStyle(.red)
                    .clipShape(Capsule())
            }

            Button {
                store.selectedNoteUuids.removeAll()
                store.isMultiSelectMode = false
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.title3)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 11)
        .pureGlassCapsule()
    }

    // MARK: - Floating Liquid Glass Search + Voice + Compose Dock

    private var floatingGlassSearchAndComposeDock: some View {
        HStack(spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(.secondary)
                TextField(
                    store.isTurkish ? "Notlarda veya içerikte ara..." : "Search titles, #tags, blocks...",
                    text: $store.searchQuery
                )
                .font(.subheadline)
                .textFieldStyle(.plain)

                if !store.searchQuery.isEmpty {
                    Button {
                        store.searchQuery = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .padding(.horizontal, 14)
            .frame(height: 44)
            .pureGlassCapsule()

            Button {
                store.speechManager.toggleListening(isTurkish: store.isTurkish) { spoken in
                    if !spoken.isEmpty {
                        store.searchQuery = spoken
                    }
                }
            } label: {
                Image(systemName: store.speechManager.isListening ? "mic.circle.fill" : "mic.fill")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(store.speechManager.isListening ? .red : .purple)
                    .frame(width: 44, height: 44)
                    .pureGlassCapsule()
            }

            Button {
                showTemplatesSheet = true
            } label: {
                Image(systemName: "square.grid.2x2")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(PureColors.accentBlue)
                    .frame(width: 44, height: 44)
                    .pureGlassCapsule()
            }

            Button {
                let created = store.createNote(title: "", icon: "📄")
                openUnlockedNote(created, decryptedJson: created.richBlocksJson)
            } label: {
                Image(systemName: "square.and.pencil")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 46, height: 46)
                    .background(PureColors.vibrantOrange)
                    .clipShape(Circle())
                    .shadow(color: PureColors.vibrantOrange.opacity(0.4), radius: 8, y: 4)
            }
        }
    }

    // MARK: - Navigation & Unlock Gate

    private func toggleSelection(_ uuid: String) {
        if store.selectedNoteUuids.contains(uuid) {
            store.selectedNoteUuids.remove(uuid)
            if store.selectedNoteUuids.isEmpty { store.isMultiSelectMode = false }
        } else {
            store.selectedNoteUuids.insert(uuid)
        }
    }

    private func handleOpenNote(_ note: IosNoteRecord) {
        store.securityManager.recordActivity()
        let isProtected = store.isNoteProtected(note)

        if let cached = unlockedPayloads[note.syncUuid], (!isProtected || store.securityManager.isVaultUnlocked) {
            openUnlockedNote(note, decryptedJson: cached)
            return
        }

        if note.isEncrypted {
            pendingProtectedNote = note
            return
        }

        if isProtected && store.securityManager.isPinConfigured && !store.securityManager.isVaultUnlocked {
            pendingProtectedNote = note
            return
        }

        openUnlockedNote(note, decryptedJson: note.richBlocksJson)
    }

    private func openUnlockedNote(
        _ note: IosNoteRecord,
        decryptedJson: String,
        initialAction: EditorInitialAction = .none
    ) {
        activeEditorTarget = EditorNavigationTarget(
            note: note,
            decryptedJson: decryptedJson,
            initialAction: initialAction
        )
    }

    // MARK: - Expanded Liquid Glass Quick-Create Overlay Menu

    private var glassQuickCreateMenuOverlay: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.opacity(0.32)
                .ignoresSafeArea()
                .background(.ultraThinMaterial.opacity(0.45))
                .onTapGesture {
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) {
                        showCreateGlassMenu = false
                    }
                }

            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Label(store.isTurkish ? "Hızlı Oluştur" : "Quick Create", systemImage: "sparkles")
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(PureColors.vibrantOrange)
                    Spacer()
                    Button {
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) {
                            showCreateGlassMenu = false
                        }
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.title3)
                            .foregroundStyle(.secondary)
                    }
                }

                let cols = [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)]
                LazyVGrid(columns: cols, spacing: 10) {
                    quickCreateTile("square.and.pencil", title: store.isTurkish ? "Boş Not" : "Blank Note", tint: PureColors.vibrantOrange) {
                        let created = store.createNote(title: "", icon: "📄")
                        openUnlockedNote(created, decryptedJson: created.richBlocksJson)
                    }
                    quickCreateTile("sun.max.fill", title: store.isTurkish ? "Günün Notu" : "Daily Agenda", tint: .orange) {
                        let agenda = store.createDailyAgendaNote()
                        openUnlockedNote(agenda, decryptedJson: agenda.richBlocksJson)
                    }
                    quickCreateTile("mic.fill", title: store.isTurkish ? "Sesli Dikte" : "Voice Dictate", tint: .purple) {
                        let created = store.createNote(title: store.isTurkish ? "Sesli Not" : "Voice Note", icon: "🗣️")
                        openUnlockedNote(created, decryptedJson: created.richBlocksJson, initialAction: .startDictation)
                    }
                    quickCreateTile("waveform", title: store.isTurkish ? "Ses Kaydı" : "Audio Record", tint: PureColors.emeraldGreen) {
                        let created = store.createNote(title: store.isTurkish ? "Ses Kaydı" : "Audio Recording", icon: "🗣️")
                        openUnlockedNote(created, decryptedJson: created.richBlocksJson, initialAction: .startAudioRecording)
                    }
                    quickCreateTile("paintbrush.pointed.fill", title: store.isTurkish ? "Çizim / Eskiz" : "Sketch / Draw", tint: Color(red: 0.54, green: 0.36, blue: 0.96)) {
                        let created = store.createNote(title: store.isTurkish ? "Çizim Notu" : "Sketch", icon: "🎨")
                        openUnlockedNote(created, decryptedJson: created.richBlocksJson, initialAction: .openDrawing)
                    }
                    quickCreateTile("doc.text.viewfinder", title: store.isTurkish ? "Belge Tara (OCR)" : "Scan OCR", tint: PureColors.accentBlue) {
                        let created = store.createNote(title: store.isTurkish ? "Tarama Notu" : "OCR Scan", icon: "📄")
                        openUnlockedNote(created, decryptedJson: created.richBlocksJson, initialAction: .openCameraOcr)
                    }
                    quickCreateTile("bolt.fill", title: store.isTurkish ? "Fikir Akışı" : "Idea Stream", tint: .yellow) {
                        store.viewMode = .stream
                    }
                    quickCreateTile("checklist", title: store.isTurkish ? "Görev Listesi" : "Checklist", tint: PureColors.vibrantOrange) {
                        let todoBlocks = [
                            EditableBlock(id: SharedIosEditorBridge.shared.doNewUuid(), type: .heading2, content: store.isTurkish ? "Yapılacaklar" : "To-Do List", isChecked: false, calloutIcon: "💡", mediaPath: nil, isExpanded: true, toggleBody: "", codeLanguage: "kotlin", tableData: []),
                            EditableBlock(id: SharedIosEditorBridge.shared.doNewUuid(), type: .todo, content: store.isTurkish ? "İlk görevi yaz..." : "First task...", isChecked: false, calloutIcon: "💡", mediaPath: nil, isExpanded: true, toggleBody: "", codeLanguage: "kotlin", tableData: []),
                            EditableBlock(id: SharedIosEditorBridge.shared.doNewUuid(), type: .todo, content: "", isChecked: false, calloutIcon: "💡", mediaPath: nil, isExpanded: true, toggleBody: "", codeLanguage: "kotlin", tableData: [])
                        ]
                        let created = store.createNote(title: store.isTurkish ? "Görev Listem" : "Task List", icon: "🎯", blocks: todoBlocks)
                        openUnlockedNote(created, decryptedJson: created.richBlocksJson)
                    }
                    quickCreateTile("tablecells.fill", title: store.isTurkish ? "Bütçe Tablosu" : "Budget Table", tint: Color(red: 0.0, green: 0.51, blue: 0.56)) {
                        let tblBlock = EditableBlock(
                            id: SharedIosEditorBridge.shared.doNewUuid(),
                            type: .table,
                            content: "",
                            isChecked: false,
                            calloutIcon: "💡",
                            mediaPath: nil,
                            isExpanded: true,
                            toggleBody: "",
                            codeLanguage: "kotlin",
                            tableData: [[store.isTurkish ? "Kalem" : "Item", store.isTurkish ? "Tutar" : "Amount"], ["", "0"]]
                        )
                        let created = store.createNote(title: store.isTurkish ? "Bütçe & Tablo" : "Budget Table", icon: "📊", blocks: [tblBlock])
                        openUnlockedNote(created, decryptedJson: created.richBlocksJson)
                    }
                    quickCreateTile("chevron.left.forwardslash.chevron.right", title: store.isTurkish ? "Kod & Matematik" : "Code & Math", tint: PureColors.accentBlue) {
                        let devBlocks = [
                            EditableBlock(id: SharedIosEditorBridge.shared.doNewUuid(), type: .code, content: "console.log(\"Merhaba PureNoteIt!\");", isChecked: false, calloutIcon: "💡", mediaPath: nil, isExpanded: true, toggleBody: "", codeLanguage: "javascript", tableData: []),
                            EditableBlock(id: SharedIosEditorBridge.shared.doNewUuid(), type: .math, content: "e^{i\\pi} + 1 = 0", isChecked: false, calloutIcon: "💡", mediaPath: nil, isExpanded: true, toggleBody: "", codeLanguage: "kotlin", tableData: [])
                        ]
                        let created = store.createNote(title: store.isTurkish ? "Kod & Formül Notu" : "Code & Math Note", icon: "💻", blocks: devBlocks)
                        openUnlockedNote(created, decryptedJson: created.richBlocksJson)
                    }
                    quickCreateTile("briefcase.fill", title: store.isTurkish ? "Toplantı Notu" : "Meeting Notes", tint: Color(red: 0.42, green: 0.29, blue: 0.24)) {
                        if let tpl = NoteTemplatesCatalog.all.first(where: { $0.id == "meeting" }) {
                            let created = store.createFromTemplate(tpl)
                            openUnlockedNote(created, decryptedJson: created.richBlocksJson)
                        }
                    }
                    quickCreateTile("rectangle.stack.badge.plus", title: store.isTurkish ? "14 Hazır Şablon" : "14 Templates", tint: .pink) {
                        showTemplatesSheet = true
                    }
                }
            }
            .padding(16)
            .pureGlassCard(cornerRadius: 24)
            .padding(.horizontal, 16)
            .padding(.top, 58)
            .transition(.scale(scale: 0.88, anchor: .topTrailing).combined(with: .opacity))
        }
    }

    private func quickCreateTile(_ symbol: String, title: String, tint: Color, action: @escaping () -> Void) -> some View {
        Button {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            showCreateGlassMenu = false
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.08) {
                action()
            }
        } label: {
            HStack(spacing: 10) {
                Image(systemName: symbol)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(tint)
                    .frame(width: 30, height: 30)
                    .background(tint.opacity(0.16))
                    .clipShape(RoundedRectangle(cornerRadius: 9, style: .continuous))
                Text(title)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 9)
            .background(Color.white.opacity(colorScheme == .dark ? 0.07 : 0.45))
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(Color.white.opacity(colorScheme == .dark ? 0.14 : 0.70), lineWidth: 0.8)
            )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Home Floating Live Dictation Banner

private struct HomeLiveDictationGlassBanner: View {
    @ObservedObject var speech: IosSpeechDictationManager
    let isTurkish: Bool
    let onApplySearch: (String) -> Void
    let onCreateNote: (String) -> Void
    let onCancel: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "waveform.and.mic")
                    .foregroundStyle(.purple)
                Text(isTurkish ? "Canlı Sesli Dikte" : "Live Dictation")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.purple)
                LiquidAudioWaveformView(
                    isActive: speech.isListening,
                    level: speech.audioLevel,
                    tint: .purple,
                    height: 16
                )
                .frame(maxWidth: .infinity)
                Button(isTurkish ? "İptal" : "Cancel", action: onCancel)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
            }

            Text(
                speech.partialText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                    ? (isTurkish ? "Konuşmaya başlayın, kelimeler anlık görünecek..." : "Start speaking...")
                    : speech.partialText
            )
            .font(.subheadline.weight(.medium))
            .foregroundStyle(speech.partialText.isEmpty ? .secondary : .primary)
            .frame(maxWidth: .infinity, alignment: .leading)

            HStack(spacing: 8) {
                Button {
                    let txt = speech.partialText.trimmingCharacters(in: .whitespacesAndNewlines)
                    speech.stopListening()
                    speech.partialText = ""
                    onApplySearch(txt)
                } label: {
                    Label(isTurkish ? "Ara" : "Search", systemImage: "magnifyingglass")
                        .font(.caption.weight(.bold))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(PureColors.accentBlue.opacity(0.18))
                        .foregroundStyle(PureColors.accentBlue)
                        .clipShape(Capsule())
                }
                .disabled(speech.partialText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)

                Button {
                    let txt = speech.partialText.trimmingCharacters(in: .whitespacesAndNewlines)
                    speech.stopListening()
                    speech.partialText = ""
                    onCreateNote(txt)
                } label: {
                    Label(isTurkish ? "Not Oluştur" : "Create Note", systemImage: "square.and.pencil")
                        .font(.caption.weight(.bold))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(PureColors.vibrantOrange)
                        .foregroundStyle(.white)
                        .clipShape(Capsule())
                }
                .disabled(speech.partialText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
        }
        .padding(12)
        .pureGlassCard(cornerRadius: 18)
    }
}

// MARK: - Liquid Glass Navigation Drawer Sheet

private struct GlassNavigationDrawerSheet: View {
    @ObservedObject var store: PureNoteStore
    let onSelectMode: (PureViewMode) -> Void
    let onDailyAgenda: () -> Void
    let onOpenTemplates: () -> Void
    let onOpenSync: () -> Void
    let onOpenVault: () -> Void
    let onOpenCategories: () -> Void
    let onOpenTrash: () -> Void
    let onOpenSettings: () -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    // Glass Hero Header
                    HStack(spacing: 14) {
                        ModernNoteIconBadge(iconKey: "💎", isProtected: false, size: 50, iconSize: 24, cornerRadius: 14)
                        VStack(alignment: .leading, spacing: 3) {
                            Text("PureNoteIt")
                                .font(.title2.weight(.bold))
                            Text(store.isTurkish ? "Çevrimdışı Öncelikli Şifreli Çalışma Alanı" : "Offline-First Encrypted Workspace")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                    }
                    .padding(14)
                    .pureGlassCard(cornerRadius: 20)

                    // Stats Strip
                    HStack(spacing: 10) {
                        statCard(value: "\(store.activeNotes.count)", label: store.isTurkish ? "Not" : "Notes", tint: PureColors.vibrantOrange)
                        statCard(value: "\(store.categories.count)", label: store.isTurkish ? "Kategori" : "Folders", tint: PureColors.accentBlue)
                        statCard(value: "\(store.streamItems.filter { !$0.isProcessed }.count)", label: store.isTurkish ? "Fikir" : "Stream", tint: .purple)
                        statCard(value: "\(store.trashedNotes.count)", label: store.isTurkish ? "Çöp" : "Trash", tint: .secondary)
                    }

                    // 7 Workspace View Modes
                    Text(store.isTurkish ? "Görünümler" : "Workspaces")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 4)

                    VStack(spacing: 6) {
                        ForEach(PureViewMode.allCases) { mode in
                            let selected = store.viewMode == mode
                            Button {
                                onSelectMode(mode)
                            } label: {
                                HStack(spacing: 12) {
                                    Image(systemName: mode.systemImage)
                                        .font(.system(size: 16, weight: .semibold))
                                        .foregroundStyle(selected ? .white : PureColors.vibrantOrange)
                                        .frame(width: 30, height: 30)
                                        .background(selected ? PureColors.vibrantOrange : PureColors.vibrantOrange.opacity(0.14))
                                        .clipShape(RoundedRectangle(cornerRadius: 8))
                                    Text(mode.title(isTr: store.isTurkish))
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundStyle(.primary)
                                    Spacer()
                                    if selected {
                                        Image(systemName: "checkmark.circle.fill")
                                            .foregroundStyle(PureColors.vibrantOrange)
                                    }
                                }
                                .padding(.horizontal, 12)
                                .padding(.vertical, 9)
                                .background(selected ? PureColors.vibrantOrange.opacity(0.12) : Color.clear)
                                .clipShape(RoundedRectangle(cornerRadius: 14))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(10)
                    .pureGlassCard(cornerRadius: 20)

                    // Tools & Settings
                    Text(store.isTurkish ? "Araçlar & Kasa" : "Tools & Vault")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 4)

                    VStack(spacing: 4) {
                        drawerActionRow(icon: "sun.max.fill", title: store.isTurkish ? "Günün Notu (Ajanda)" : "Daily Agenda Note", tint: PureColors.vibrantOrange, action: onDailyAgenda)
                        drawerActionRow(icon: "rectangle.stack.badge.plus", title: store.isTurkish ? "Şablon Merkezi (14)" : "Template Hub (14)", tint: PureColors.accentBlue, action: onOpenTemplates)
                        drawerActionRow(
                            icon: store.isDriveConnected ? "checkmark.icloud.fill" : "icloud.and.arrow.up.fill",
                            title: store.isTurkish ? "Google Drive E2EE Eşitleme" : "Google Drive E2EE Sync",
                            tint: store.isDriveConnected ? PureColors.emeraldGreen : PureColors.accentBlue,
                            badge: store.isDriveConnected ? (store.isTurkish ? "Bağlı" : "Connected") : nil,
                            action: onOpenSync
                        )
                        drawerActionRow(icon: "lock.shield.fill", title: store.isTurkish ? "Kasa Güvenliği & Yedek" : "Vault PIN & Backup", tint: .purple, action: onOpenVault)
                        drawerActionRow(icon: "folder.fill", title: store.isTurkish ? "Kategorileri Yönet" : "Manage Categories", tint: PureColors.emeraldGreen, action: onOpenCategories)
                        drawerActionRow(
                            icon: "trash.fill",
                            title: store.isTurkish ? "Çöp Kutusu" : "Trash Bin",
                            tint: .red,
                            badge: store.trashedNotes.isEmpty ? nil : "\(store.trashedNotes.count)",
                            action: onOpenTrash
                        )
                        drawerActionRow(icon: "gearshape.fill", title: store.isTurkish ? "Ayarlar (Tema & Dil)" : "Settings (Theme & Language)", tint: .secondary, action: onOpenSettings)
                    }
                    .padding(10)
                    .pureGlassCard(cornerRadius: 20)
                }
                .padding(16)
            }
            .navigationTitle(store.isTurkish ? "Menü" : "Menu")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(store.isTurkish ? "Kapat" : "Close") { dismiss() }
                }
            }
        }
    }

    private func statCard(value: String, label: String, tint: Color) -> some View {
        VStack(spacing: 3) {
            Text(value)
                .font(.headline.weight(.bold).monospacedDigit())
                .foregroundStyle(tint)
            Text(label)
                .font(.caption2.weight(.semibold))
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .pureGlassCard(cornerRadius: 14)
    }

    private func drawerActionRow(
        icon: String,
        title: String,
        tint: Color,
        badge: String? = nil,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(tint)
                    .frame(width: 28, height: 28)
                    .background(tint.opacity(0.14))
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                Text(title)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.primary)
                Spacer()
                if let badge {
                    Text(badge)
                        .font(.caption2.weight(.bold))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(tint.opacity(0.16))
                        .foregroundStyle(tint)
                        .clipShape(Capsule())
                }
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.tertiary)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 9)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Filter Bottom Sheet (8 Smart Filters + Categories + #Hashtags)

private struct HomeFilterSheet: View {
    @ObservedObject var store: PureNoteStore
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Text(store.isTurkish ? "Akıllı Filtreler" : "Smart Filters")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.secondary)

                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                        ForEach(PureSmartFilter.allCases) { f in
                            let selected = store.smartFilter == f
                            Button {
                                store.smartFilter = f
                            } label: {
                                HStack(spacing: 8) {
                                    Image(systemName: f.systemImage)
                                        .foregroundStyle(selected ? .white : PureColors.vibrantOrange)
                                    Text(f.title(isTr: store.isTurkish))
                                        .font(.caption.weight(.semibold))
                                        .lineLimit(1)
                                    Spacer()
                                }
                                .padding(.horizontal, 12)
                                .padding(.vertical, 10)
                                .background(selected ? PureColors.vibrantOrange : Color.secondary.opacity(0.12))
                                .foregroundStyle(selected ? .white : .primary)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    if !store.allHashtags.isEmpty {
                        Text(store.isTurkish ? "Etiketler (#Hashtag)" : "Hashtags")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(.secondary)

                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 95), spacing: 8)], spacing: 8) {
                            ForEach(store.allHashtags, id: \.self) { tag in
                                let selected = store.selectedHashtag == tag
                                Button {
                                    store.selectedHashtag = selected ? nil : tag
                                } label: {
                                    Text("#\(tag)")
                                        .font(.caption.weight(.semibold))
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 7)
                                        .frame(maxWidth: .infinity)
                                        .background(selected ? PureColors.accentBlue : PureColors.accentBlue.opacity(0.14))
                                        .foregroundStyle(selected ? .white : PureColors.accentBlue)
                                        .clipShape(Capsule())
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
                .padding(16)
            }
            .navigationTitle(store.isTurkish ? "Filtrele" : "Filter Notes")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(store.isTurkish ? "Sıfırla" : "Reset") {
                        store.smartFilter = .all
                        store.selectedCategoryUuid = nil
                        store.selectedHashtag = nil
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(store.isTurkish ? "Uygula" : "Done") { dismiss() }
                }
            }
        }
    }
}

// MARK: - Protected Note Unlock Sheet

private struct ProtectedNoteUnlockSheet: View {
    @ObservedObject var store: PureNoteStore
    let note: IosNoteRecord
    let onUnlocked: (String) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var input = ""
    @State private var error: String? = nil

    var body: some View {
        NavigationStack {
            Form {
                Section(note.isEncrypted
                        ? (store.isTurkish ? "AES-256-GCM Parola Doğrulaması" : "Decrypt AES-256 Note")
                        : (store.isTurkish ? "Kasa PIN Doğrulaması" : "Vault PIN Required")) {
                    SecureField(
                        note.isEncrypted
                            ? (store.isTurkish ? "Not şifreleme parolası" : "Note passphrase")
                            : (store.isTurkish ? "6 haneli Master PIN" : "6-digit Master PIN"),
                        text: $input
                    )

                    if let error {
                        Text(error)
                            .font(.caption)
                            .foregroundStyle(.red)
                    }

                    Button(store.isTurkish ? "Kilidi Aç" : "Unlock") {
                        attemptUnlock()
                    }
                    .fontWeight(.semibold)

                    if !note.isEncrypted && store.securityManager.isBiometricEnabled && store.securityManager.canUseBiometrics() {
                        Button {
                            Task {
                                let res = await store.securityManager.unlockWithBiometrics(
                                    reason: store.isTurkish ? "Korumalı notu açın" : "Unlock protected note"
                                )
                                if res.0 {
                                    onUnlocked(note.richBlocksJson)
                                } else {
                                    error = res.1
                                }
                            }
                        } label: {
                            Label("Face ID / Touch ID", systemImage: "faceid")
                        }
                    }
                }
            }
            .navigationTitle(note.title.isEmpty ? (store.isTurkish ? "Korumalı Not" : "Protected Note") : note.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(store.isTurkish ? "İptal" : "Cancel") { dismiss() }
                }
            }
        }
    }

    private func attemptUnlock() {
        if note.isEncrypted {
            if let plain = store.decryptNote(note, passphrase: input) {
                onUnlocked(plain)
            } else {
                error = store.isTurkish ? "Parola hatalı veya veri çözülemedi." : "Invalid passphrase."
            }
        } else {
            let res = store.securityManager.unlockWithPin(input)
            if res.0 {
                onUnlocked(note.richBlocksJson)
            } else {
                error = res.1
            }
        }
    }
}
