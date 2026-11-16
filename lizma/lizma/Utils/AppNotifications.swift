import Foundation

extension Notification.Name {
    /// Friendships changed (invite accepted, request accepted, became friends): friend lists and the feed reload.
    static let friendsChanged = Notification.Name("lizma.friendsChanged")

    /// The user published a post: the feed reloads from its first page.
    static let postCreated = Notification.Name("lizma.postCreated")

    /// The Feed tab was tapped while it was already showing: the feed goes back to its newest post.
    static let feedTabReselected = Notification.Name("lizma.feedTabReselected")
}
