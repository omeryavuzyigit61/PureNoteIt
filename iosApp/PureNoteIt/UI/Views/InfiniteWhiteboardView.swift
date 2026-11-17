import PureNoteShared
import SwiftUI

struct InfiniteWhiteboardView: View {
    @ObservedObject var store: PureNoteStore
    let onOpenNote: (IosNoteRecord) -> Void

    @State private var scale: CGFloat = 0.9
    @State private var lastScale: CGFloat = 0.9
    @State private var panOffset: CGSize = CGSize(width: 40, height: 60)
    @State private var lastPan: CGSize = CGSize(width: 40, height: 60)
    @State private var activeDragDeltas: [String: CGSize] = [:]

    @Environment(\.colorScheme) private var colorScheme

    private let cardW: CGFloat = 210
    private let cardH: CGFloat = 132

    private var edges: [(source: String, target: String)] {
        var pairs: [(String, String)] = []
        let activeSet = Set(store.activeNotes.map { $0.syncUuid })
        for l in store.noteLinks where activeSet.contains(l.sourceSyncUuid) && activeSet.contains(l.targetSyncUuid) {
            pairs.append((l.sourceSyncUuid, l.targetSyncUuid))
        }
        for n in store.activeNotes where !n.isEncrypted {
            for b in BlockParser.shared.parse(raw: n.richBlocksJson) where b.type == .subPage {
                if let cUuid = b.subPageSyncUuid, activeSet.contains(cUuid) {
                    pairs.append((n.syncUuid, cUuid))
                }
            }
        }
        return pairs
    }

