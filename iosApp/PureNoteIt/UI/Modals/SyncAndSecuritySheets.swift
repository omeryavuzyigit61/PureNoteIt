import SwiftUI
import UniformTypeIdentifiers

// MARK: - Cloud Sync Sheet (Google Drive E2EE + Cross-Device Setup)

struct CloudSyncSheet: View {
    @ObservedObject var store: PureNoteStore
    @Environment(\.dismiss) private var dismiss

    @State private var clientIdInput: String = ""
    @State private var customPassphrase: String = ""
    @State private var showAdvancedPublisher: Bool = false
    @State private var showPassphraseOptions: Bool = false

    private var hasConfiguredClient: Bool {
        !store.resolvedIosOAuthClientId().isEmpty
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    HStack(spacing: 12) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill((store.isDriveConnected ? PureColors.emeraldGreen : PureColors.vibrantOrange).opacity(0.15))
                            Image(systemName: store.isDriveConnected ? "checkmark.icloud.fill" : "icloud.fill")
                                .font(.title2)
                                .foregroundStyle(store.isDriveConnected ? PureColors.emeraldGreen : PureColors.vibrantOrange)
                        }
                        .frame(width: 44, height: 44)

                        VStack(alignment: .leading, spacing: 3) {
                            Text(store.isDriveConnected
                                 ? (store.isTurkish ? "Google Drive Bağlı" : "Google Drive Connected")
                                 : (store.isTurkish ? "Google Drive Bulut Eşitleme" : "Google Drive Cloud Sync"))
                                .font(.subheadline.bold())
                            Text(store.isDriveConnected
                                 ? (store.isTurkish ? "Notlarınız, çizimleriniz ve sesleriniz şifreli olarak eşitleniyor." : "Your notes, drawings and audio sync securely encrypted.")
                                 : (store.isTurkish ? "Tek dokunuşla Google hesabınızla giriş yapıp onay verin; Android, Windows, Mac ve iOS cihazlarınız eşitlensin." : "Sign in once with your Google account to sync across Android, Windows, Mac & iOS."))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(.vertical, 4)

                    if let msg = store.syncStatusMessage {
                        Label(msg, systemImage: store.isSyncError ? "exclamationmark.triangle.fill" : "checkmark.circle.fill")
                            .font(.caption)
                            .foregroundStyle(store.isSyncError ? .red : PureColors.emeraldGreen)
                    }

                    if store.isSyncing {
                        HStack(spacing: 8) {
                            ProgressView()
                            Text(store.isTurkish ? "Google hesabınız doğrulanıyor & eşitleniyor..." : "Signing in & syncing encrypted notes...")
                                .font(.caption)
                        }
                    }

                    if !store.isDriveConnected {
                        Button {
                            if hasConfiguredClient {
                                Task { await store.connectAndSyncDrive() }
                            } else {
                                withAnimation { showAdvancedPublisher = true }
                                store.syncStatusMessage = store.isTurkish
                                    ? "Bu yerel geliştirici derlemesinde henüz Google uygulama anahtarı gömülü değil; aşağıdan yayıncı Client ID girebilirsiniz."
                                    : "Google app client key is not bundled in this local developer build yet."
                            }
                        } label: {
                            HStack {
                                Spacer()
                                Label(store.isTurkish ? "Google ile Bağlan ve Onayla" : "Continue with Google", systemImage: "person.badge.key.fill")
                                    .fontWeight(.semibold)
                                Spacer()
                            }
                            .padding(.vertical, 4)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(PureColors.vibrantOrange)
                        .disabled(store.isSyncing)
                    } else {
                        Button {
                            Task { await store.triggerDriveSync(customPassphrase: customPassphrase) }
                        } label: {
                            Label(store.isTurkish ? "Şimdi Eşitle" : "Sync Now", systemImage: "arrow.triangle.2.circlepath")
                                .fontWeight(.semibold)
                        }
                        .disabled(store.isSyncing)

                        Button(role: .destructive) {
                            Task { await store.disconnectDrive() }
                        } label: {
                            Label(store.isTurkish ? "Google Hesabından Çık" : "Disconnect Google Account", systemImage: "xmark.icloud")
                        }
                        .disabled(store.isSyncing)
                    }
                }

                Section {
                    DisclosureGroup(
                        store.isTurkish ? "Gelişmiş Güvenlik Seçenekleri" : "Advanced Security Options",
                        isExpanded: $showPassphraseOptions
                    ) {
                        SecureField(
                            store.isTurkish ? "Özel şifreleme anahtarı (isteğe bağlı)" : "Optional custom passphrase",
                            text: $customPassphrase
                        )
                        Text(store.isTurkish
                             ? "Boş bırakıldığında tüm cihazlarınız otomatik olarak ortak güvenli kasa anahtarını paylaşır."
                             : "When empty, all your signed-in devices automatically share an end-to-end vault key.")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }

                    if !hasConfiguredClient {
                        DisclosureGroup(
                            store.isTurkish ? "Yayıncı / Geliştirici Bağlantısı" : "Developer / Publisher App Key",
                            isExpanded: $showAdvancedPublisher
                        ) {
                            TextField("xxxx.apps.googleusercontent.com", text: $clientIdInput)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                                .font(.caption.monospaced())

                            Button(store.isTurkish ? "Uygulama Anahtarını Kaydet" : "Save App Client ID") {
                                store.saveIosOAuthClientId(clientIdInput)
                            }
                            .disabled(clientIdInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)

                            Text(store.isTurkish
                                 ? "Son kullanıcıların görmemesi için bu anahtarı proje kökündeki local.properties -> purenote.ios.oauth.clientId alanına bir kez eklemeniz yeterlidir."
                                 : "Set purenote.ios.oauth.clientId in local.properties so end users sign in immediately.")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
            .onAppear {
                clientIdInput = store.resolvedIosOAuthClientId()
                showAdvancedPublisher = false
            }
            .navigationTitle(store.isTurkish ? "Bulut Eşitleme" : "Cloud Sync")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(store.isTurkish ? "Kapat" : "Close") { dismiss() }
                }
            }
        }
        .presentationBackground(.ultraThinMaterial)
    }
}

