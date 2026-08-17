import Foundation

enum JGlassGatewayProtocol {
    static let version = "jglass/1"
    static let maximumControlMessageBytes = 4 * 1024
    static let maximumIdentifierBytes = 128
}

struct GatewayPayload: Codable, Equatable {
    var transport: String?
    var role: String?
    var acceptedProtocol: String?
    var internetReachable: Bool?
    var code: String?

    init(
        transport: String? = nil,
        role: String? = nil,
        acceptedProtocol: String? = nil,
        internetReachable: Bool? = nil,
        code: String? = nil
    ) {
        self.transport = transport
        self.role = role
        self.acceptedProtocol = acceptedProtocol
        self.internetReachable = internetReachable
        self.code = code
    }
}

struct GatewayMessage: Codable, Equatable {
    let protocolVersion: String
    let type: String
    let sessionId: String
    let requestId: String
    let sequence: Int
    let payload: GatewayPayload

    enum CodingKeys: String, CodingKey {
        case protocolVersion = "protocol"
        case type
        case sessionId
        case requestId
        case sequence
        case payload
    }

    static func decode(_ data: Data) throws -> GatewayMessage {
        guard data.count <= JGlassGatewayProtocol.maximumControlMessageBytes else {
            throw GatewayMessageError.messageTooLarge
        }

        let message = try JSONDecoder().decode(GatewayMessage.self, from: data)
        try message.validateEnvelope()
        return message
    }

    func encoded() throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        let data = try encoder.encode(self)
        guard data.count <= JGlassGatewayProtocol.maximumControlMessageBytes else {
            throw GatewayMessageError.messageTooLarge
        }
        return data
    }

    private func validateEnvelope() throws {
        guard protocolVersion == JGlassGatewayProtocol.version else {
            throw GatewayMessageError.unsupportedProtocol
        }
        guard sequence >= 0 else {
            throw GatewayMessageError.invalidSequence
        }
        guard Self.isValidIdentifier(sessionId), Self.isValidIdentifier(requestId) else {
            throw GatewayMessageError.invalidIdentifier
        }
    }

    private static func isValidIdentifier(_ value: String) -> Bool {
        !value.isEmpty && value.lengthOfBytes(using: .utf8) <= JGlassGatewayProtocol.maximumIdentifierBytes
    }
}

enum GatewayMessageProcessor {
    static func response(to request: GatewayMessage, internetReachable: Bool) throws -> GatewayMessage {
        switch request.type {
        case "gateway.hello":
            guard request.payload.transport == "ble-gatt", request.payload.role == "glass-central" else {
                throw GatewayMessageError.invalidPayload
            }
            return GatewayMessage(
                protocolVersion: JGlassGatewayProtocol.version,
                type: "gateway.hello_ack",
                sessionId: request.sessionId,
                requestId: request.requestId,
                sequence: request.sequence,
                payload: GatewayPayload(
                    role: "iphone-peripheral",
                    acceptedProtocol: JGlassGatewayProtocol.version
                )
            )

        case "gateway.ping":
            return GatewayMessage(
                protocolVersion: JGlassGatewayProtocol.version,
                type: "gateway.pong",
                sessionId: request.sessionId,
                requestId: request.requestId,
                sequence: request.sequence,
                payload: GatewayPayload(internetReachable: internetReachable)
            )

        default:
            throw GatewayMessageError.unsupportedType
        }
    }
}

enum GatewayMessageError: Error, Equatable {
    case invalidIdentifier
    case invalidPayload
    case invalidSequence
    case messageTooLarge
    case unsupportedProtocol
    case unsupportedType
}
