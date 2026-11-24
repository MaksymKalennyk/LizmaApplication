import SwiftUI

/// A red count for things that wait for the user, drawn inside a row's label so it sits before the
/// chevron (`.badge` on a navigation row puts it after the chevron). Nothing for zero.
struct CountBadge: View {
    let count: Int

    var body: some View {
        if count > 0 {
            Text(count > 99 ? "99+" : "\(count)")
                .font(.footnote.weight(.semibold))
                .monospacedDigit()
                .foregroundStyle(.white)
                .padding(.horizontal, 7)
                .frame(minWidth: 22, minHeight: 22)
                .background(Color.red, in: Capsule())
                .accessibilityLabel("\(count) waiting")
        }
    }
}
