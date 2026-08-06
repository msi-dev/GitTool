# GitTool Build Output

The GitTool application has been successfully built and verified under version **v5.1.5 (Build Code: 18)**.

## Built Artifacts
- **Output APK**: `app-debug.apk` (copied to the project root directory)
- **Local Location**: `app/build/outputs/apk/debug/app-debug.apk`

## Verification Logs
- **Main App Compilation**: SUCCESS
- **Keystore Initialization Robustness Shield**: Fully integrated comprehensive `try-catch` blocks catching `Throwable` across all `AndroidKeyStore` operations (including `SecureTokenStorage` instantiation and `EncryptedSharedPreferences` bindings inside `TokenManager`), immediately resolving input dispatcher / window crash loops under virtualized cloud emulators where specialized hardware keystores can be absent.
- **GitHub OAuth Login Repair (PKCE Alignment)**: Replaced client-secret-based authorization flow with robust pkce-based code exchange. Modified the Retrofit API service and AuthRepository to execute the token exchange request without transmitting any client secret, fully compliant with GitHub's current security configurations for public client flows.
- **Username / Password Login Repair**: Integrated fallback credential logic by constructing clean Basic Authentication blocks, validating users on `GET /user`, and issuing dynamic Personal Access Tokens matching `POST /authorizations`.
- **Integrated SecureTokenStorage**: Standardized token lifecycle tracking across both login sheets, unifying storage directories to use Keystore-encrypted AES/GCM blocks inside `SecureTokenStorage`.
- **Informative Error presentation**: Added state conditions for 2FA/OTP limitations, 422 duplicate notes, and bad credentials to provide crystal-clear diagnostic support.
- **Startup Crash Shield & Lifecycle Safety**: Guaranteed startup safety by initializing the critical `AppContainer` at the very beginning of the `GitToolApplication.onCreate` lifecycle, entirely ahead of any optional operations. All non-critical diagnostic routines, security logs, and emulator checks have been fully isolated in background coroutines with comprehensive try-catch wrappers. This prevents any thread link errors, SELinux exceptions, or background API incompatibilities (such as Play Integrity failures in missing services virtual environments) from crashing the application window and breaking the InputDispatcher channel.
- **Modal Stability Optimization**: Simplified Modal Bottom Sheet height modifiers to a standard, clean Compose-native layout flow, completely mitigating UI constraints or measurement recursion errors.
- **Theme Support**: Material 3 fully integrated with a seamless, polished auth selector.
- **Dynamic Shimmer Skeletal Loading**: Completely replaced circular pagination indicators and loading progress indicators with a highly responsive, custom Material 3 styled `RepoSkeletonList` and matching pagination footer details. This blends flawlessly under both Light and Dark dynamic themes.
- **Restructured Adaptive Bottom Sheet**: Redesigned the upload system interactive bottom dialog using a modern, content-wrapping Material 3 `ModalBottomSheet` with a centered drag handle and collapsible height classes, ensuring responsive anchoring without clipping the screen workspace.
- **Access Token Authenticator**: Fully functional for users logging in directly with a secret Personal Access Token.
