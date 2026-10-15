import UIKit
import CopyMangaShared

// Integrate this root in SceneDelegate/App. UIKit's category overrides belong in Swift.
final class ReaderHostingController: UIViewController, UIGestureRecognizerDelegate {
    private lazy var windowState = IosReaderWindowState(onChanged: { [weak self] in self?.applyReaderPolicy() })
    private let backBridge = NativeBackBridge()
    private var compose: UIViewController?
    private var edgeBack: UIScreenEdgePanGestureRecognizer?

    override func viewDidLoad() {
        super.viewDidLoad()
        let child = MainViewControllerKt.MainViewController(windowState: windowState, backBridge: backBridge, presenter: { [unowned self] in self })
        compose = child
        addChild(child)
        child.view.frame = view.bounds
        child.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(child.view)
        child.didMove(toParent: self)
        let edgeBack = UIScreenEdgePanGestureRecognizer(target: self, action: #selector(handleEdgeBack(_:)))
        edgeBack.edges = leadingBackEdge
        self.edgeBack = edgeBack
        edgeBack.delegate = self
        view.addGestureRecognizer(edgeBack)
    }

    private var leadingBackEdge: UIRectEdge {
        view.effectiveUserInterfaceLayoutDirection == .rightToLeft ? .right : .left
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        // Never change the edge/sign during an in-flight gesture.
        if let gesture = edgeBack, gesture.state != .began && gesture.state != .changed {
            gesture.edges = leadingBackEdge
        }
    }

    // Only the physical screen edge belongs to back. Ordinary horizontal reader
    // swipes and native modal pickers/shares retain their own input handling.
    private var canUseSharedBack: Bool {
        backBridge.canHandleBack && view.window != nil &&
            presentedViewController == nil && compose?.presentedViewController == nil
    }

    func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        guard canUseSharedBack, let gesture = gestureRecognizer as? UIScreenEdgePanGestureRecognizer else { return false }
        let velocity = gesture.velocity(in: view)
        let inwardVelocity = gesture.edges == .right ? -velocity.x : velocity.x
        return inwardVelocity > 0 && abs(velocity.x) > abs(velocity.y)
    }

    @objc private func handleEdgeBack(_ gesture: UIScreenEdgePanGestureRecognizer) {
        let width = max(view.bounds.width, 1)
        let fromLeft = gesture.edges != .right
        let direction: CGFloat = fromLeft ? 1 : -1
        let progress = Float(max(0, min(1, direction * gesture.translation(in: view).x / width)))
        switch gesture.state {
        case .began:
            if canUseSharedBack { _ = backBridge.beginGesture(progress: progress, fromLeft: fromLeft) }
        case .changed:
            if canUseSharedBack { backBridge.updateGesture(progress: progress) }
            else { backBridge.cancelGesture() }
        case .ended:
            if canUseSharedBack {
                backBridge.finishGesture(progress: progress, velocity: Float(direction * gesture.velocity(in: view).x / width))
            } else { backBridge.cancelGesture() }
        case .cancelled, .failed:
            backBridge.cancelGesture()
        default: break
        }
    }

    override var canBecomeFirstResponder: Bool { true }
    override var keyCommands: [UIKeyCommand]? {
        guard canUseSharedBack else { return super.keyCommands }
        return (super.keyCommands ?? []) + [UIKeyCommand(input: UIKeyCommand.inputEscape, modifierFlags: [], action: #selector(handleHardwareBack))]
    }
    override func canPerformAction(_ action: Selector, withSender sender: Any?) -> Bool {
        if action == #selector(handleHardwareBack) { return canUseSharedBack }
        return super.canPerformAction(action, withSender: sender)
    }
    @objc private func handleHardwareBack() {
        if canUseSharedBack { _ = backBridge.performBack() }
    }
    override func viewWillDisappear(_ animated: Bool) {
        backBridge.cancelGesture()
        super.viewWillDisappear(animated)
    }
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        applyReaderPolicy()
    }
    override var prefersStatusBarHidden: Bool { windowState.hiddenBars }
    override var preferredStatusBarStyle: UIStatusBarStyle {
        guard windowState.hasAppearancePolicy else { return .default }
        return windowState.lightBars ? .darkContent : .lightContent
    }
    override var prefersHomeIndicatorAutoHidden: Bool { windowState.hiddenBars }
    override var supportedInterfaceOrientations: UIInterfaceOrientationMask {
        let mask = windowState.orientationMask
        return mask == 0 ? super.supportedInterfaceOrientations : UIInterfaceOrientationMask(rawValue: UInt(mask))
    }
    override var shouldAutorotate: Bool { true }
    private func applyReaderPolicy() {
        setNeedsStatusBarAppearanceUpdate()
        setNeedsUpdateOfHomeIndicatorAutoHidden()
        if #available(iOS 16.0, *) {
            setNeedsUpdateOfSupportedInterfaceOrientations()
            view.window?.windowScene?.requestGeometryUpdate(.iOS(interfaceOrientations: supportedInterfaceOrientations)) { [weak self] error in
                self?.windowState.reportFailure(message: "屏幕方向调整未完成：\(error.localizedDescription)")
            }
        } else { UIViewController.attemptRotationToDeviceOrientation() }
    }
}
