import UIKit
import UniformTypeIdentifiers

/// Share-in trampoline. Writes shared text into the App Group suite so the
/// iOS app can ingest it through `PortableSharePayload`.
class ShareViewController: UIViewController {
    private let suiteName = "group.lastchat.rikkafork.cocolal"
    private let pendingKey = "pending_share_text"

    override func viewDidLoad() {
        super.viewDidLoad()
        ingest()
    }

    private func ingest() {
        guard let item = extensionContext?.inputItems.first as? NSExtensionItem else {
            finish()
            return
        }
        let providers = item.attachments ?? []
        let textType = UTType.plainText.identifier
        for provider in providers where provider.hasItemConformingToTypeIdentifier(textType) {
            provider.loadItem(forTypeIdentifier: textType, options: nil) { payload, _ in
                let text = (payload as? String)
                    ?? ((payload as? Data).flatMap { String(data: $0, encoding: .utf8) })
                    ?? ""
                let defaults = UserDefaults(suiteName: self.suiteName) ?? .standard
                defaults.set(text, forKey: self.pendingKey)
                defaults.synchronize()
                if let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: self.suiteName) {
                    try? text.write(
                        to: container.appendingPathComponent("\(self.pendingKey).txt"),
                        atomically: true,
                        encoding: .utf8
                    )
                }
                self.finish()
            }
            return
        }
        finish()
    }

    private func finish() {
        extensionContext?.completeRequest(returningItems: [], completionHandler: nil)
    }
}
