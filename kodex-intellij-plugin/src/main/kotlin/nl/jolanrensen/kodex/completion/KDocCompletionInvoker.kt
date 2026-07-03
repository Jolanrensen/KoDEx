package nl.jolanrensen.kodex.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import nl.jolanrensen.kodex.kodexCompletionIsEnabled
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens

/**
 * Makes sure the completion popup appears when the
 * user types a '{' or '$' in a KDoc comment.
 */
class KDocCompletionInvoker : TypedHandlerDelegate() {

    private fun autoPopupIfInKdoc(project: Project, editor: Editor) {
        AutoPopupController.getInstance(project).autoPopupMemberLookup(editor) { file ->
            val offset = editor.caretModel.offset
            val elementAtCaret = file.findElementAt(offset - 1)
            val lastNodeType = elementAtCaret?.node?.elementType ?: return@autoPopupMemberLookup false

            lastNodeType === KDocTokens.TEXT ||
                lastNodeType === KDocTokens.TAG_NAME ||
                lastNodeType === KDocTokens.LEADING_ASTERISK ||
                lastNodeType === KDocTokens.CODE_BLOCK_TEXT ||
                lastNodeType === KDocTokens.START
        }
    }

    override fun beforeCharTyped(
        c: Char,
        project: Project,
        editor: Editor,
        file: PsiFile,
        fileType: FileType,
    ): Result {
        if (!kodexCompletionIsEnabled) return Result.CONTINUE
        if (file.fileType != KotlinFileType.INSTANCE) return Result.CONTINUE
        when (c) {
            '{', '$', '@' -> autoPopupIfInKdoc(project, editor)
        }
        return Result.CONTINUE
    }
}
