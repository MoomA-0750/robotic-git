package com.example.roboticgit.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.performTextInput
import com.example.roboticgit.data.model.AccountType
import com.example.roboticgit.ui.screens.AddGenericAccountDialog
import com.example.roboticgit.ui.screens.AddGitHubAccountDialog
import com.example.roboticgit.ui.theme.RoboticGitTheme
import com.example.roboticgit.ui.viewmodel.ValidationStatus
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/**
 * The account dialogs, and specifically that a token typed into them is treated
 * as the secret it is.
 *
 * These dialogs used to render the token in plain text, so it was legible to
 * anyone beside the user and survived in any screenshot of the screen.
 */
class AccountDialogTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val isPassword = SemanticsMatcher.keyIsDefined(SemanticsProperties.Password)

    @Test
    fun theGitHubTokenFieldIsTreatedAsASecret() {
        compose.setContent {
            RoboticGitTheme(dynamicColor = false) {
                AddGitHubAccountDialog(
                    onDismiss = {},
                    onManualAdd = {},
                    validationStatus = ValidationStatus.Idle
                )
            }
        }

        compose.onNodeWithText("GitHub Token").assert(isPassword)
    }

    @Test
    fun theSelfHostedTokenFieldIsTreatedAsASecret() {
        compose.setContent {
            RoboticGitTheme(dynamicColor = false) {
                AddGenericAccountDialog(
                    type = AccountType.GITEA,
                    onDismiss = {},
                    onAdd = { _, _, _ -> },
                    validationStatus = ValidationStatus.Idle
                )
            }
        }

        compose.onNodeWithText("Access Token").assert(isPassword)
    }

    /**
     * The instance URL is not a secret and has to stay readable -- a mistyped
     * address is the most likely thing to go wrong when adding a self-hosted
     * account, and it cannot be spotted through dots.
     */
    @Test
    fun theInstanceUrlStaysReadable() {
        compose.setContent {
            RoboticGitTheme(dynamicColor = false) {
                AddGenericAccountDialog(
                    type = AccountType.GITEA,
                    onDismiss = {},
                    onAdd = { _, _, _ -> },
                    validationStatus = ValidationStatus.Idle
                )
            }
        }

        compose.onNodeWithText("Instance URL").performTextInput("http://192.168.1.101:30008")

        assertFalse(
            "the URL must not be masked",
            compose.onAllNodes(isPassword).fetchSemanticsNodes().any { node ->
                node.config.getOrNull(SemanticsProperties.EditableText)
                    ?.text?.contains("192.168") == true
            }
        )
        compose.onNodeWithText("http://192.168.1.101:30008").assertExists()
    }
}
