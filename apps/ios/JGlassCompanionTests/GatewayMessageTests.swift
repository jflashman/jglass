import XCTest
@testable import JGlassCompanion

final class GatewayMessageTests: XCTestCase {
    func testHelloFixtureProducesCanonicalAcknowledgement() throws {
        let request = try GatewayMessage.decode(fixture(named: "hello"))
        let response = try GatewayMessageProcessor.response(to: request, internetReachable: false)
        let expected = try GatewayMessage.decode(fixture(named: "hello-ack"))

        XCTAssertEqual(response, expected)
    }

    func testPingFixtureProducesReachablePong() throws {
        let request = try GatewayMessage.decode(fixture(named: "ping"))
        let response = try GatewayMessageProcessor.response(to: request, internetReachable: true)
        let expected = try GatewayMessage.decode(fixture(named: "pong"))

        XCTAssertEqual(response, expected)
    }

    func testUnsupportedProtocolIsRejected() throws {
        let data = fixture(named: "ping")
        let json = String(decoding: data, as: UTF8.self)
            .replacingOccurrences(of: "jglass/1", with: "jglass/999")

        XCTAssertThrowsError(try GatewayMessage.decode(Data(json.utf8))) { error in
            XCTAssertEqual(error as? GatewayMessageError, .unsupportedProtocol)
        }
    }

    func testOversizedMessageIsRejectedBeforeDecoding() {
        let data = Data(repeating: 0x20, count: JGlassGatewayProtocol.maximumControlMessageBytes + 1)

        XCTAssertThrowsError(try GatewayMessage.decode(data)) { error in
            XCTAssertEqual(error as? GatewayMessageError, .messageTooLarge)
        }
    }

    private func fixture(named name: String) -> Data {
        let url = Bundle(for: Self.self).url(forResource: name, withExtension: "json")!
        return try! Data(contentsOf: url)
    }
}
