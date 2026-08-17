import SwiftUI

struct ContentView: View {
    @StateObject private var bluetooth = BLEGatewayPeripheral()
    @StateObject private var audio = AudioRouteProbe()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    diagnosticNotice
                    bluetoothCard
                    audioCard
                }
                .padding()
            }
            .navigationTitle("JGlass Companion")
        }
    }

    private var diagnosticNotice: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("Feasibility diagnostic", systemImage: "wrench.and.screwdriver")
                .font(.headline)
            Text("This build exchanges only bounded hello and ping messages. It does not carry speech, notifications, credentials, or computer actions.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .cardStyle()
    }

    private var bluetoothCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("Glass BLE gateway", systemImage: "antenna.radiowaves.left.and.right")
                .font(.headline)

            StatusRow(label: "Bluetooth", value: bluetooth.bluetoothState)
            StatusRow(label: "Gateway", value: bluetooth.gatewayState)
            StatusRow(label: "Subscribers", value: String(bluetooth.subscriberCount))
            StatusRow(label: "Last event", value: bluetooth.lastEvent)

            Button(bluetooth.isEnabled ? "Stop BLE Gateway" : "Start BLE Gateway") {
                bluetooth.isEnabled ? bluetooth.stop() : bluetooth.start()
            }
            .buttonStyle(.borderedProminent)
        }
        .cardStyle()
    }

    private var audioCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("Glass HFP route", systemImage: "waveform")
                .font(.headline)

            StatusRow(label: "Probe", value: audio.state)
            StatusRow(label: "Input", value: audio.inputRoute)
            StatusRow(label: "Output", value: audio.outputRoute)
            StatusRow(label: "Format", value: audio.inputFormat)
            StatusRow(label: "Engine", value: audio.engineStrategy)
            StatusRow(label: "Buffers", value: String(audio.buffersObserved))

            VStack(alignment: .leading, spacing: 4) {
                Text("Transient input level")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                ProgressView(value: audio.inputLevel)
            }

            Text("The probe requests the Bluetooth HFP input, opens full-duplex voice I/O, discards microphone buffers immediately, and displays only an ephemeral level.")
                .font(.caption)
                .foregroundStyle(.secondary)

            Button(audio.isRunning ? "Stop HFP Probe" : "Start HFP Probe") {
                audio.isRunning ? audio.stop() : audio.requestPermissionAndStart()
            }
            .buttonStyle(.borderedProminent)
        }
        .cardStyle()
    }
}

private struct StatusRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label)
                .foregroundStyle(.secondary)
            Spacer(minLength: 16)
            Text(value)
                .multilineTextAlignment(.trailing)
                .textSelection(.enabled)
        }
        .font(.subheadline)
    }
}

private extension View {
    func cardStyle() -> some View {
        self
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding()
            .background(.background)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .shadow(color: .black.opacity(0.08), radius: 8, y: 3)
    }
}
