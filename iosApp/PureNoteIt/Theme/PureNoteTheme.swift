import SwiftUI

enum PureColors {
    static let pureWhite = Color.white
    static let backgroundLight = Color(red: 0.973, green: 0.973, blue: 0.980) // #F8F8FA
    static let backgroundDark = Color(red: 0.102, green: 0.102, blue: 0.110)  // #1A1A1C
    static let surfaceLight = Color.white
    static let surfaceDark = Color(red: 0.141, green: 0.141, blue: 0.149)     // #242426
    static let charcoalGray = Color(red: 0.157, green: 0.157, blue: 0.157)    // #282828
    static let vibrantOrange = Color(red: 1.0, green: 0.565, blue: 0.059)     // #FF900F
    static let softOrangeLight = Color(red: 1.0, green: 0.910, blue: 0.820)   // #FFE8D1
    static let softYellow = Color(red: 0.969, green: 0.843, blue: 0.525)      // #F7D786
    static let softYellowLight = Color(red: 1.0, green: 0.969, blue: 0.890)   // #FFF7E3
    static let accentBlue = Color(red: 0.196, green: 0.412, blue: 1.0)        // #3269FF
    static let emeraldGreen = Color(red: 0.063, green: 0.725, blue: 0.506)    // #10B981
    static let borderLight = Color(red: 0.898, green: 0.898, blue: 0.918)     // #E5E5EA
    static let borderDark = Color(red: 0.227, green: 0.227, blue: 0.235)      // #3A3A3C
    static let textPrimaryLight = Color(red: 0.110, green: 0.110, blue: 0.118)// #1C1C1E
    static let textSecondaryLight = Color(red: 0.557, green: 0.557, blue: 0.576)
    static let textPrimaryDark = Color(red: 0.949, green: 0.949, blue: 0.969) // #F2F2F7
    static let textSecondaryDark = Color(red: 0.627, green: 0.627, blue: 0.647)

    static let neonPalette: [Color] = [
        Color(red: 1.0, green: 0.478, blue: 0.0),   // Neon Orange
        Color(red: 0.063, green: 0.725, blue: 0.506), // Emerald
        Color(red: 0.024, green: 0.714, blue: 0.831), // Cyan
        Color(red: 0.545, green: 0.361, blue: 0.965), // Neural Violet
        Color(red: 0.961, green: 0.620, blue: 0.043), // Solar Amber
        Color(red: 0.957, green: 0.247, blue: 0.369), // Cyber Rose
        Color(red: 0.231, green: 0.510, blue: 0.965)  // Hyper Cobalt
    ]

    static let recentBannerPalettes: [(Color, Color)] = [
        (Color(red: 1.0, green: 0.88, blue: 0.76), Color(red: 1.0, green: 0.67, blue: 0.42)),   // Peach
        (Color(red: 0.80, green: 0.96, blue: 0.88), Color(red: 0.36, green: 0.82, blue: 0.62)), // Mint
        (Color(red: 1.0, green: 0.84, blue: 0.90), Color(red: 0.96, green: 0.48, blue: 0.64)),  // Rose
        (Color(red: 0.82, green: 0.92, blue: 1.0), Color(red: 0.38, green: 0.66, blue: 0.98)),  // Sky
        (Color(red: 1.0, green: 0.94, blue: 0.78), Color(red: 0.98, green: 0.76, blue: 0.32)),  // Amber
        (Color(red: 0.91, green: 0.85, blue: 1.0), Color(red: 0.65, green: 0.48, blue: 0.98))   // Lavender
    ]

    static func categoryColor(index: Int) -> Color {
        neonPalette[abs(index) % neonPalette.count]
    }
}

enum AppThemePreference: String, CaseIterable, Identifiable {
    case system
    case light
    case dark

    var id: String { rawValue }

    func label(isTr: Bool) -> String {
        switch self {
        case .system: return isTr ? "Sistem" : "System"
        case .light: return isTr ? "Açık" : "Light"
        case .dark: return isTr ? "Koyu" : "Dark"
        }
    }
}

