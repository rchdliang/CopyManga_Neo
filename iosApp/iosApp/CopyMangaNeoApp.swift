import UIKit

@main
final class CopyMangaNeoAppDelegate: UIResponder, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        configurationForConnecting session: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(name: "Main", sessionRole: session.role)
        configuration.delegateClass = CopyMangaSceneDelegate.self
        return configuration
    }

    func application(
        _ application: UIApplication,
        supportedInterfaceOrientationsFor window: UIWindow?
    ) -> UIInterfaceOrientationMask {
        window?.rootViewController?.supportedInterfaceOrientations ?? .allButUpsideDown
    }
}

final class CopyMangaSceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        guard let scene = scene as? UIWindowScene else { return }
        let window = UIWindow(windowScene: scene)
        // The native reader container is the actual root, so UIKit honors its window policies.
        window.rootViewController = ReaderHostingController()
        self.window = window
        window.makeKeyAndVisible()
    }
}