// MARK: - Vault Security, Face ID, PIN & Backup Sheet

struct VaultSecuritySheet: View {
    @ObservedObject var store: PureNoteStore
    @Environment(\.dismiss) private var dismiss

    @State private var currentPin = ""
    @State private var newPin = ""
    @State private var confirmPin = ""
    @State private var unlockPin = ""
    @State private var backupPass = ""
    @State private var feedback: String? = nil
    @State private var exportedUrl: URL? = nil
    @State private var showImporter = false

    var body: some View {
        NavigationStack {
            Form {
                if store.securityManager.isPinConfigured && !store.securityManager.isVaultUnlocked {
                    Section(store.isTurkish ? "Kasa Kilidini Aç" : "Unlock Vault") {
                        SecureField(store.isTurkish ? "6 haneli PIN" : "6-digit PIN", text: $unlockPin)
                            .keyboardType(.numberPad)
                        Button(store.isTurkish ? "PIN ile Aç" : "Unlock with PIN") {
                            let res = store.securityManager.unlockWithPin(unlockPin)
                            feedback = res.1
                            unlockPin = ""
                        }
                        if store.securityManager.canUseBiometrics() {
                            Button {
                                Task {
                                    let res = await store.securityManager.unlockWithBiometrics(
                                        reason: store.isTurkish ? "PureNoteIt kasasını açın" : "Unlock PureNoteIt vault"
                                    )
                                    feedback = res.1
                                }
                            } label: {
                                Label(store.isTurkish ? "Face ID / Touch ID ile Aç" : "Unlock with Face ID", systemImage: "faceid")
                            }
                        }
                    }
                }

                Section(store.isTurkish ? "6 Haneli Ana PIN" : "6-Digit Master PIN") {
                    if store.securityManager.isPinConfigured {
                        SecureField(store.isTurkish ? "Mevcut PIN" : "Current PIN", text: $currentPin)
                            .keyboardType(.numberPad)
                    }
                    SecureField(store.isTurkish ? "Yeni 6 haneli PIN" : "New 6-digit PIN", text: $newPin)
                        .keyboardType(.numberPad)
                    SecureField(store.isTurkish ? "Yeni PIN Tekrar" : "Confirm PIN", text: $confirmPin)
                        .keyboardType(.numberPad)

                    Button(store.securityManager.isPinConfigured
                           ? (store.isTurkish ? "PIN'i Güncelle" : "Update PIN")
                           : (store.isTurkish ? "PIN Oluştur" : "Set Master PIN")) {
                        guard newPin == confirmPin else {
                            feedback = store.isTurkish ? "PIN'ler eşleşmiyor." : "PINs do not match."
                            return
                        }
                        let res = store.securityManager.setOrUpdatePin(
                            currentPin: store.securityManager.isPinConfigured ? currentPin : nil,
                            newPin: newPin
                        )
                        feedback = res.0 ? (store.isTurkish ? "PIN kaydedildi." : "PIN saved.") : res.1
                        currentPin = ""; newPin = ""; confirmPin = ""
                    }

                    if store.securityManager.isPinConfigured {
                        Button(role: .destructive) {
                            let res = store.securityManager.removePin(currentPin: currentPin)
                            feedback = res.0 ? (store.isTurkish ? "PIN kaldırıldı." : "PIN removed.") : res.1
                            currentPin = ""
                        } label: {
                            Text(store.isTurkish ? "Master PIN'i Kaldır" : "Remove Master PIN")
                        }
                    }
                }

                Section(store.isTurkish ? "Biyometri & Otomatik Kilit" : "Biometrics & Auto-Lock") {
                    if store.securityManager.canUseBiometrics() {
                        Toggle(
                            store.isTurkish ? "Face ID / Touch ID Aktif" : "Enable Face ID / Touch ID",
                            isOn: Binding(
                                get: { store.securityManager.isBiometricEnabled },
                                set: { store.securityManager.setBiometricEnabled($0) }
                            )
                        )
                    }

                    Picker(
                        store.isTurkish ? "Otomatik Kilit Süresi" : "Auto-Lock Timeout",
                        selection: Binding(
                            get: { store.securityManager.autoLockMinutes },
                            set: { store.securityManager.setAutoLockMinutes($0) }
                        )
                    ) {
                        Text("1 dk").tag(1)
                        Text("5 dk").tag(5)
                        Text("15 dk").tag(15)
                        Text(store.isTurkish ? "Manuel" : "Manual").tag(0)
                    }

                    if store.securityManager.isPinConfigured && store.securityManager.isVaultUnlocked {
                        Button(store.isTurkish ? "Kasayı Şimdi Kilitle" : "Lock Vault Now") {
                            store.securityManager.lockVault()
                        }
                    }
                }

                Section(store.isTurkish ? "Şifreli Kasa Yedeği (.purevault.json)" : "Encrypted Vault Backup") {
                    SecureField(
                        store.isTurkish ? "Yedek şifreleme parolası" : "Backup passphrase",
                        text: $backupPass
                    )
                    Button {
                        exportedUrl = IosExporterAndBackupService.exportEncryptedVaultJson(
                            db: store.db,
                            vaultCrypto: store.vaultCrypto,
                            passphrase: backupPass
                        )
                        if exportedUrl == nil {
                            feedback = store.isTurkish ? "Önce geçerli bir yedek parolası girin." : "Enter a backup passphrase."
                        }
                    } label: {
                        Label(store.isTurkish ? "Şifreli Yedek Oluştur" : "Export Encrypted Backup", systemImage: "arrow.up.doc.fill")
                    }

                    if let exportedUrl {
                        ShareLink(item: exportedUrl) {
                            Label(store.isTurkish ? "Yedek Dosyasını Paylaş" : "Share Backup File", systemImage: "square.and.arrow.up")
                        }
                    }

                    Button {
                        showImporter = true
                    } label: {
                        Label(store.isTurkish ? "Yedekten Geri Yükle" : "Import Encrypted Backup", systemImage: "arrow.down.doc.fill")
                    }
                }

                if let feedback {
                    Section {
                        Text(feedback)
                            .font(.caption)
                            .foregroundStyle(PureColors.vibrantOrange)
                    }
                }
            }
            .fileImporter(isPresented: $showImporter, allowedContentTypes: [.json, .data]) { result in
                if case .success(let url) = result {
                    _ = url.startAccessingSecurityScopedResource()
                    defer { url.stopAccessingSecurityScopedResource() }
                    let count = IosExporterAndBackupService.importEncryptedVaultJson(
                        from: url,
                        db: store.db,
                        vaultCrypto: store.vaultCrypto,
                        passphrase: backupPass
                    )
                    if count >= 0 {
                        store.reloadAll()
                        feedback = store.isTurkish ? "\(count) not başarıyla geri yüklendi." : "Restored \(count) notes."
                    } else {
                        feedback = store.isTurkish ? "Yedek çözülemedi — parolayı kontrol edin." : "Failed to decrypt backup."
                    }
                }
            }
            .navigationTitle(store.isTurkish ? "Kasa & Güvenlik" : "Vault & Security")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(store.isTurkish ? "Kapat" : "Close") { dismiss() }
                }
            }
        }
    }
}