struct GlassCardModifier: ViewModifier {
    var cornerRadius: CGFloat = 18
    @Environment(\.colorScheme) private var colorScheme

    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        content
            .background(
                shape
                    .fill(.ultraThinMaterial)
            )
            .clipShape(shape)
            .overlay(
                shape
                    .stroke(
                        LinearGradient(
                            colors: [
                                Color.white.opacity(colorScheme == .dark ? 0.24 : 0.78),
                                Color.white.opacity(colorScheme == .dark ? 0.04 : 0.16)
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: 1
                    )
            )
            .shadow(color: Color.black.opacity(colorScheme == .dark ? 0.28 : 0.06), radius: 10, x: 0, y: 4)
    }
}

struct GlassCapsuleModifier: ViewModifier {
    @Environment(\.colorScheme) private var colorScheme

    func body(content: Content) -> some View {
        content
            .background(Capsule().fill(.ultraThinMaterial))
            .overlay(
                Capsule()
                    .stroke(
                        Color.white.opacity(colorScheme == .dark ? 0.18 : 0.65),
                        lineWidth: 1
                    )
            )
            .shadow(color: Color.black.opacity(colorScheme == .dark ? 0.25 : 0.07), radius: 8, x: 0, y: 3)
    }
}

extension View {
    func pureGlassCard(cornerRadius: CGFloat = 18) -> some View {
        modifier(GlassCardModifier(cornerRadius: cornerRadius))
    }

    func pureGlassCapsule() -> some View {
        modifier(GlassCapsuleModifier())
    }
}

/// Modern Material-style rounded vector badge replacing raw emojis across Grid, List, Kanban, Graph & Sheets.
struct ModernNoteIconBadge: View {
    let iconKey: String?
    var isProtected: Bool = false
    var size: CGFloat = 30
    var iconSize: CGFloat = 15
    var cornerRadius: CGFloat = 9

    @Environment(\.colorScheme) private var colorScheme

    private var resolved: (symbol: String, tint: Color) {
        if isProtected { return ("lock.fill", PureColors.vibrantOrange) }
        let key = (iconKey ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let lower = key.lowercased()
        switch key {
        case "", "📄", "📝", "doc":
            return ("doc.text.fill", PureColors.vibrantOrange)
        case "💼", "📋":
            return ("briefcase.fill", Color(red: 0.42, green: 0.29, blue: 0.24))
        case "⚡", "💻":
            return ("laptopcomputer", Color(red: 0.08, green: 0.40, blue: 0.75))
        case "♾️", "♾", "📐":
            return ("function", Color(red: 0.96, green: 0.50, blue: 0.09))
        case "📖", "📚":
            return ("book.closed.fill", Color(red: 0.42, green: 0.11, blue: 0.60))
        case "🏛️", "🏛":
            return ("building.columns.fill", Color(red: 0.22, green: 0.28, blue: 0.31))
        case "🗣️", "🗣", "🎙️", "🎙":
            return ("waveform.and.mic", Color(red: 0.0, green: 0.41, blue: 0.36))
        case "💎":
            return ("diamond.fill", Color(red: 0.0, green: 0.51, blue: 0.56))
        case "🥗":
            return ("leaf.fill", Color(red: 0.18, green: 0.49, blue: 0.20))
        case "💪":
            return ("figure.strengthtraining.traditional", Color(red: 0.76, green: 0.09, blue: 0.36))
        case "🌿":
            return ("sparkles", Color(red: 0.33, green: 0.55, blue: 0.18))
        case "📊":
            return ("chart.bar.fill", Color(red: 0.16, green: 0.21, blue: 0.58))
        case "🧭":
            return ("safari.fill", Color(red: 0.01, green: 0.47, blue: 0.74))
        case "🎯":
            return ("target", Color(red: 0.90, green: 0.32, blue: 0.0))
        case "📅", "☀️":
            return ("calendar", PureColors.vibrantOrange)
        case "🚀":
            return ("paperplane.fill", PureColors.accentBlue)
        case "💡":
            return ("lightbulb.fill", Color(red: 0.96, green: 0.62, blue: 0.04))
        case "🔐", "🔒":
            return ("lock.shield.fill", PureColors.vibrantOrange)
        default:
            if lower.contains("toplantı") || lower.contains("meeting") { return ("briefcase.fill", Color(red: 0.42, green: 0.29, blue: 0.24)) }
            if lower.contains("kod") || lower.contains("dev") { return ("chevron.left.forwardslash.chevron.right", Color(red: 0.08, green: 0.40, blue: 0.75)) }
            if lower.contains("matematik") || lower.contains("math") { return ("function", Color(red: 0.96, green: 0.50, blue: 0.09)) }
            if lower.contains("ajanda") || lower.contains("günlük") { return ("calendar", PureColors.vibrantOrange) }
            if lower.contains("bütçe") || lower.contains("finans") { return ("chart.bar.fill", Color(red: 0.18, green: 0.49, blue: 0.20)) }
            return ("note.text", PureColors.vibrantOrange)
        }
    }

    var body: some View {
        let item = resolved
        ZStack {
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .fill(colorScheme == .dark ? Color(red: 0.15, green: 0.15, blue: 0.17) : Color.white)
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .fill(item.tint.opacity(colorScheme == .dark ? 0.16 : 0.10))
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .stroke(
                    colorScheme == .dark ? Color.white.opacity(0.12) : PureColors.borderLight,
                    lineWidth: 1
                )
            Image(systemName: item.symbol)
                .font(.system(size: iconSize, weight: .semibold))
                .foregroundStyle(item.tint)
        }
        .frame(width: size, height: size)
    }
}

struct ModernCalloutBadge: View {
    let key: String
    var size: CGFloat = 26

