import Combine
import CoreBluetooth
import Foundation

final class BLEGatewayPeripheral: NSObject, ObservableObject {
    static let serviceUUID = CBUUID(string: "c91249f4-d2a1-4d93-8f66-ce0a24753b9d")
    static let controlUplinkUUID = CBUUID(string: "fe398f65-82ad-4e3e-b46b-5978725413c1")
    static let controlDownlinkUUID = CBUUID(string: "1d40843f-bab8-4cec-a70e-72e839205998")

    @Published private(set) var bluetoothState = "Initializing"
    @Published private(set) var gatewayState = "Stopped"
    @Published private(set) var lastEvent = "None"
    @Published private(set) var subscriberCount = 0
    @Published private(set) var isEnabled = false

    private lazy var peripheralManager = CBPeripheralManager(
        delegate: self,
        queue: nil,
        options: [CBPeripheralManagerOptionRestoreIdentifierKey: "com.jflashman.jglass.gateway"]
    )
    private var uplinkCharacteristic: CBMutableCharacteristic?
    private var downlinkCharacteristic: CBMutableCharacteristic?
    private let reassembler = BLEControlReassembler()
    private let reachability = NetworkReachabilityMonitor()
    private var pendingIndications: [Data] = []
    private var nextStreamID: UInt16 = 1

    override init() {
        super.init()
        _ = peripheralManager
    }

    func start() {
        isEnabled = true
        lastEvent = "Start requested"
        publishIfPossible()
    }

    func stop() {
        isEnabled = false
        peripheralManager.stopAdvertising()
        peripheralManager.removeAllServices()
        uplinkCharacteristic = nil
        downlinkCharacteristic = nil
        pendingIndications.removeAll(keepingCapacity: false)
        reassembler.reset()
        subscriberCount = 0
        gatewayState = "Stopped"
        lastEvent = "Gateway stopped"
    }

    private func publishIfPossible() {
        guard isEnabled else { return }
        guard peripheralManager.state == .poweredOn else {
            gatewayState = "Waiting for Bluetooth"
            return
        }
        guard uplinkCharacteristic == nil, downlinkCharacteristic == nil else {
            startAdvertisingIfNeeded()
            return
        }

        let uplink = CBMutableCharacteristic(
            type: Self.controlUplinkUUID,
            properties: [.write],
            value: nil,
            permissions: [.writeable]
        )
        let downlink = CBMutableCharacteristic(
            type: Self.controlDownlinkUUID,
            properties: [.indicate, .notify],
            value: nil,
            permissions: []
        )
        let service = CBMutableService(type: Self.serviceUUID, primary: true)
        service.characteristics = [uplink, downlink]

        uplinkCharacteristic = uplink
        downlinkCharacteristic = downlink
        gatewayState = "Publishing service"
        peripheralManager.add(service)
    }

    private func startAdvertisingIfNeeded() {
        guard isEnabled, !peripheralManager.isAdvertising else { return }
        gatewayState = "Starting advertisement"
        peripheralManager.startAdvertising([
            CBAdvertisementDataServiceUUIDsKey: [Self.serviceUUID],
            CBAdvertisementDataLocalNameKey: "JGlass"
        ])
    }

    private func handleCompletedMessage(_ data: Data) {
        do {
            let request = try GatewayMessage.decode(data)
            let response = try GatewayMessageProcessor.response(
                to: request,
                internetReachable: reachability.isReachable
            )
            lastEvent = "Received \(request.type)"
            try queue(response: response)
        } catch let error as GatewayMessageError {
            lastEvent = "Rejected message: \(error.stableCode)"
        } catch {
            lastEvent = "Rejected malformed message"
        }
    }

    private func queue(response: GatewayMessage) throws {
        guard subscriberCount > 0 else {
            lastEvent = "No downlink subscriber"
            return
        }

        let data = try response.encoded()
        let streamID = nextStreamID
        nextStreamID &+= 1
        if nextStreamID == 0 { nextStreamID = 1 }

        let frames = try BLEControlFrame.fragment(data, streamID: streamID, maximumPacketLength: 20)
        guard pendingIndications.count + frames.count <= 512 else {
            pendingIndications.removeAll(keepingCapacity: false)
            throw BLEFramingError.tooManyFragments
        }
        pendingIndications.append(contentsOf: frames)
        lastEvent = "Queued \(response.type)"
        flushPendingIndications()
    }

    private func flushPendingIndications() {
        guard let downlinkCharacteristic else { return }
        while let next = pendingIndications.first {
            let accepted = peripheralManager.updateValue(
                next,
                for: downlinkCharacteristic,
                onSubscribedCentrals: nil
            )
            guard accepted else { return }
            pendingIndications.removeFirst()
        }
    }

    private func restoreCharacteristics(from services: [CBMutableService]) {
        guard let service = services.first(where: { $0.uuid == Self.serviceUUID }) else { return }
        uplinkCharacteristic = service.characteristics?
            .first(where: { $0.uuid == Self.controlUplinkUUID }) as? CBMutableCharacteristic
        downlinkCharacteristic = service.characteristics?
            .first(where: { $0.uuid == Self.controlDownlinkUUID }) as? CBMutableCharacteristic
    }
}