// MARK: - Trash Bin Sheet

struct TrashBinSheet: View {
    @ObservedObject var store: PureNoteStore
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Picker(
                        store.isTurkish ? "Otomatik Silme Süresi" : "Auto-Purge Retention",
                        selection: Binding(
                            get: { store.trashRetentionDays },
                            set: { store.setTrashRetention($0) }
                        )
                    ) {
                        Text("7 Gün").tag(7)
                        Text("14 Gün").tag(14)
                        Text("30 Gün").tag(30)
                        Text("60 Gün").tag(60)
                        Text("90 Gün").tag(90)
                        Text(store.isTurkish ? "Asla" : "Never").tag(0)
                    }
                }

                if store.trashedNotes.isEmpty {
                    ContentUnavailableView(
                        store.isTurkish ? "Çöp Kutusu Boş" : "Trash is Empty",
                        systemImage: "trash"
                    )
                } else {
                    ForEach(store.trashedNotes) { note in
                        HStack(spacing: 12) {
                            ModernNoteIconBadge(iconKey: note.icon, isProtected: store.isNoteProtected(note), size: 30, iconSize: 15)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(note.title.isEmpty ? (store.isTurkish ? "Başlıksız Not" : "Untitled") : note.title)
                                    .font(.subheadline.weight(.semibold))
                                Text(store.plainSummary(for: note))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                            Spacer()
                            Button {
                                store.restoreFromTrash(note)
                            } label: {
                                Image(systemName: "arrow.uturn.backward.circle.fill")
                                    .foregroundStyle(PureColors.emeraldGreen)
                            }
                            Button(role: .destructive) {
                                store.permanentlyDelete(note)
                            } label: {
                                Image(systemName: "trash.circle.fill")
                                    .foregroundStyle(.red)
                            }
                        }
                    }
                }
            }
            .navigationTitle(store.isTurkish ? "Çöp Kutusu (\(store.trashedNotes.count))" : "Trash (\(store.trashedNotes.count))")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(store.isTurkish ? "Kapat" : "Close") { dismiss() }
                }
                if !store.trashedNotes.isEmpty {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button(store.isTurkish ? "Boşalt" : "Empty", role: .destructive) {
                            store.emptyTrash()
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Category Management & App Settings Sheets

struct CategoryManagementSheet: View {
    @ObservedObject var store: PureNoteStore
    @Environment(\.dismiss) private var dismiss
    @State private var newName = ""
    @State private var search = ""

    var body: some View {
        NavigationStack {
            List {
                Section(store.isTurkish ? "Yeni Kategori Ekle" : "Add Category") {
                    HStack {
                        TextField(store.isTurkish ? "Kategori adı" : "Category name", text: $newName)
                        Button(store.isTurkish ? "Ekle" : "Add") {
                            store.createCategory(name: newName)
                            newName = ""
                        }
                        .disabled(newName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }
                }

                Section(store.isTurkish ? "Kategoriler" : "Categories") {
                    ForEach(Array(store.categories.filter { search.isEmpty || $0.name.localizedCaseInsensitiveContains(search) }.enumerated()), id: \.element.syncUuid) { idx, cat in
                        HStack(spacing: 10) {
                            Circle()
                                .fill(PureColors.categoryColor(index: idx))
                                .frame(width: 10, height: 10)
                            Text(cat.name)
                                .font(.subheadline.weight(.medium))
                            if ["Not", "Önemli", "Günlük"].contains(cat.name) {
                                Text(store.isTurkish ? "Varsayılan" : "Default")
                                    .font(.caption2)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(Color.secondary.opacity(0.15))
                                    .clipShape(Capsule())
                            }
                            Spacer()
                            Button {
                                store.toggleCategoryLock(cat)
                            } label: {
                                Image(systemName: cat.isPassword ? "lock.fill" : "lock.open")
                                    .foregroundStyle(cat.isPassword ? PureColors.vibrantOrange : .secondary)
                            }
                            if !["Not", "Önemli", "Günlük"].contains(cat.name) {
                                Button(role: .destructive) {
                                    store.deleteCategory(cat)
                                } label: {
                                    Image(systemName: "trash")
                                        .foregroundStyle(.red)
                                }
                            }
                        }
                    }
                }
            }
            .searchable(text: $search)
            .navigationTitle(store.isTurkish ? "Kategori Yönetimi" : "Categories")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(store.isTurkish ? "Kapat" : "Close") { dismiss() }
                }
            }
        }
    }
}

struct AppSettingsSheet: View {
    @ObservedObject var store: PureNoteStore
    let onOpenSync: () -> Void
    let onOpenVault: () -> Void
    let onOpenCategories: () -> Void
    let onOpenTrash: () -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section(store.isTurkish ? "Görünüm & Dil" : "Appearance & Language") {
                    Picker(
                        store.isTurkish ? "Tema" : "Theme",
                        selection: Binding(
                            get: { store.themePreference },
                            set: { store.setTheme($0) }
                        )
                    ) {
                        ForEach(AppThemePreference.allCases) { pref in
                            Text(pref.label(isTr: store.isTurkish)).tag(pref)
                        }
                    }
                    .pickerStyle(.segmented)

                    Toggle(
                        store.isTurkish ? "Türkçe Arayüz (TR / EN)" : "Turkish Interface (TR / EN)",
                        isOn: Binding(
                            get: { store.isTurkish },
                            set: { _ in store.toggleLanguage() }
                        )
                    )
                }

                Section(store.isTurkish ? "Kasa, Eşitleme & Veri" : "Vault, Sync & Data") {
                    Button { dismiss(); onOpenSync() } label: {
                        Label(store.isTurkish ? "Google Drive E2EE Eşitleme" : "Google Drive E2EE Sync", systemImage: "icloud.fill")
                    }
                    Button { dismiss(); onOpenVault() } label: {
                        Label(store.isTurkish ? "PIN, Face ID & Şifreli Yedekleme" : "PIN, Face ID & Vault Backup", systemImage: "lock.shield.fill")
                    }
                    Button { dismiss(); onOpenCategories() } label: {
                        Label(store.isTurkish ? "Kategori Yönetimi" : "Category Management", systemImage: "folder.fill")
                    }
                    Button { dismiss(); onOpenTrash() } label: {
                        Label(store.isTurkish ? "Çöp Kutusu & Saklama Süresi" : "Trash Bin & Retention", systemImage: "trash.fill")
                    }
                }

                Section(store.isTurkish ? "Hakkında" : "About") {
                    HStack {
                        Text("PureNoteIt iOS")
                        Spacer()
                        Text("v1.2.1 • Pandafy")
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle(store.isTurkish ? "Ayarlar" : "Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(store.isTurkish ? "Kapat" : "Close") { dismiss() }
                }
            }
        }
    }
}
