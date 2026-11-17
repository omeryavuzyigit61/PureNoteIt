import PureNoteShared
import SwiftUI

struct KnowledgeGraphView: View {
    @ObservedObject var store: PureNoteStore
    let onOpenNote: (IosNoteRecord) -> Void

    @State private var scale: CGFloat = 1.0
    @State private var lastScale: CGFloat = 1.0
    @State private var panOffset: CGSize = .zero
    @State private var lastPan: CGSize = .zero
    @State private var focusCategoryUuid: String? = nil

    @Environment(\.colorScheme) private var colorScheme

    private var visibleNotes: [IosNoteRecord] {
        store.activeNotes.filter { note in
            focusCategoryUuid == nil || note.categorySyncUuid == focusCategoryUuid
        }
    }

    private var edges: [(source: String, target: String)] {
        var pairs: [(String, String)] = []
        let activeSet = Set(visibleNotes.map { $0.syncUuid })
        for l in store.noteLinks where activeSet.contains(l.sourceSyncUuid) && activeSet.contains(l.targetSyncUuid) {
            pairs.append((l.sourceSyncUuid, l.targetSyncUuid))
        }
        // Also add SUB_PAGE edges
        for n in visibleNotes where !n.isEncrypted {
            let blocks = BlockParser.shared.parse(raw: n.richBlocksJson)
            for b in blocks where b.type == .subPage {
                if let childUuid = b.subPageSyncUuid, activeSet.contains(childUuid) {
                    pairs.append((n.syncUuid, childUuid))
                }
            }
        }
        return pairs
    }

