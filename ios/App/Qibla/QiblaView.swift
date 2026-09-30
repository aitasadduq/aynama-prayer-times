import SharedLogic
import SwiftData
import SwiftUI

struct QiblaView: View {
    @Query(sort: [SortDescriptor(\ProfileRecord.sortOrder), SortDescriptor(\ProfileRecord.profileID)])
    private var records: [ProfileRecord]
    @StateObject private var location = LocationService()
    @State private var aligned = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    private var profile: Profile? { (records.first(where: \.isGps) ?? records.first)?.profile }

    var body: some View {
        Group {
            if let profile {
                TimelineView(.periodic(from: .now, by: 60)) { _ in instrument(profile) }
            } else { ProfileRequiredView(title: "Set up a prayer profile to find Qibla direction") }
        }
        .onAppear { if profile != nil { location.startCompass() } }
        .onDisappear { location.stop() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active, profile != nil { location.startCompass() }
            else { location.stop() }
        }
    }

    private func instrument(_ profile: Profile) -> some View {
        let date = CalendarDate.from(AppClock.now, in: profile.effectiveTimeZone)
        let phase = PrayerSchedule.times(profile: profile, date: date).map {
            derivePhase($0, asrMadhab: profile.asrMadhab, now: ClockTime.from(AppClock.now, in: profile.effectiveTimeZone))
        } ?? .isha
        let surface = phase.surface
        let latitude = location.location?.coordinate.latitude ?? profile.latitude
        let longitude = location.location?.coordinate.longitude ?? profile.longitude
        let bearing = QiblaCalculator.bearing(latitude: latitude, longitude: longitude)
        let distance = QiblaCalculator.distanceKm(latitude: latitude, longitude: longitude)
        let delta = ((bearing - (location.heading ?? 0)).truncatingRemainder(dividingBy: 360) + 540).truncatingRemainder(dividingBy: 360) - 180
        let panel = surface.prefersLightForeground ? AynamaColor.ink : AynamaColor.parchment
        return ScrollView {
            VStack(spacing: 24) {
                HStack {
                    Text("Qibla").font(AynamaFont.qiblaTitle)
                    Spacer()
                    Text(phaseDisplayName(phase, on: date).uppercased())
                        .font(AynamaFont.bodySM).tracking(1).foregroundStyle(surface.prefersLightForeground ? AynamaColor.saffron : AynamaColor.saffronInk)
                }.padding(16).background(panel, in: RoundedRectangle(cornerRadius: 16))
                Text(hint(delta: delta))
                    .font(AynamaFont.bodySM)
                    .foregroundStyle(aligned ? surface.activeForeground : surface.foregroundMuted)
                    .padding(.horizontal, 16).padding(.vertical, 10).background(panel, in: Capsule())
                    .accessibilityIdentifier("qibla-hint")
                ZStack {
                    Circle().stroke(panel, lineWidth: 28).frame(width: 252, height: 252)
                    QiblaArrow().fill(AynamaColor.saffron)
                        .frame(width: 140, height: 200).rotationEffect(.degrees(bearing))
                    Text("N").font(AynamaFont.north)
                        .rotationEffect(.degrees(location.heading ?? 0)).offset(y: -126)
                }
                .frame(width: 280, height: 280)
                .rotationEffect(.degrees(-(location.heading ?? 0)))
                .animation(reduceMotion ? nil : .spring(response: 0.6, dampingFraction: 0.8), value: location.heading)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Qibla bearing \(Int(bearing.rounded())) degrees from true north")
                VStack(spacing: 8) {
                    Text("\(Int(bearing.rounded()))°").font(AynamaFont.qiblaDegree)
                    Text("\(Int(distance.rounded()).formatted()) km \(location.location == nil ? "from \(profile.name) " : "")to the Kaaba")
                        .font(AynamaFont.bodySM).foregroundStyle(surface.foregroundMuted)
                        .multilineTextAlignment(.center)
                }.padding(20).frame(maxWidth: .infinity)
                    .background(panel, in: RoundedRectangle(cornerRadius: 16))
                if location.heading != nil, location.accuracy > 15 || location.accuracy < 0 {
                    Text("Hold phone flat and move in a figure-8 to calibrate")
                        .font(AynamaFont.bodySM).padding(12)
                        .background(panel, in: RoundedRectangle(cornerRadius: 8))
                } else if !location.hasCompass || location.heading == nil {
                    Text("Compass unavailable. The arrow shows the bearing from true north.")
                        .font(AynamaFont.bodySM).foregroundStyle(surface.foregroundMuted).multilineTextAlignment(.center)
                }
            }.padding(.horizontal, 24).padding(.vertical, 16)
        }
        .foregroundStyle(surface.foreground).timeOfDaySurface(surface)
        .onChange(of: delta) { _, value in
            let next = location.heading != nil && abs(value) < (aligned ? 7 : 5)
            if next && !aligned { UISelectionFeedbackGenerator().selectionChanged() }
            aligned = next
        }
    }

    private func hint(delta: Double) -> String {
        guard location.heading != nil else { return "Bearing from true north" }
        if aligned { return "— Aligned —" }
        return "Turn \(delta >= 0 ? "right" : "left") \(Int(abs(delta).rounded()))°"
    }
}

/// Letterpress arrow, with no needle, tick dial, cardinal legend, or additional ring.
struct QiblaArrow: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.midX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.height * 0.4))
        path.addLine(to: CGPoint(x: rect.width * 0.61, y: rect.height * 0.32))
        path.addLine(to: CGPoint(x: rect.width * 0.61, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.width * 0.39, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.width * 0.39, y: rect.height * 0.32))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.height * 0.4))
        path.closeSubpath()
        return path
    }
}