extension BLEGatewayPeripheral: CBPeripheralManagerDelegate {
    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        bluetoothState = peripheral.state.displayName
        switch peripheral.state {
        case .poweredOn:
            publishIfPossible()
        case .poweredOff, .resetting:
            gatewayState = isEnabled ? "Waiting for Bluetooth" : "Stopped"
        case .unauthorized:
            gatewayState = "Bluetooth permission denied"
        case .unsupported:
            gatewayState = "BLE peripheral unavailable"
        case .unknown:
            gatewayState = "Bluetooth state unknown"
        @unknown default:
            gatewayState = "Unknown Bluetooth state"
        }
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didAdd service: CBService, error: Error?) {
        if error != nil {
            gatewayState = "Service publish failed"
            lastEvent = "Core Bluetooth could not publish the service"
            return
        }
        lastEvent = "JGlass service published"
        startAdvertisingIfNeeded()
    }

    func peripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: Error?) {
        if error != nil {
            gatewayState = "Advertisement failed"
            lastEvent = "Core Bluetooth could not start advertising"
            return
        }
        gatewayState = "Advertising"
        lastEvent = "Waiting for Glass"
    }

    func peripheralManager(
        _ peripheral: CBPeripheralManager,
        central: CBCentral,
        didSubscribeTo characteristic: CBCharacteristic
    ) {
        guard characteristic.uuid == Self.controlDownlinkUUID else { return }
        subscriberCount += 1
        gatewayState = "Glass subscribed"
        lastEvent = "Downlink subscribed"
        flushPendingIndications()
    }

    func peripheralManager(
        _ peripheral: CBPeripheralManager,
        central: CBCentral,
        didUnsubscribeFrom characteristic: CBCharacteristic
    ) {
        guard characteristic.uuid == Self.controlDownlinkUUID else { return }
        subscriberCount = max(0, subscriberCount - 1)
        gatewayState = peripheral.isAdvertising ? "Advertising" : "Published"
        lastEvent = "Downlink unsubscribed"
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveWrite requests: [CBATTRequest]) {
        for request in requests {
            guard request.characteristic.uuid == Self.controlUplinkUUID else {
                peripheral.respond(to: request, withResult: .requestNotSupported)
                continue
            }
            guard request.offset == 0 else {
                peripheral.respond(to: request, withResult: .invalidOffset)
                continue
            }
            guard let packet = request.value else {
                peripheral.respond(to: request, withResult: .invalidAttributeValueLength)
                continue
            }

            do {
                let result = try reassembler.accept(packet)
                peripheral.respond(to: request, withResult: .success)
                switch result {
                case .completed(let message):
                    handleCompletedMessage(message)
                case .cancelled:
                    lastEvent = "Control stream cancelled"
                case .duplicate:
                    lastEvent = "Duplicate fragment ignored"
                case .waiting:
                    break
                }
            } catch let error as BLEFramingError {
                peripheral.respond(to: request, withResult: .invalidPdu)
                lastEvent = "Rejected frame: \(error.stableCode)"
            } catch {
                peripheral.respond(to: request, withResult: .unlikelyError)
                lastEvent = "Rejected frame"
            }
        }
    }

    func peripheralManagerIsReady(toUpdateSubscribers peripheral: CBPeripheralManager) {
        flushPendingIndications()
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, willRestoreState dict: [String: Any]) {
        if let services = dict[CBPeripheralManagerRestoredStateServicesKey] as? [CBMutableService] {
            restoreCharacteristics(from: services)
        }
        isEnabled = true
        gatewayState = "State restored"
        lastEvent = "Core Bluetooth restored state"
    }
}

private extension CBManagerState {
    var displayName: String {
        switch self {
        case .unknown: "Unknown"
        case .resetting: "Resetting"
        case .unsupported: "Unsupported"
        case .unauthorized: "Unauthorized"
        case .poweredOff: "Powered off"
        case .poweredOn: "Powered on"
        @unknown default: "Unknown"
        }
    }
}

private extension GatewayMessageError {
    var stableCode: String {
        switch self {
        case .invalidIdentifier: "invalid_identifier"
        case .invalidPayload: "invalid_payload"
        case .invalidSequence: "invalid_sequence"
        case .messageTooLarge: "message_too_large"
        case .unsupportedProtocol: "unsupported_protocol"
        case .unsupportedType: "unsupported_type"
        }
    }
}

private extension BLEFramingError {
    var stableCode: String {
        switch self {
        case .invalidFlags: "invalid_flags"
        case .invalidKind: "invalid_kind"
        case .invalidLength: "invalid_length"
        case .invalidMagic: "invalid_magic"
        case .invalidSequence: "invalid_sequence"
        case .messageTooLarge: "message_too_large"
        case .missingFirstFragment: "missing_first"
        case .packetTooSmall: "packet_too_small"
        case .tooManyFragments: "too_many_fragments"
        case .tooManyStreams: "too_many_streams"
        case .truncatedHeader: "truncated_header"
        case .unsupportedVersion: "unsupported_version"
        }
    }
}
