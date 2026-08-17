import XCTest
@testable import JGlassCompanion

final class BLEControlFramingTests: XCTestCase {
    func testTwentyBytePacketsRoundTripCanonicalHello() throws {
        let message = fixture(named: "hello")
        let packets = try BLEControlFrame.fragment(message, streamID: 7, maximumPacketLength: 20)
        let reassembler = BLEControlReassembler()
        var completed: Data?

        XCTAssertGreaterThan(packets.count, 1)
        XCTAssertTrue(packets.allSatisfy { $0.count <= 20 })

        for packet in packets {
            if case .completed(let value) = try reassembler.accept(packet) {
                completed = value
            }
        }
        XCTAssertEqual(completed, message)
    }

    func testOutOfOrderFragmentIsRejected() throws {
        let packets = try BLEControlFrame.fragment(fixture(named: "ping"), streamID: 9, maximumPacketLength: 20)
        let reassembler = BLEControlReassembler()

        _ = try reassembler.accept(packets[0])
        XCTAssertThrowsError(try reassembler.accept(packets[2])) { error in
            XCTAssertEqual(error as? BLEFramingError, .invalidSequence)
        }
    }

    func testDuplicateFragmentIsDetected() throws {
        let packets = try BLEControlFrame.fragment(fixture(named: "ping"), streamID: 11, maximumPacketLength: 20)
        let reassembler = BLEControlReassembler()

        XCTAssertEqual(try reassembler.accept(packets[0]), .waiting)
        XCTAssertEqual(try reassembler.accept(packets[0]), .duplicate)
    }

    func testCancellationClearsPartialStream() throws {
        let packets = try BLEControlFrame.fragment(fixture(named: "ping"), streamID: 13, maximumPacketLength: 20)
        let reassembler = BLEControlReassembler()

        _ = try reassembler.accept(packets[0])
        XCTAssertEqual(
            try reassembler.accept(BLEControlFrame.cancellation(streamID: 13, sequence: 1)),
            .cancelled
        )
        XCTAssertThrowsError(try reassembler.accept(packets[1])) { error in
            XCTAssertEqual(error as? BLEFramingError, .missingFirstFragment)
        }
    }

    func testOversizedPayloadCannotBeFragmented() {
        let data = Data(repeating: 0, count: JGlassGatewayProtocol.maximumControlMessageBytes + 1)

        XCTAssertThrowsError(try BLEControlFrame.fragment(data, streamID: 1, maximumPacketLength: 20)) { error in
            XCTAssertEqual(error as? BLEFramingError, .messageTooLarge)
        }
    }

    private func fixture(named name: String) -> Data {
        let url = Bundle(for: Self.self).url(forResource: name, withExtension: "json")!
        return try! Data(contentsOf: url)
    }
}
