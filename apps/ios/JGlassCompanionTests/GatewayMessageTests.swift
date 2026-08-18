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

    func testReliabilityMetricsAreEphemeralAndRelative() {
        var metrics = GatewayReliabilityMetrics(startedAt: 100)

        metrics.recordSubscription("Subscribed", at: 101.25)
        metrics.recordCompletedMessage("Received ping", at: 103.5)
        metrics.recordRestoration("Restored", at: 108)

        XCTAssertEqual(metrics.subscriptionEvents, 1)
        XCTAssertEqual(metrics.completedMessages, 1)
        XCTAssertEqual(metrics.restorationEvents, 1)
        XCTAssertEqual(metrics.lastEvent, "Restored")
        XCTAssertEqual(metrics.lastEventElapsedSeconds, 8)
        XCTAssertEqual(metrics.lastEventTime, "8.0 s")
    }

    private func fixture(named name: String) -> Data {
        let url = Bundle(for: Self.self).url(forResource: name, withExtension: "json")!
        return try! Data(contentsOf: url)
    }
}
