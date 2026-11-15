import Foundation

extension Notification.Name {
    /// Friendships changed (invite accepted, request accepted, became friends): friend lists and the feed reload.
    static let friendsChanged = Notification.Name("lizma.friendsChanged")

    /// The user published a post: the feed reloads from its first page.
    static let postCreated = Notification.Name("lizma.postCreated")
}
