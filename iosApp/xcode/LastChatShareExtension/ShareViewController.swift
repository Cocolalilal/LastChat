import UIKit
import UniformTypeIdentifiers

/// Share-in trampoline. Writes shared text into App Group / standard defaults so the
/// iOS app can ingest it through `PortableSharePayload`. Not added to the Xcode
/// project yet — CI unsigned `xcodebuild` cannot sign an extension target.
class ShareViewController: UIViewController {
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
                let defaults = UserDefaults(suiteName: "group.lastchat.rikkafork.cocolal") ?? .standard
                defaults.set(text, forKey: "pending_share_text")
                defaults.synchronize()
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
