import SwiftUI
import BackgroundTasks
import UserNotifications
import LastChatUI

@main
struct LastChatIOSApp: App {
    @UIApplicationDelegateAdaptor(LastChatAppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            LastChatRootView()
                .ignoresSafeArea()
        }
    }
}

private final class LastChatAppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        AdaptiveMemoryBackgroundTasks.shared.register()
        ScheduledMessageBackgroundTasks.shared.register()
        return true
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound])
    }
}

private final class ScheduledMessageBackgroundTasks {
    static let shared = ScheduledMessageBackgroundTasks()
    private let identifier = "lastchat.rikkafork.cocolal.ios.scheduled-message.refresh"

    func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: nil) { [weak self] task in
            guard let self, let refreshTask = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            self.handle(refreshTask)
        }
        MainViewControllerKt.InstallIosScheduledMessageBackgroundScheduler(
            schedule: { [weak self] epochMs in
                self?.schedule(earliestBeginEpochMs: epochMs.int64Value)
            },
            cancel: { [weak self] in
                self?.cancel()
            }
        )
    }

    private func schedule(earliestBeginEpochMs: Int64) {
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: identifier)
        let request = BGAppRefreshTaskRequest(identifier: identifier)
        request.earliestBeginDate = Date(
            timeIntervalSince1970: TimeInterval(earliestBeginEpochMs) / 1_000.0
        )
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            // The in-process timer remains active when the OS declines a refresh request.
        }
    }

    private func cancel() {
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: identifier)
    }

    private func handle(_ task: BGAppRefreshTask) {
        let lock = NSLock()
        var completed = false
        let finish: (Bool) -> Void = { success in
            lock.lock()
            defer { lock.unlock() }
            guard !completed else { return }
            completed = true
            task.setTaskCompleted(success: success)
        }
        task.expirationHandler = { finish(false) }
        MainViewControllerKt.RunIosScheduledMessageBackgroundMaintenance { success in
            finish(success.boolValue)
        }
    }
}

private final class AdaptiveMemoryBackgroundTasks {
    static let shared = AdaptiveMemoryBackgroundTasks()
    private let identifier = "lastchat.rikkafork.cocolal.ios.memory.refresh"

    func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: nil) { [weak self] task in
            guard let self, let refreshTask = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            self.handle(refreshTask)
        }
        MainViewControllerKt.InstallIosAdaptiveMemoryBackgroundScheduler(
            schedule: { [weak self] epochMs in
                self?.schedule(earliestBeginEpochMs: epochMs.int64Value)
            },
            cancel: { [weak self] in
                self?.cancel()
            }
        )
    }

    private func schedule(earliestBeginEpochMs: Int64) {
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: identifier)
        let request = BGAppRefreshTaskRequest(identifier: identifier)
        request.earliestBeginDate = Date(
            timeIntervalSince1970: TimeInterval(earliestBeginEpochMs) / 1_000.0
        )
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            // The in-process timer remains active when the OS declines a refresh request.
        }
    }

    private func cancel() {
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: identifier)
    }

    private func handle(_ task: BGAppRefreshTask) {
        let lock = NSLock()
        var completed = false
        let finish: (Bool) -> Void = { success in
            lock.lock()
            defer { lock.unlock() }
            guard !completed else { return }
            completed = true
            task.setTaskCompleted(success: success)
        }
        task.expirationHandler = { finish(false) }
        MainViewControllerKt.RunIosAdaptiveMemoryBackgroundMaintenance { success in
            finish(success.boolValue)
        }
    }
}

private struct LastChatRootView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // Compose owns the root state and updates itself.
    }
}
