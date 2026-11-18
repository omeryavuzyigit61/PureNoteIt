import SwiftUI

@main
struct PureNoteItApp: App {
    @StateObject private var store = PureNoteStore()
    @Environment(\.scenePhase) private var scenePhase
    @State private var showSplash = true
    @State private var splashScale: CGFloat = 0.72
    @State private var splashRingScale: CGFloat = 0.65
    @State private var splashOpacity: Double = 1.0

    var body: some Scene {
        WindowGroup {
            ZStack {
                NotesHomeView(store: store)
                    .tint(PureColors.vibrantOrange)
                    .preferredColorScheme(store.preferredColorScheme)

                if showSplash {
                    splashOverlay
                        .opacity(splashOpacity)
                        .zIndex(99)
                }
            }
            .onAppear {
                #if DEBUG
                if ProcessInfo.processInfo.arguments.contains("-demoScene") {
                    showSplash = false
                    return
                }
                #endif
                withAnimation(.spring(response: 0.45, dampingFraction: 0.7)) {
                    splashScale = 1.0
                    splashRingScale = 1.65
                }
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.85) {
                    withAnimation(.easeOut(duration: 0.35)) {
                        splashOpacity = 0.0
                    }
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.38) {
                        showSplash = false
                    }
                }
            }
            .onChange(of: scenePhase) { _, newPhase in
                switch newPhase {
                case .active:
                    store.securityManager.checkAutoLockIfNeeded()
                case .background:
                    if store.securityManager.isPinConfigured && store.securityManager.autoLockMinutes == 0 {
                        // Manual lock stays unchanged
                    } else {
                        store.securityManager.checkAutoLockIfNeeded()
                    }
                default:
                    break
                }
            }
        }
    }

    private var splashOverlay: some View {
        ZStack {
            (store.preferredColorScheme == .light ? PureColors.backgroundLight : PureColors.backgroundDark)
                .ignoresSafeArea()

            Circle()
                .stroke(PureColors.vibrantOrange.opacity(0.28), lineWidth: 2)
                .frame(width: 140, height: 140)
                .scaleEffect(splashRingScale)
                .opacity(max(0, 2.0 - splashRingScale))

            VStack(spacing: 14) {
                ModernNoteIconBadge(iconKey: "💎", isProtected: false, size: 78, iconSize: 36, cornerRadius: 22)
                    .shadow(color: PureColors.vibrantOrange.opacity(0.35), radius: 22, y: 8)
                Text("PureNoteIt")
                    .font(.system(size: 28, weight: .bold, design: .rounded))
                    .foregroundStyle(.primary)
                Text("Encrypted Glass Workspace")
                    .font(.caption.weight(.medium))
                    .foregroundStyle(.secondary)
            }
            .scaleEffect(splashScale)
        }
    }
}
