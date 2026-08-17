import Foundation

enum BLEFrameKind: UInt8 {
    case control = 1
    case cancel = 2
}

struct BLEControlFrame: Equatable {
    static let magic: UInt8 = 0x4A
    static let version: UInt8 = 1
    static let headerLength = 10

    let kind: BLEFrameKind
    let isFirst: Bool
    let isFinal: Bool
    let streamID: UInt16
    let sequence: UInt16
    let totalLength: UInt16
    let payload: Data

    func encoded() -> Data {
        var data = Data(capacity: Self.headerLength + payload.count)
        data.append(Self.magic)
        data.append(Self.version)
        data.append(kind.rawValue)
        data.append((isFirst ? 0x01 : 0) | (isFinal ? 0x02 : 0))
        data.append(contentsOf: streamID.bigEndianBytes)
        data.append(contentsOf: sequence.bigEndianBytes)
        data.append(contentsOf: totalLength.bigEndianBytes)
        data.append(payload)
        return data
    }

    static func decode(_ data: Data) throws -> BLEControlFrame {
        guard data.count >= headerLength else { throw BLEFramingError.truncatedHeader }
        let bytes = [UInt8](data)
        guard bytes[0] == magic else { throw BLEFramingError.invalidMagic }
        guard bytes[1] == version else { throw BLEFramingError.unsupportedVersion }
        guard let kind = BLEFrameKind(rawValue: bytes[2]) else { throw BLEFramingError.invalidKind }
        guard bytes[3] & 0xFC == 0 else { throw BLEFramingError.invalidFlags }

        return BLEControlFrame(
            kind: kind,
            isFirst: bytes[3] & 0x01 != 0,
            isFinal: bytes[3] & 0x02 != 0,
            streamID: UInt16(bigEndianBytes: bytes[4], bytes[5]),
            sequence: UInt16(bigEndianBytes: bytes[6], bytes[7]),
            totalLength: UInt16(bigEndianBytes: bytes[8], bytes[9]),
            payload: data.dropFirst(headerLength)
        )
    }

    static func fragment(
        _ message: Data,
        streamID: UInt16,
        maximumPacketLength: Int
    ) throws -> [Data] {
        guard message.count <= JGlassGatewayProtocol.maximumControlMessageBytes,
              message.count <= Int(UInt16.max) else {
            throw BLEFramingError.messageTooLarge
        }
        guard maximumPacketLength > headerLength else {
            throw BLEFramingError.packetTooSmall
        }

        let payloadCapacity = maximumPacketLength - headerLength
        let fragmentCount = max(1, Int(ceil(Double(message.count) / Double(payloadCapacity))))
        guard fragmentCount <= Int(UInt16.max) else { throw BLEFramingError.tooManyFragments }

        return (0..<fragmentCount).map { index in
            let lowerBound = min(index * payloadCapacity, message.count)
            let upperBound = min(lowerBound + payloadCapacity, message.count)
            let payload = message.subdata(in: lowerBound..<upperBound)
            return BLEControlFrame(
                kind: .control,
                isFirst: index == 0,
                isFinal: index == fragmentCount - 1,
                streamID: streamID,
                sequence: UInt16(index),
                totalLength: index == 0 ? UInt16(message.count) : 0,
                payload: payload
            ).encoded()
        }
    }

    static func cancellation(streamID: UInt16, sequence: UInt16) -> Data {
        BLEControlFrame(
            kind: .cancel,
            isFirst: false,
            isFinal: true,
            streamID: streamID,
            sequence: sequence,
            totalLength: 0,
            payload: Data()
        ).encoded()
    }
}

final class BLEControlReassembler {
    enum Result: Equatable {
        case waiting
        case completed(Data)
        case duplicate
        case cancelled
    }

    private struct Assembly {
        var bytes = Data()
        let expectedLength: Int
        var nextSequence: UInt16
        var updatedAt: Date
    }

    private var assemblies: [UInt16: Assembly] = [:]
    private let timeout: TimeInterval

    init(timeout: TimeInterval = 5) {
        self.timeout = timeout
    }

    func accept(_ packet: Data, now: Date = Date()) throws -> Result {
        expireAssemblies(olderThan: now.addingTimeInterval(-timeout))
        let frame = try BLEControlFrame.decode(packet)

        if frame.kind == .cancel {
            assemblies.removeValue(forKey: frame.streamID)
            return .cancelled
        }

        if frame.isFirst {
            guard frame.sequence == 0 else { throw BLEFramingError.invalidSequence }
            guard frame.totalLength <= JGlassGatewayProtocol.maximumControlMessageBytes else {
                throw BLEFramingError.messageTooLarge
            }
            if assemblies[frame.streamID] != nil {
                return .duplicate
            }
            guard assemblies.isEmpty else { throw BLEFramingError.tooManyStreams }
            assemblies[frame.streamID] = Assembly(
                expectedLength: Int(frame.totalLength),
                nextSequence: 0,
                updatedAt: now
            )
        } else if frame.totalLength != 0 {
            throw BLEFramingError.invalidLength
        }

        guard var assembly = assemblies[frame.streamID] else {
            throw BLEFramingError.missingFirstFragment
        }
        if frame.sequence < assembly.nextSequence { return .duplicate }
        guard frame.sequence == assembly.nextSequence else { throw BLEFramingError.invalidSequence }
        guard assembly.bytes.count + frame.payload.count <= assembly.expectedLength else {
            assemblies.removeValue(forKey: frame.streamID)
            throw BLEFramingError.invalidLength
        }

        assembly.bytes.append(frame.payload)
        assembly.nextSequence &+= 1
        assembly.updatedAt = now

        if frame.isFinal {
            assemblies.removeValue(forKey: frame.streamID)
            guard assembly.bytes.count == assembly.expectedLength else {
                throw BLEFramingError.invalidLength
            }
            return .completed(assembly.bytes)
        }

        assemblies[frame.streamID] = assembly
        return .waiting
    }

    func reset() {
        assemblies.removeAll(keepingCapacity: false)
    }

    private func expireAssemblies(olderThan cutoff: Date) {
        assemblies = assemblies.filter { $0.value.updatedAt >= cutoff }
    }
}

enum BLEFramingError: Error, Equatable {
    case invalidFlags
    case invalidKind
    case invalidLength
    case invalidMagic
    case invalidSequence
    case messageTooLarge
    case missingFirstFragment
    case packetTooSmall
    case tooManyFragments
    case tooManyStreams
    case truncatedHeader
    case unsupportedVersion
}

private extension UInt16 {
    var bigEndianBytes: [UInt8] {
        [UInt8((self >> 8) & 0xFF), UInt8(self & 0xFF)]
    }

    init(bigEndianBytes high: UInt8, _ low: UInt8) {
        self = (UInt16(high) << 8) | UInt16(low)
    }
}
