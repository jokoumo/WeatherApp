## Project Context

This repository contains a native Android weather application developed with Kotlin and Jetpack Compose.

The application is intended for real-world personal use and must prioritize reliability, maintainability, performance, and usability.

The repository was initialized using the Android Studio "Empty Activity" template and has already been verified to build and run successfully.

## 1. General Working Principles

- Work autonomously and make reasonable engineering decisions.
- Inspect existing code and configuration before making changes.
- Prefer simple, reliable solutions over unnecessary complexity.
- Follow established Android and Kotlin development conventions.
- Write production-quality code, not demonstration code.
- Avoid introducing dependencies without a clear benefit.
- Do not leave unfinished implementations, placeholder logic, or unnecessary TODO comments.
- Do not silently ignore errors.
- Keep modifications within the project directory.
- Do not create Git commits unless explicitly instructed.

## 2. Technology Standards

Use the following technologies:

- Kotlin for application code.
- Jetpack Compose for user interfaces.
- Material 3 for visual components.
- MVVM for presentation architecture.
- Kotlin Coroutines and StateFlow for asynchronous operations and state management.
- Repository Pattern for data access.
- Retrofit and OkHttp for networking.
- Kotlinx Serialization for JSON.
- DataStore for application preferences.
- Room for structured local persistence.

Use the existing Gradle Wrapper and compatible stable dependency versions.

Do not change the application's package name or application ID without explicit authorization.

Avoid deprecated Android APIs whenever supported alternatives are available.

## 3. Code Organization

Separate responsibilities appropriately:

- UI components should focus on presentation and user interaction.
- ViewModels should coordinate UI state and application behavior.
- Repositories should manage data access and caching.
- API DTOs should not be exposed directly to the UI.
- Data transformations should be implemented in testable functions.
- Network and database operations must not block the main thread.

Prefer dependency injection where it improves testability.

Avoid excessive abstraction, unnecessary interfaces, and overengineering.

## 4. Kotlin and Compose Conventions

- Follow standard Kotlin naming conventions.
- Prefer immutable data structures and read-only state.
- Use data classes for appropriate domain and UI models.
- Use sealed interfaces or classes for meaningful state representations.
- Handle nullable data explicitly.
- Avoid unsafe non-null assertions.
- Use lifecycle-aware state collection in Compose.
- Keep Composable functions focused and reusable.
- Avoid unnecessary recomposition and expensive work inside Composables.
- Use stable keys for dynamic lists where appropriate.
- Use structured concurrency and properly scoped coroutines.

## 5. Language Conventions

- Write all source code, identifiers, and code comments in English.
- Write technical documentation in English.
- Use German for all user-visible application text.
- Store user-visible strings in Android resources where practical.
- Use German date and number formatting in the application.
- Support location-specific time zones.

## 6. External APIs and Data Integrity

- Use real weather data from the configured weather provider.
- Never replace live data with fabricated values in production code.
- Handle missing, null, and incomplete API responses safely.
- Use HTTPS for network communication.
- Keep network operations asynchronous.
- Avoid unnecessary API requests.
- Clearly distinguish cached data from newly retrieved information.
- Do not assume forecast values are available for every location or date.

Mock data is permitted in automated tests and Compose previews but must not silently replace real data in the released application.

## 7. Error Handling

Handle errors explicitly and gracefully.

- Never crash because a network request fails.
- Handle missing permissions and unavailable system services where relevant.
- Provide clear loading, empty, success, and error states.
- Avoid displaying technical exception messages directly to users.
- Preserve useful cached information during temporary failures.
- Log relevant diagnostic information without exposing sensitive data.

## 8. Testing Requirements

Treat testing as part of implementation, not as an optional final step.

- Write meaningful unit tests for business logic.
- Test important edge cases and failure conditions.
- Use deterministic test data and fake dependencies.
- Avoid relying on live external services in unit tests.
- Add UI tests for important user interactions where feasible.
- Run relevant tests after implementing or modifying functionality.
- Fix failing tests rather than disabling them.

Do not create tests that only verify trivial implementation details.

## 9. Build and Verification

Use the existing Gradle Wrapper.

On Windows, the primary build command is:

```powershell
.\gradlew.bat assembleDebug
```

Run the relevant unit tests using:

```powershell
.\gradlew.bat testDebugUnitTest
```

Run Android lint using:

```powershell
.\gradlew.bat lintDebug
```

If an Android Emulator is available:

- Install and launch the application.
- Verify important user interactions.
- Inspect Logcat when failures occur.
- Check relevant layouts visually.
- Resolve discovered runtime issues.

Never claim that a build or test passed unless it was actually executed successfully.

## 10. Security and Privacy

- Never hardcode passwords, credentials, or API secrets.
- Do not add unnecessary Android permissions.
- Do not collect personal information unnecessarily.
- Store application data locally unless external storage is required by the task.
- Do not introduce analytics, advertising SDKs, or user tracking.
- Do not transmit private application data to unrelated external services.

## 11. Git and File Management

- Respect the existing `.gitignore` and `.gitattributes`.
- Preserve the repository history.
- Do not execute destructive Git operations.
- Do not reset, clean, or overwrite unrelated user changes.
- Do not create commits unless requested.
- Do not include generated build artifacts in version control.
- Prefer LF line endings for source code and configuration files.

## 12. Autonomous Development Workflow

For substantial implementation tasks:

1. Inspect the relevant project files.
2. Identify existing architecture and dependencies.
3. Plan the necessary changes.
4. Implement the functionality.
5. Build the application.
6. Run relevant tests.
7. Investigate and fix failures.
8. Review the final changes.
9. Summarize the completed work.

Do not stop after the first implementation if compilation or testing reveals problems.

Do not repeatedly rewrite working code without a clear technical reason.

## 13. Completion and Reporting

At the end of a development task, provide:

- A summary of implemented changes.
- Important architectural decisions.
- Build results.
- Test results.
- Any remaining limitations.
- The location of generated artifacts, when applicable.

Clearly distinguish verified functionality from untested behavior.

Never claim completion of requirements that remain unfinished.