    var body: some View {
        ZStack(alignment: .topTrailing) {
            GeometryReader { _ in
                ZStack(alignment: .topLeading) {
                    // Dotted infinite canvas + curved Bezier arrows
                    Canvas { context, canvasSize in
                        let step: CGFloat = 32 * scale
                        let dotColor = colorScheme == .dark ? Color(white: 0.22) : Color(white: 0.85)
                        let sx = panOffset.width.truncatingRemainder(dividingBy: step)
                        let sy = panOffset.height.truncatingRemainder(dividingBy: step)
                        var x = sx
                        while x < canvasSize.width {
                            var y = sy
                            while y < canvasSize.height {
                                context.fill(
                                    Path(ellipseIn: CGRect(x: x, y: y, width: 2.4 * scale, height: 2.4 * scale)),
                                    with: .color(dotColor)
                                )
                                y += step
                            }
                            x += step
                        }

                        // Draw curved arrows between linked cards
                        for edge in edges {
                            guard let idxA = store.activeNotes.firstIndex(where: { $0.syncUuid == edge.source }),
                                  let idxB = store.activeNotes.firstIndex(where: { $0.syncUuid == edge.target }) else { continue }
                            let cA = cardCenter(note: store.activeNotes[idxA], index: idxA)
                            let cB = cardCenter(note: store.activeNotes[idxB], index: idxB)

                            var path = Path()
                            path.move(to: cA)
                            let midX = (cA.x + cB.x) / 2
                            path.addCurve(
                                to: cB,
                                control1: CGPoint(x: midX, y: cA.y),
                                control2: CGPoint(x: midX, y: cB.y)
                            )
                            context.stroke(path, with: .color(PureColors.vibrantOrange.opacity(0.85)), style: StrokeStyle(lineWidth: 2.2 * scale, lineCap: .round))

                            let dotRect = CGRect(x: cB.x - 4 * scale, y: cB.y - 4 * scale, width: 8 * scale, height: 8 * scale)
                            context.fill(Path(ellipseIn: dotRect), with: .color(PureColors.vibrantOrange))
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
                                scale = max(0.4, min(2.2, lastScale * val))
                            }
                            .onEnded { _ in
                                lastScale = scale
                            }
                    )

                    // Individually draggable note cards on infinite whiteboard
                    ForEach(Array(store.activeNotes.enumerated()), id: \.element.syncUuid) { idx, note in
                        let origin = cardOrigin(note: note, index: idx)
                        whiteboardCard(note)
                            .frame(width: cardW, height: cardH)
                            .scaleEffect(scale, anchor: .topLeading)
                            .offset(
                                x: origin.x * scale + panOffset.width,
                                y: origin.y * scale + panOffset.height
                            )
                            .gesture(
                                DragGesture()
                                    .onChanged { v in
                                        activeDragDeltas[note.syncUuid] = CGSize(
                                            width: v.translation.width / scale,
                                            height: v.translation.height / scale
                                        )
                                    }
                                    .onEnded { v in
                                        let base = basePosition(note: note, index: idx)
                                        let finalPt = CGPoint(
                                            x: base.x + v.translation.width / scale,
                                            y: base.y + v.translation.height / scale
                                        )
                                        activeDragDeltas.removeValue(forKey: note.syncUuid)
                                        store.updateWhiteboardPosition(noteSyncUuid: note.syncUuid, point: finalPt)
                                    }
                            )
                    }
                }
            }
            .background(colorScheme == .dark ? Color(red: 0.08, green: 0.08, blue: 0.09) : Color(red: 0.975, green: 0.975, blue: 0.985))

            // Top-right glass zoom & create HUD
            HStack(spacing: 8) {
                Button {
                    scale = max(0.4, scale - 0.15)
                    lastScale = scale
                } label: {
                    Image(systemName: "minus.magnifyingglass")
                }

                Text("\(Int(scale * 100))%")
                    .font(.caption.monospacedDigit().bold())

                Button {
                    scale = min(2.2, scale + 0.15)
                    lastScale = scale
                } label: {
                    Image(systemName: "plus.magnifyingglass")
                }

                Divider().frame(height: 16)

                Button {
                    let created = store.createNote(title: "", icon: "💡")
                    onOpenNote(created)
                } label: {
                    Image(systemName: "plus.circle.fill")
                        .foregroundStyle(PureColors.vibrantOrange)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .pureGlassCapsule()
            .padding(14)
        }
    }

    private func whiteboardCard(_ note: IosNoteRecord) -> some View {
        let isProtected = store.isNoteProtected(note)
        return VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                ModernNoteIconBadge(iconKey: note.icon, isProtected: isProtected, size: 24, iconSize: 12)
                Text(note.title.isEmpty ? (store.isTurkish ? "Başlıksız" : "Untitled") : note.title)
                    .font(.caption.bold())
                    .lineLimit(1)
                Spacer()
                Button {
                    onOpenNote(note)
                } label: {
                    Image(systemName: "arrow.up.right.circle.fill")
                        .font(.subheadline)
                        .foregroundStyle(PureColors.vibrantOrange)
                }
            }

            Text(isProtected && !store.securityManager.isVaultUnlocked ? "••••••" : store.plainSummary(for: note))
                .font(.caption2)
                .foregroundStyle(.secondary)
                .lineLimit(4)

            Spacer()

            HStack {
                if note.totalTasks > 0 {
                    Label("\(note.completedTasks)/\(note.totalTasks)", systemImage: "checklist")
                        .font(.caption2)
                        .foregroundStyle(PureColors.emeraldGreen)
                }
                Spacer()
                Image(systemName: "hand.draw.fill")
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            }
        }
        .padding(10)
        .pureGlassCard(cornerRadius: 16)
    }

    private func basePosition(note: IosNoteRecord, index: Int) -> CGPoint {
        if let saved = store.whiteboardPositions[note.syncUuid] {
            return saved
        }
        let col = index % 3
        let row = index / 3
        return CGPoint(
            x: 30 + CGFloat(col) * 240 + (row % 2 == 1 ? 30 : 0),
            y: 30 + CGFloat(row) * 175
        )
    }

    private func cardOrigin(note: IosNoteRecord, index: Int) -> CGPoint {
        let base = basePosition(note: note, index: index)
        let delta = activeDragDeltas[note.syncUuid] ?? .zero
        return CGPoint(x: base.x + delta.width, y: base.y + delta.height)
    }

    private func cardCenter(note: IosNoteRecord, index: Int) -> CGPoint {
        let origin = cardOrigin(note: note, index: index)
        return CGPoint(
            x: (origin.x + cardW / 2) * scale + panOffset.width,
            y: (origin.y + cardH / 2) * scale + panOffset.height
        )
    }
}
