import WidgetKit
import SwiftUI

/// Thin WidgetKit shell over the shared Kotlin `AssistantWidgetSnapshot`.
/// The iOS app writes JSON + UserDefaults through `IosPlatformWidgetStore`.
struct LastChatAssistantWidget: Widget {
    let kind: String = "LastChatAssistantWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: AssistantWidgetProvider()) { entry in
            AssistantWidgetView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("LastChat Assistant")
        .description("Opens LastChat with the current assistant.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct AssistantWidgetEntry: TimelineEntry {
    let date: Date
    let assistantName: String
    let conversationTitle: String
}

struct AssistantWidgetProvider: TimelineProvider {
    func placeholder(in context: Context) -> AssistantWidgetEntry {
        AssistantWidgetEntry(date: Date(), assistantName: "Assistant", conversationTitle: "LastChat")
    }

    func getSnapshot(in context: Context, completion: @escaping (AssistantWidgetEntry) -> Void) {
        completion(currentEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<AssistantWidgetEntry>) -> Void) {
        let timeline = Timeline(entries: [currentEntry()], policy: .after(Date().addingTimeInterval(15 * 60)))
        completion(timeline)
    }

    private func currentEntry() -> AssistantWidgetEntry {
        let defaults = UserDefaults(suiteName: "group.lastchat.rikkafork.cocolal") ?? .standard
        if let raw = defaults.string(forKey: "assistant_widget_snapshot"),
           let data = raw.data(using: .utf8),
           let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            let name = stringValue(json["assistantName"]) ?? defaults.string(forKey: "assistant_name") ?? "Assistant"
            let title = stringValue(json["conversationTitle"]) ?? defaults.string(forKey: "conversation_title") ?? "LastChat"
            return AssistantWidgetEntry(date: Date(), assistantName: name, conversationTitle: title)
        }
        let name = defaults.string(forKey: "assistant_name") ?? "Assistant"
        let title = defaults.string(forKey: "conversation_title") ?? "LastChat"
        return AssistantWidgetEntry(date: Date(), assistantName: name, conversationTitle: title)
    }

    private func stringValue(_ value: Any?) -> String? {
        guard let text = value as? String, !text.isEmpty else { return nil }
        return text
    }
}

struct AssistantWidgetView: View {
    var entry: AssistantWidgetProvider.Entry

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("LastChat")
                .font(.caption.weight(.semibold))
            Text(entry.assistantName)
                .font(.headline)
                .lineLimit(1)
            Text(entry.conversationTitle)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .lineLimit(2)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .padding()
    }
}
