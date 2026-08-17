import AVFAudio
import Combine
import Foundation

final class AudioRouteProbe: ObservableObject {
    @Published private(set) var state = "Stopped"
    @Published private(set) var inputRoute = "None"
    @Published private(set) var outputRoute = "None"
    @Published private(set) var inputFormat = "Unavailable"
    @Published private(set) var engineStrategy = "Stopped"
    @Published private(set) var buffersObserved: UInt64 = 0
    @Published private(set) var inputLevel = 0.0
    @Published private(set) var isRunning = false

    private let session = AVAudioSession.sharedInstance()
    private var engine: AVAudioEngine?
    private var silentSource: AVAudioSourceNode?
    private var observers: [NSObjectProtocol] = []
    private var lastLevelUpdate = 0.0
    private var tapBufferCount: UInt64 = 0

    init() {
        let center = NotificationCenter.default
        observers = [
            center.addObserver(
                forName: AVAudioSession.routeChangeNotification,
                object: session,
                queue: .main
            ) { [weak self] _ in self?.refreshRoute() },
            center.addObserver(
                forName: AVAudioSession.interruptionNotification,
                object: session,
                queue: .main
            ) { [weak self] _ in self?.handleInterruption() },
            center.addObserver(
                forName: AVAudioSession.mediaServicesWereResetNotification,
                object: session,
                queue: .main
            ) { [weak self] _ in self?.handleMediaServicesReset() }
        ]
        refreshRoute()
    }

    deinit {
        observers.forEach(NotificationCenter.default.removeObserver)
        stopEngine(deactivateSession: true)
    }

    func requestPermissionAndStart() {
        state = "Requesting microphone permission"
        AVAudioApplication.requestRecordPermission { [weak self] granted in
            DispatchQueue.main.async {
                guard let self else { return }
                if granted {
                    self.start()
                } else {
                    self.state = "Microphone permission denied"
                }
            }
        }
    }

    func stop() {
        stopEngine(deactivateSession: true)
        state = "Stopped"
        refreshRoute()
    }

    private func start() {
        stopEngine(deactivateSession: false)
        do {
            try session.setCategory(.playAndRecord, mode: .voiceChat, options: [.allowBluetoothHFP])
            try session.setActive(true)

            guard let hfpInput = session.availableInputs?.first(where: { $0.portType == .bluetoothHFP }) else {
                state = "No Bluetooth HFP input available"
                try? session.setActive(false, options: .notifyOthersOnDeactivation)
                refreshRoute()
                return
            }
            try session.setPreferredInput(hfpInput)

            let engine = AVAudioEngine()
            let input = engine.inputNode
            let inputFormat = input.inputFormat(forBus: 0)
            guard inputFormat.sampleRate > 0, inputFormat.channelCount > 0 else {
                throw AudioProbeError.inputUnavailable
            }
            let outputFormat = engine.outputNode.inputFormat(forBus: 0)
            guard outputFormat.sampleRate > 0, outputFormat.channelCount > 0 else {
                throw AudioProbeError.outputUnavailable
            }
            self.inputFormat = inputFormat.diagnosticDescription
            tapBufferCount = 0
            buffersObserved = 0

            input.installTap(onBus: 0, bufferSize: 512, format: inputFormat) { [weak self] buffer, _ in
                self?.updateLevel(from: buffer)
            }

            let silentSource = AVAudioSourceNode(format: outputFormat) {
                _, _, _, audioBufferList -> OSStatus in
                let buffers = UnsafeMutableAudioBufferListPointer(audioBufferList)
                for buffer in buffers {
                    guard let data = buffer.mData else { continue }
                    data.initializeMemory(
                        as: UInt8.self,
                        repeating: 0,
                        count: Int(buffer.mDataByteSize)
                    )
                }
                return 0
            }
            engine.attach(silentSource)
            engine.connect(silentSource, to: engine.mainMixerNode, format: outputFormat)
            engine.prepare()
            try engine.start()

            self.engine = engine
            self.silentSource = silentSource
            isRunning = true
            engineStrategy = "Raw input + active silent output"
            state = "Running full-duplex probe"
            refreshRoute()
        } catch {
            stopEngine(deactivateSession: true)
            state = "Probe failed: \(error.localizedDescription)"
            refreshRoute()
        }
    }