    private var resolved: (symbol: String, tint: Color) {
        switch key {
        case "📅", "☀️": return ("calendar", Color(red: 0.85, green: 0.26, blue: 0.08))
        case "💡", "🌿": return ("lightbulb.fill", Color(red: 0.96, green: 0.50, blue: 0.09))
        case "⚡", "🚀": return ("bolt.fill", Color(red: 0.08, green: 0.40, blue: 0.75))
        case "❝", "\"": return ("quote.opening", Color(red: 0.36, green: 0.25, blue: 0.22))
        case "🎯": return ("target", Color(red: 0.90, green: 0.32, blue: 0.0))
        case "🎨": return ("paintbrush.pointed.fill", Color(red: 0.48, green: 0.12, blue: 0.64))
        case "📖", "📚": return ("book.fill", Color(red: 0.29, green: 0.08, blue: 0.55))
        case "🏛️", "🏛": return ("building.columns.fill", Color(red: 0.22, green: 0.28, blue: 0.31))
        case "🗣️", "🎙️": return ("waveform.and.mic", Color(red: 0.0, green: 0.41, blue: 0.36))
        case "💎": return ("diamond.fill", Color(red: 0.0, green: 0.51, blue: 0.56))
        case "🥗": return ("leaf.fill", Color(red: 0.18, green: 0.49, blue: 0.20))
        case "💪": return ("figure.strengthtraining.traditional", Color(red: 0.76, green: 0.09, blue: 0.36))
        case "🧭": return ("safari.fill", Color(red: 0.01, green: 0.47, blue: 0.74))
        case "♾️": return ("function", Color(red: 0.96, green: 0.50, blue: 0.09))
        default: return ("info.circle.fill", PureColors.vibrantOrange)
        }
    }

    var body: some View {
        let item = resolved
        ZStack {
            RoundedRectangle(cornerRadius: 7, style: .continuous)
                .fill(item.tint.opacity(0.14))
            Image(systemName: item.symbol)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(item.tint)
        }
        .frame(width: size, height: size)
    }
}

struct ModernHeadingBadge: View {
    let text: String
    let isH1: Bool

