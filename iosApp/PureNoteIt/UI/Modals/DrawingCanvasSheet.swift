import SwiftUI
import UIKit

enum IosDrawingTool: String, CaseIterable, Identifiable {
    case pen
    case line
    case rectangle
    case circle
    case triangle
    case arrow
    case star
    case eraser

    var id: String { rawValue }

    var systemImage: String {
        switch self {
        case .pen: return "pencil.tip"
        case .line: return "line.diagonal"
        case .rectangle: return "rectangle"
        case .circle: return "circle"
        case .triangle: return "triangle"
        case .arrow: return "arrow.up.right"
        case .star: return "star"
        case .eraser: return "eraser.fill"
        }
    }

    func label(isTr: Bool) -> String {
        switch self {
        case .pen: return isTr ? "Kalem" : "Pen"
        case .line: return isTr ? "Çizgi" : "Line"
        case .rectangle: return isTr ? "Kare" : "Rect"
        case .circle: return isTr ? "Daire" : "Circle"
        case .triangle: return isTr ? "Üçgen" : "Triangle"
        case .arrow: return isTr ? "Ok" : "Arrow"
        case .star: return isTr ? "Yıldız" : "Star"
        case .eraser: return isTr ? "Silgi" : "Eraser"
        }
    }
}

struct DrawingStrokeItem: Identifiable {
    let id = UUID()
    let tool: IosDrawingTool
    let points: [CGPoint]
    let color: Color
    let uiColor: UIColor
    let lineWidth: CGFloat
}

struct DrawingCanvasSheet: View {
    let isTurkish: Bool
    let existingImageURL: URL?
    let onSavePng: (Data) -> Void

    @Environment(\.dismiss) private var dismiss

    @State private var strokes: [DrawingStrokeItem] = []
    @State private var activePoints: [CGPoint] = []
    @State private var selectedTool: IosDrawingTool = .pen
    @State private var selectedColorIndex: Int = 1 // Vibrant Orange default
    @State private var strokeWidth: CGFloat = 5.0
    @State private var canvasSize: CGSize = CGSize(width: 380, height: 480)
    @State private var clearBaseImage: Bool = false

    private let palette: [(color: Color, ui: UIColor)] = [
        (Color(red: 0.12, green: 0.12, blue: 0.12), UIColor(red: 0.12, green: 0.12, blue: 0.12, alpha: 1)),
        (Color(red: 1.0, green: 0.48, blue: 0.0), UIColor(red: 1.0, green: 0.48, blue: 0.0, alpha: 1)),
        (Color(red: 0.16, green: 0.47, blue: 1.0), UIColor(red: 0.16, green: 0.47, blue: 1.0, alpha: 1)),
        (Color(red: 0.0, green: 0.78, blue: 0.33), UIColor(red: 0.0, green: 0.78, blue: 0.33, alpha: 1)),
        (Color(red: 0.84, green: 0.0, blue: 0.0), UIColor(red: 0.84, green: 0.0, blue: 0.0, alpha: 1)),
        (Color(red: 0.67, green: 0.0, blue: 1.0), UIColor(red: 0.67, green: 0.0, blue: 1.0, alpha: 1))
    ]

