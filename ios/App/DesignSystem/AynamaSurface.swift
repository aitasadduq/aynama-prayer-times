import SwiftUI

struct NeutralPalette {
    let scheme: ColorScheme
    var background: Color { scheme == .dark ? AynamaColor.ink : AynamaColor.parchment }
    var foreground: Color { scheme == .dark ? AynamaColor.parchment : AynamaColor.ink }
    var muted: Color { scheme == .dark ? AynamaColor.parchmentMuted : AynamaColor.inkMuted }
    var accent: Color { scheme == .dark ? AynamaColor.saffron : AynamaColor.saffronInk }
}

private struct NeutralSurface: ViewModifier {
    @Environment(\.colorScheme) private var scheme
    func body(content: Content) -> some View {
        let palette = NeutralPalette(scheme: scheme)
        content
            .font(AynamaFont.body)
            .foregroundStyle(palette.foreground)
            .tint(palette.accent)
            .scrollContentBackground(.hidden)
            .background(palette.background.ignoresSafeArea())
    }
}

extension View {
    func neutralSurface() -> some View { modifier(NeutralSurface()) }
}

struct AddProfileButton: View {
    var usesGlass = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "plus")
                .font(.system(size: 22, weight: .medium))
                .foregroundStyle(AynamaColor.ink)
                .frame(width: 56, height: 56)
                .modifier(AddButtonSurface(usesGlass: usesGlass))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("New profile")
    }
}

private struct AddButtonSurface: ViewModifier {
    let usesGlass: Bool
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    @ViewBuilder
    func body(content: Content) -> some View {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *), usesGlass, !reduceTransparency {
            content.glassEffect(.regular.tint(AynamaColor.saffron).interactive(), in: Circle())
        } else {
            content.background(AynamaColor.saffron, in: Circle())
        }
        #else
        content.background(AynamaColor.saffron, in: Circle())
        #endif
    }
}

struct ProfileRequiredView: View {
    let title: String
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title).font(AynamaFont.displayMD)
            Text("Add a location in Prayers or Settings.")
                .font(AynamaFont.bodyLG)
                .foregroundStyle(NeutralPalette(scheme: scheme).muted)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .neutralSurface()
    }
}
