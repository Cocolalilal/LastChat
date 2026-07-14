import SwiftUI
import LastChatUI

@main
struct LastChatIOSApp: App {
    var body: some Scene {
        WindowGroup {
            LastChatRootView()
                .ignoresSafeArea()
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