    var body: some View {
        NavigationStack {
            VStack(spacing: 10) {
                // Canvas Surface
                GeometryReader { geo in
                    ZStack {
                        Color.white

                        if !clearBaseImage, let url = existingImageURL, let img = UIImage(contentsOfFile: url.path) {
                            Image(uiImage: img)
                                .resizable()
                                .scaledToFit()
                        }

                        Canvas { ctx, _ in
                            for stroke in strokes {
                                drawStroke(stroke, in: &ctx)
                            }
                            if !activePoints.isEmpty {
                                let preview = DrawingStrokeItem(
                                    tool: selectedTool,
                                    points: activePoints,
                                    color: selectedTool == .eraser ? .white : palette[selectedColorIndex].color,
                                    uiColor: selectedTool == .eraser ? .white : palette[selectedColorIndex].ui,
                                    lineWidth: selectedTool == .eraser ? strokeWidth * 2 : strokeWidth
                                )
                                drawStroke(preview, in: &ctx)
                            }
                        }
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 18, style: .continuous)
                            .stroke(Color.secondary.opacity(0.25), lineWidth: 1)
                    )
                    .contentShape(Rectangle())
                    .onAppear { canvasSize = geo.size }
                    .onChange(of: geo.size) { _, newSize in canvasSize = newSize }
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { value in
                                activePoints.append(value.location)
                            }
                            .onEnded { _ in
                                guard !activePoints.isEmpty else { return }
                                let item = DrawingStrokeItem(
                                    tool: selectedTool,
                                    points: activePoints,
                                    color: selectedTool == .eraser ? .white : palette[selectedColorIndex].color,
                                    uiColor: selectedTool == .eraser ? .white : palette[selectedColorIndex].ui,
                                    lineWidth: selectedTool == .eraser ? strokeWidth * 2 : strokeWidth
                                )
                                strokes.append(item)
                                activePoints = []
                            }
                    )
                }
                .padding(.horizontal, 14)

                // Tools & Palette Glass Dock
                VStack(spacing: 10) {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(IosDrawingTool.allCases) { tool in
                                Button {
                                    selectedTool = tool
                                } label: {
                                    VStack(spacing: 3) {
                                        Image(systemName: tool.systemImage)
                                            .font(.subheadline)
                                        Text(tool.label(isTr: isTurkish))
                                            .font(.caption2)
                                    }
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 6)
                                    .background(selectedTool == tool ? PureColors.vibrantOrange : Color.secondary.opacity(0.14))
                                    .foregroundStyle(selectedTool == tool ? .white : .primary)
                                    .clipShape(RoundedRectangle(cornerRadius: 10))
                                }
                            }
                        }
                        .padding(.horizontal, 4)
                    }

                    HStack(spacing: 12) {
                        ForEach(Array(palette.enumerated()), id: \.offset) { idx, item in
                            Button {
                                selectedColorIndex = idx
                                if selectedTool == .eraser { selectedTool = .pen }
                            } label: {
                                Circle()
                                    .fill(item.color)
                                    .frame(width: 26, height: 26)
                                    .overlay(
                                        Circle()
                                            .stroke(Color.primary, lineWidth: selectedColorIndex == idx && selectedTool != .eraser ? 2.5 : 0)
                                            .padding(-3)
                                    )
                            }
                        }
                        Spacer()
                        Circle()
                            .fill(selectedTool == .eraser ? Color.gray : palette[selectedColorIndex].color)
                            .frame(width: max(8, min(26, strokeWidth)), height: max(8, min(26, strokeWidth)))
                        Slider(value: $strokeWidth, in: 2...36, step: 1)
                            .frame(width: 110)
                    }
                }
                .padding(12)
                .pureGlassCard(cornerRadius: 18)
                .padding(.horizontal, 14)
                .padding(.bottom, 8)
            }
            .navigationTitle(isTurkish ? "Çizim Tuvali" : "Drawing Canvas")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    HStack(spacing: 12) {
                        Button(isTurkish ? "Kapat" : "Close") { dismiss() }
                        Button {
                            if !strokes.isEmpty { strokes.removeLast() }
                        } label: {
                            Image(systemName: "arrow.uturn.backward")
                        }
                        .disabled(strokes.isEmpty)

                        Button {
                            strokes.removeAll()
                            clearBaseImage = true
                        } label: {
                            Image(systemName: "trash")
                        }
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(isTurkish ? "Kaydet" : "Save") {
                        if let data = renderPngData() {
                            onSavePng(data)
                        }
                        dismiss()
                    }
                    .fontWeight(.bold)
                }
            }
        }
    }

    private func buildSwiftUIPath(for stroke: DrawingStrokeItem) -> Path {
        var path = Path()
        guard let start = stroke.points.first, let end = stroke.points.last else { return path }
        switch stroke.tool {
        case .pen, .eraser:
            path.move(to: start)
            for p in stroke.points.dropFirst() {
                path.addLine(to: p)
            }
        case .line:
            path.move(to: start)
            path.addLine(to: end)
        case .rectangle:
            let rect = CGRect(
                x: min(start.x, end.x), y: min(start.y, end.y),
                width: abs(end.x - start.x), height: abs(end.y - start.y)
            )
            path.addRect(rect)
        case .circle:
            let rect = CGRect(
                x: min(start.x, end.x), y: min(start.y, end.y),
                width: abs(end.x - start.x), height: abs(end.y - start.y)
            )
            path.addEllipse(in: rect)
        case .triangle:
            let minX = min(start.x, end.x), maxX = max(start.x, end.x)
            let minY = min(start.y, end.y), maxY = max(start.y, end.y)
            path.move(to: CGPoint(x: (minX + maxX) / 2, y: minY))
            path.addLine(to: CGPoint(x: maxX, y: maxY))
            path.addLine(to: CGPoint(x: minX, y: maxY))
            path.closeSubpath()
        case .arrow:
            path.move(to: start)
            path.addLine(to: end)
            let angle = atan2(end.y - start.y, end.x - start.x)
            let headLen: CGFloat = 16
            path.move(to: end)
            path.addLine(to: CGPoint(x: end.x - headLen * cos(angle - .pi / 6), y: end.y - headLen * sin(angle - .pi / 6)))
            path.move(to: end)
            path.addLine(to: CGPoint(x: end.x - headLen * cos(angle + .pi / 6), y: end.y - headLen * sin(angle + .pi / 6)))
        case .star:
            let cx = (start.x + end.x) / 2
            let cy = (start.y + end.y) / 2
            let outerR = max(10, hypot(end.x - start.x, end.y - start.y) / 2)
            let innerR = outerR * 0.42
            for i in 0..<10 {
                let r = i % 2 == 0 ? outerR : innerR
                let angle = Double(i) * .pi / 5.0 - .pi / 2.0
                let pt = CGPoint(x: cx + r * CGFloat(cos(angle)), y: cy + r * CGFloat(sin(angle)))
                if i == 0 { path.move(to: pt) } else { path.addLine(to: pt) }
            }
            path.closeSubpath()
        }
        return path
    }

    private func drawStroke(_ stroke: DrawingStrokeItem, in context: inout GraphicsContext) {
        let p = buildSwiftUIPath(for: stroke)
        context.stroke(
            p,
            with: .color(stroke.color),
            style: StrokeStyle(lineWidth: stroke.lineWidth, lineCap: .round, lineJoin: .round)
        )
    }

    private func renderPngData() -> Data? {
        let targetSize = CGSize(width: max(300, canvasSize.width), height: max(360, canvasSize.height))
        let renderer = UIGraphicsImageRenderer(size: targetSize)
        let img = renderer.image { _ in
            UIColor.white.setFill()
            UIRectFill(CGRect(origin: .zero, size: targetSize))

            if !clearBaseImage, let url = existingImageURL, let base = UIImage(contentsOfFile: url.path) {
                base.draw(in: CGRect(origin: .zero, size: targetSize))
            }

            for stroke in strokes {
                let uiPath = UIBezierPath(cgPath: buildSwiftUIPath(for: stroke).cgPath)
                uiPath.lineWidth = stroke.lineWidth
                uiPath.lineCapStyle = .round
                uiPath.lineJoinStyle = .round
                stroke.uiColor.setStroke()
                uiPath.stroke()
            }
        }
        return img.pngData()
    }
}