    private func stopEngine(deactivateSession: Bool) {
        if let engine {
            engine.inputNode.removeTap(onBus: 0)
            engine.stop()
            if let silentSource {
                engine.detach(silentSource)
            }
        }
        engine = nil
        silentSource = nil
        inputLevel = 0
        inputFormat = "Unavailable"
        engineStrategy = "Stopped"
        tapBufferCount = 0
        buffersObserved = 0
        isRunning = false
        if deactivateSession {
            try? session.setPreferredInput(nil)
            try? session.setActive(false, options: .notifyOthersOnDeactivation)
        }
    }

    private func refreshRoute() {
        inputRoute = session.currentRoute.inputs.map(\.portType.rawValue).joined(separator: ", ")
        outputRoute = session.currentRoute.outputs.map(\.portType.rawValue).joined(separator: ", ")
        if inputRoute.isEmpty { inputRoute = "None" }
        if outputRoute.isEmpty { outputRoute = "None" }

        if isRunning {
            let hasHFPInput = session.currentRoute.inputs.contains { $0.portType == .bluetoothHFP }
            let hasHFPOutput = session.currentRoute.outputs.contains { $0.portType == .bluetoothHFP }
            if !hasHFPInput || !hasHFPOutput {
                state = "HFP route fell back to the phone"
            }
        }
    }

    private func updateLevel(from buffer: AVAudioPCMBuffer) {
        tapBufferCount &+= 1
        let now = ProcessInfo.processInfo.systemUptime
        guard now - lastLevelUpdate >= 0.1 else { return }
        lastLevelUpdate = now

        let normalized = normalizedLevel(from: buffer)
        let observed = tapBufferCount

        DispatchQueue.main.async { [weak self] in
            self?.buffersObserved = observed
            self?.inputLevel = normalized ?? 0
        }
    }

    private func normalizedLevel(from buffer: AVAudioPCMBuffer) -> Double? {
        let frames = Int(buffer.frameLength)
        let channels = Int(buffer.format.channelCount)
        guard frames > 0, channels > 0 else { return nil }

        let pointerCount = buffer.format.isInterleaved ? 1 : channels
        let samplesPerPointer = buffer.format.isInterleaved ? frames * channels : frames
        var sum = 0.0
        var sampleCount = 0

        switch buffer.format.commonFormat {
        case .pcmFormatFloat32:
            guard let channelData = buffer.floatChannelData else { return nil }
            for channel in 0..<pointerCount {
                for index in 0..<samplesPerPointer {
                    let sample = Double(channelData[channel][index])
                    sum += sample * sample
                }
                sampleCount += samplesPerPointer
            }
        case .pcmFormatInt16:
            guard let channelData = buffer.int16ChannelData else { return nil }
            for channel in 0..<pointerCount {
                for index in 0..<samplesPerPointer {
                    let sample = Double(channelData[channel][index]) / 32_768.0
                    sum += sample * sample
                }
                sampleCount += samplesPerPointer
            }
        case .pcmFormatInt32:
            guard let channelData = buffer.int32ChannelData else { return nil }
            for channel in 0..<pointerCount {
                for index in 0..<samplesPerPointer {
                    let sample = Double(channelData[channel][index]) / 2_147_483_648.0
                    sum += sample * sample
                }
                sampleCount += samplesPerPointer
            }
        default:
            return nil
        }

        guard sampleCount > 0 else { return nil }
        let rootMeanSquare = sqrt(sum / Double(sampleCount))
        return min(1, max(0, rootMeanSquare * 8))
    }

    private func handleInterruption() {
        guard isRunning else { return }
        stopEngine(deactivateSession: false)
        state = "Interrupted; tap Start to retry"
        refreshRoute()
    }

    private func handleMediaServicesReset() {
        stopEngine(deactivateSession: false)
        state = "Audio service reset; tap Start to retry"
        refreshRoute()
    }
}

private extension AVAudioFormat {
    var diagnosticDescription: String {
        let formatName: String
        switch commonFormat {
        case .pcmFormatFloat32: formatName = "Float32"
        case .pcmFormatFloat64: formatName = "Float64"
        case .pcmFormatInt16: formatName = "Int16"
        case .pcmFormatInt32: formatName = "Int32"
        case .otherFormat: formatName = "Other"
        @unknown default: formatName = "Unknown"
        }

        let layout = isInterleaved ? "interleaved" : "planar"
        return String(
            format: "%.0f Hz · %d ch · %@ · %@",
            sampleRate,
            channelCount,
            formatName,
            layout
        )
    }
}

private enum AudioProbeError: LocalizedError {
    case inputUnavailable
    case outputUnavailable

    var errorDescription: String? {
        switch self {
        case .inputUnavailable: "Audio input is unavailable"
        case .outputUnavailable: "Audio output is unavailable"
        }
    }
}
