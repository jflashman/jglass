import Foundation
import Network

final class NetworkReachabilityMonitor {
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "com.jflashman.jglass.reachability")
    private let lock = NSLock()
    private var reachable = false

    init() {
        monitor.pathUpdateHandler = { [weak self] path in
            self?.setReachable(path.status == .satisfied)
        }
        monitor.start(queue: queue)
    }

    deinit {
        monitor.cancel()
    }

    var isReachable: Bool {
        lock.withLock { reachable }
    }

    private func setReachable(_ value: Bool) {
        lock.withLock { reachable = value }
    }
}

private extension NSLock {
    func withLock<T>(_ operation: () -> T) -> T {
        lock()
        defer { unlock() }
        return operation()
    }
}