    private var resolved: (symbol: String, tint: Color) {
        let lower = text.lowercased()
        if lower.contains("gündem") || lower.contains("agenda") || lower.contains("madde") {
            return ("pin.fill", Color(red: 0.90, green: 0.22, blue: 0.21))
        }
        if lower.contains("karar") || lower.contains("decision") || lower.contains("hedef") || lower.contains("sprint") {
            return ("target", Color(red: 0.90, green: 0.32, blue: 0.0))
        }
        if lower.contains("aksiyon") || lower.contains("action") || lower.contains("görev") || lower.contains("kontrol") || lower.contains("test") {
            return ("checklist", Color(red: 0.18, green: 0.49, blue: 0.20))
        }
        if lower.contains("kod") || lower.contains("code") || lower.contains("çözüm") || lower.contains("mimari") {
            return ("chevron.left.forwardslash.chevron.right", PureColors.accentBlue)
        }
        if lower.contains("formül") || lower.contains("math") || lower.contains("teorem") {
            return ("function", PureColors.vibrantOrange)
        }
        if lower.contains("tablo") || lower.contains("table") || lower.contains("bütçe") || lower.contains("gelir") {
            return ("tablecells.fill", Color(red: 0.0, green: 0.51, blue: 0.56))
        }
        if lower.contains("fikir") || lower.contains("özet") || lower.contains("not") || lower.contains("summary") {
            return ("lightbulb.fill", Color(red: 0.96, green: 0.62, blue: 0.04))
        }
        return (isH1 ? "textformat.size.larger" : "textformat.size", PureColors.vibrantOrange)
    }

    var body: some View {
        let item = resolved
        let dim: CGFloat = isH1 ? 26 : 22
        ZStack {
            RoundedRectangle(cornerRadius: 7, style: .continuous)
                .fill(item.tint.opacity(0.14))
            Image(systemName: item.symbol)
                .font(.system(size: isH1 ? 13 : 11, weight: .bold))
                .foregroundStyle(item.tint)
        }
        .frame(width: dim, height: dim)
    }
}

/// 60 FPS continuous dual sinusoidal audio wave visualizer matching Android's SpotifyAudioVisualizer.
struct LiquidAudioWaveformView: View {
    let isActive: Bool
    let level: CGFloat
    var tint: Color = PureColors.vibrantOrange
    var height: CGFloat = 28

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 60.0, paused: !isActive)) { timeline in
            Canvas { context, size in
                let t = isActive ? timeline.date.timeIntervalSinceReferenceDate : 0
                let w = size.width
                let h = size.height
                let midY = h / 2.0

                // Breathing floor when active so wave stays fluid even on quiet room audio
                let ambient = isActive ? (0.34 + 0.18 * CGFloat(sin(t * 4.2))) : 0.10
                let ampFraction = min(1.0, max(ambient, level))
                let maxAmp = max(2.0, (h / 2.0 - 2.0) * ampFraction)
                let waveLen = max(28.0, w / 2.3)

                var primaryPath = Path()
                var secondaryPath = Path()

                let step: CGFloat = 3.0
                var x: CGFloat = 0
                var started = false

                while x <= w {
                    let progress = x / max(1.0, w)
                    let envelope = CGFloat(sin(Double(progress) * .pi))

                    let angle1 = (Double(x / waveLen) * 2.0 * .pi) - (t * 6.5)
                    let angle2 = (Double(x / (waveLen * 1.32)) * 2.0 * .pi) + (t * 4.8)

                    let y1 = midY + (maxAmp * envelope) * CGFloat(sin(angle1))
                    let y2 = midY + (maxAmp * 0.68 * envelope) * CGFloat(sin(angle2))

                    if !started {
                        primaryPath.move(to: CGPoint(x: x, y: y1))
                        secondaryPath.move(to: CGPoint(x: x, y: y2))
                        started = true
                    } else {
                        primaryPath.addLine(to: CGPoint(x: x, y: y1))
                        secondaryPath.addLine(to: CGPoint(x: x, y: y2))
                    }
                    x += step
                }

                context.stroke(
                    secondaryPath,
                    with: .color(tint.opacity(isActive ? 0.40 : 0.18)),
                    style: StrokeStyle(lineWidth: 2.0, lineCap: .round, lineJoin: .round)
                )
                context.stroke(
                    primaryPath,
                    with: .color(tint.opacity(isActive ? 0.95 : 0.35)),
                    style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round)
                )
            }
        }
        .frame(height: height)
    }
}