    var body: some View {
        ZStack(alignment: .top) {
            GeometryReader { geo in
                let positions = computeLayout(notes: visibleNotes, edges: edges, size: geo.size)
                TimelineView(.animation) { timeline in
                    let t = timeline.date.timeIntervalSinceReferenceDate
                    let flow = CGFloat((t.truncatingRemainder(dividingBy: 2.2)) / 2.2)
                    let pulse = CGFloat(0.5 + 0.5 * sin(t * 2.5))

                    Canvas { context, canvasSize in
                        drawStarfield(context: &context, size: canvasSize)

                        // Draw neon edges & traveling photon particles
                        for edge in edges {
                            guard let p1 = positions[edge.source], let p2 = positions[edge.target] else { continue }
                            let s1 = screenPoint(p1, center: CGPoint(x: canvasSize.width / 2, y: canvasSize.height / 2))
                            let s2 = screenPoint(p2, center: CGPoint(x: canvasSize.width / 2, y: canvasSize.height / 2))
                            var path = Path()
                            path.move(to: s1)
                            path.addLine(to: s2)
                            context.stroke(path, with: .color(PureColors.vibrantOrange.opacity(0.22)), lineWidth: 5 * scale)
                            context.stroke(path, with: .color(PureColors.vibrantOrange.opacity(0.75)), lineWidth: 1.8 * scale)

                            let px = s1.x + (s2.x - s1.x) * flow
                            let py = s1.y + (s2.y - s1.y) * flow
                            let dot = CGRect(x: px - 3.5 * scale, y: py - 3.5 * scale, width: 7 * scale, height: 7 * scale)
                            context.fill(Path(ellipseIn: dot), with: .color(Color.white))
                        }

                        // Draw nodes
                        for note in visibleNotes {
                            guard let pos = positions[note.syncUuid] else { continue }
                            let sp = screenPoint(pos, center: CGPoint(x: canvasSize.width / 2, y: canvasSize.height / 2))
                            let nodeColor = colorForNote(note)
                            let isLinked = edges.contains { $0.source == note.syncUuid || $0.target == note.syncUuid }
                            let baseRadius: CGFloat = (note.isPinned || isLinked ? 16 : 12) * scale

                            if note.isPinned || isLinked {
                                let auraR = baseRadius * (1.35 + 0.3 * pulse)
                                let auraRect = CGRect(x: sp.x - auraR, y: sp.y - auraR, width: auraR * 2, height: auraR * 2)
                                context.fill(Path(ellipseIn: auraRect), with: .color(nodeColor.opacity(0.22)))
                            }

                            let nodeRect = CGRect(x: sp.x - baseRadius, y: sp.y - baseRadius, width: baseRadius * 2, height: baseRadius * 2)
                            context.fill(Path(ellipseIn: nodeRect), with: .color(nodeColor))
                            context.stroke(Path(ellipseIn: nodeRect), with: .color(Color.white.opacity(0.7)), lineWidth: 1.5)

                            let label = store.isNoteProtected(note) && !store.securityManager.isVaultUnlocked
                                ? "🔒 •••"
                                : (note.title.isEmpty ? "Untitled" : String(note.title.prefix(20)))
                            let text = Text(label)
                                .font(.system(size: max(9, 11 * scale), weight: .semibold))
                                .foregroundColor(colorScheme == .dark ? .white : .black)
                            context.draw(text, at: CGPoint(x: sp.x, y: sp.y + baseRadius + 12 * scale))
                        }
                    }
                    .contentShape(Rectangle())
                    .gesture(
                        DragGesture()
                            .onChanged { v in
                                panOffset = CGSize(
                                    width: lastPan.width + v.translation.width,
                                    height: lastPan.height + v.translation.height
                                )
                            }
                            .onEnded { _ in
                                lastPan = panOffset
                            }
                    )
                    .simultaneousGesture(
                        MagnificationGesture()
                            .onChanged { val in
                                scale = max(0.35, min(3.5, lastScale * val))
                            }
                            .onEnded { _ in
                                lastScale = scale
                            }
                    )
                    .simultaneousGesture(
                        SpatialTapGesture()
                            .onEnded { value in
                                let center = CGPoint(x: geo.size.width / 2, y: geo.size.height / 2)
                                for note in visibleNotes {
                                    guard let pos = positions[note.syncUuid] else { continue }
                                    let sp = screenPoint(pos, center: center)
                                    let dist = hypot(value.location.x - sp.x, value.location.y - sp.y)
                                    if dist <= 34 * max(0.8, scale) {
                                        onOpenNote(note)
                                        break
                                    }
                                }
                            }
                    )
                }
            }
            .background(colorScheme == .dark ? Color(red: 0.06, green: 0.06, blue: 0.08) : PureColors.backgroundLight)

            // Category Focus HUD
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    Button {
                        focusCategoryUuid = nil
                    } label: {
                        Text(store.isTurkish ? "Tüm Ağ (\(store.activeNotes.count))" : "All (\(store.activeNotes.count))")
                            .font(.caption.bold())
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(focusCategoryUuid == nil ? PureColors.vibrantOrange : Color.secondary.opacity(0.2))
                            .foregroundStyle(focusCategoryUuid == nil ? .white : .primary)
                            .clipShape(Capsule())
                    }

                    ForEach(Array(store.categories.enumerated()), id: \.element.syncUuid) { idx, cat in
                        let c = PureColors.categoryColor(index: idx)
                        let sel = focusCategoryUuid == cat.syncUuid
                        Button {
                            focusCategoryUuid = sel ? nil : cat.syncUuid
                        } label: {
                            HStack(spacing: 5) {
                                Circle().fill(c).frame(width: 7, height: 7)
                                Text(cat.name)
                            }
                            .font(.caption.weight(.semibold))
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(sel ? c.opacity(0.28) : Color.secondary.opacity(0.15))
                            .clipShape(Capsule())
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
            }
            .background(.ultraThinMaterial)
        }
    }

    private func screenPoint(_ logical: CGPoint, center: CGPoint) -> CGPoint {
        CGPoint(
            x: center.x + logical.x * scale + panOffset.width,
            y: center.y + logical.y * scale + panOffset.height
        )
    }

    private func drawStarfield(context: inout GraphicsContext, size: CGSize) {
        let step: CGFloat = 34 * max(0.6, scale)
        let dotColor = colorScheme == .dark ? Color.white.opacity(0.08) : Color.black.opacity(0.06)
        let offsetX = panOffset.width.truncatingRemainder(dividingBy: step)
        let offsetY = panOffset.height.truncatingRemainder(dividingBy: step)
        var x = offsetX
        while x < size.width {
            var y = offsetY
            while y < size.height {
                let rect = CGRect(x: x, y: y, width: 2, height: 2)
                context.fill(Path(ellipseIn: rect), with: .color(dotColor))
                y += step
            }
            x += step
        }
    }

    private func computeLayout(notes: [IosNoteRecord], edges: [(source: String, target: String)], size: CGSize) -> [String: CGPoint] {
        var pos: [String: CGPoint] = [:]
        let goldenAngle = Double.pi * (3.0 - sqrt(5.0))
        for (i, n) in notes.enumerated() {
            let r = notes.count <= 6 ? 115.0 : 88.0 * sqrt(Double(i + 1))
            let angle = (notes.count <= 6 ? (Double(i) / Double(max(1, notes.count))) * 2.0 * .pi : Double(i) * goldenAngle)
            pos[n.syncUuid] = CGPoint(x: cos(angle) * r, y: sin(angle) * r)
        }

        // Light force-directed repulsion + spring attraction
        for _ in 0..<18 {
            // Repulsion to prevent overlapping labels/circles
            for i in 0..<notes.count {
                for j in (i + 1)..<notes.count {
                    let a = notes[i].syncUuid
                    let b = notes[j].syncUuid
                    guard let p1 = pos[a], let p2 = pos[b] else { continue }
                    let dx = p2.x - p1.x
                    let dy = p2.y - p1.y
                    let dist = max(1, hypot(dx, dy))
                    let minDist = 138.0
                    if dist < minDist {
                        let push = (minDist - dist) * 0.25
                        let nx = dx / dist * push
                        let ny = dy / dist * push
                        pos[a] = CGPoint(x: p1.x - nx, y: p1.y - ny)
                        pos[b] = CGPoint(x: p2.x + nx, y: p2.y + ny)
                    }
                }
            }
            for e in edges {
                guard let p1 = pos[e.source], let p2 = pos[e.target] else { continue }
                let dx = p2.x - p1.x
                let dy = p2.y - p1.y
                let dist = max(1, hypot(dx, dy))
                let pull = (dist - 145) * 0.06
                let nx = dx / dist * pull
                let ny = dy / dist * pull
                pos[e.source] = CGPoint(x: p1.x + nx, y: p1.y + ny)
                pos[e.target] = CGPoint(x: p2.x - nx, y: p2.y - ny)
            }
        }
        return pos
    }

    private func colorForNote(_ note: IosNoteRecord) -> Color {
        if let catUuid = note.categorySyncUuid,
           let idx = store.categories.firstIndex(where: { $0.syncUuid == catUuid }) {
            return PureColors.categoryColor(index: idx)
        }
        return PureColors.vibrantOrange
    }
}
